package org.jetbrains.exposed.dao

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import org.jetbrains.exposed.sql.transactionManager
import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.transactions.ITransactionManager.Companion.currentThreadManager
import org.jetbrains.exposed.sql.transactions.ITransactionManager.Companion.managers
import java.sql.SQLException

open class DaoTransactionManager(private val db: Database,
                                 @Volatile override var defaultIsolationLevel: Int,
                                 @Volatile override var defaultRepetitionAttempts: Int) : ITransactionManager {

	val threadLocal = ThreadLocal<ITransaction>()

	override fun newTransaction(isolation: Int, outerTransaction: ITransaction?): ITransaction =
		(outerTransaction?.takeIf { !db.useNestedTransactions } ?:
			DaoThreadLocalTransaction(
				db = db,
				transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
				threadLocal = threadLocal,
				outerTransaction = outerTransaction
			)
		).apply {
			threadLocal.set(this)
		}

	override fun currentOrNull(): DaoTransaction? {
		val value = threadLocal.get()
		if (value != null) {
			return value as DaoTransaction
		} else {
			return null
		}
	}

	private class DaoThreadLocalTransaction(
			override val db: Database,
			override val transactionIsolation: Int,
			val threadLocal: ThreadLocal<ITransaction>,
			override val outerTransaction: ITransaction?
	) : DaoTransaction(db, transactionIsolation, outerTransaction, null, false) {

		private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
			outerTransaction?.connection ?: db.connector().apply {
				autoCommit = false
				transactionIsolation = this@DaoThreadLocalTransaction.transactionIsolation
			}
		}
		override val connection: ExposedConnection<*>
			get() = connectionLazy.value

		private val useSavePoints = outerTransaction != null && db.useNestedTransactions
		private var savepoint: ExposedSavepoint? = if (useSavePoints) {
			connection.setSavepoint(savepointName)
		} else null


		override fun txCommit() {
			if (connectionLazy.isInitialized()) {
				if (!useSavePoints) {
					connection.commit()
				} else {
					// do nothing in nested. close() will commit everything and release savepoint.
				}
			}
		}

		override fun txRollback() {
			if (connectionLazy.isInitialized() && !connection.isClosed) {
				if (useSavePoints && savepoint != null) {
					connection.rollback(savepoint!!)
					savepoint = connection.setSavepoint(savepointName)
				} else {
					connection.rollback()
				}
			}
		}

		override fun close() {
			try {
				if (!useSavePoints) {
					if (connectionLazy.isInitialized()) connection.close()
				} else {
					savepoint?.let {
						connection.releaseSavepoint(it)
						savepoint = null
					}
				}
			} finally {
				threadLocal.set(outerTransaction)
			}
		}

		private val savepointName: String
			get() {
				var nestedLevel = 0
				var currenTransaction = outerTransaction
				while (currenTransaction?.outerTransaction != null) {
					nestedLevel++
					currenTransaction = currenTransaction.outerTransaction
				}
				return "Exposed_savepoint_$nestedLevel"
			}
	}

	override fun <T> keepAndRestoreTransactionRefAfterRun(db: Database?, block: () -> T): T {
		val currentTransaction = this.currentOrNull()
		return try {
			block()
		} finally {
			this.threadLocal.set(currentTransaction)
		}
	}

	companion object {

		val manager: DaoTransactionManager
			get() = currentThreadManager.get() as DaoTransactionManager


		fun resetCurrent(manager: DaoTransactionManager?) {
			manager?.let { currentThreadManager.set(it) } ?: currentThreadManager.remove()
		}

		fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)

		fun currentOrNull() = manager.currentOrNull()

		fun current() = currentOrNull() ?: error("No transaction in context.")

		fun isInitialized() = managers.first != NotInitializedManager
	}
}

fun <T> transaction(db: Database? = null, statement: ITransaction.() -> T): T =
		transaction(db.transactionManager.defaultIsolationLevel, db.transactionManager.defaultRepetitionAttempts, db, statement)

fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, db: Database? = null, statement: ITransaction.() -> T): T = db.transactionManager.keepAndRestoreTransactionRefAfterRun(db) {
	val outer = ITransactionManager.currentOrNull()

	if (outer != null && (db == null || outer.db == db)) {
		val outerManager = outer.db.transactionManager

		val transaction = outerManager.newTransaction(transactionIsolation, outer)
		try {
			transaction.statement().also {
				if (outer.db.useNestedTransactions)
					transaction.commit()
			}
		} finally {
			ITransactionManager.resetCurrent(outerManager)
		}
	} else {
        val existingForDb = db?.transactionManager
		existingForDb?.currentOrNull()?.let { transaction ->
			val currentManager = outer?.db.transactionManager
			try {
				ITransactionManager.resetCurrent(existingForDb)
				transaction.statement().also {
					if (db.useNestedTransactions)
						transaction.commit()
				}
			} finally {
				ITransactionManager.resetCurrent(currentManager)
			}
		} ?: inTopLevelTransaction(transactionIsolation, repetitionAttempts, db, null, statement)
	}
}

fun <T> inTopLevelTransaction(
		transactionIsolation: Int,
		repetitionAttempts: Int,
		db: Database? = null,
		outerTransaction: ITransaction? = null,
		statement: ITransaction.() -> T
): T {

	fun run():T {
		var repetitions = 0

		val outerManager = outerTransaction?.db.transactionManager.takeIf { it.currentOrNull() != null }

		while (true) {
			db?.let { db.transactionManager.let { m -> ITransactionManager.resetCurrent(m) } }
			val transaction: ITransaction = db.transactionManager.newTransaction(transactionIsolation, outerTransaction)

			try {
				val answer = transaction.statement()
				transaction.commit()
				return answer
			} catch (e: SQLException) {
				val exposedSQLException = e as? ExposedSQLException
				val queriesToLog = exposedSQLException?.causedByQueries()?.joinToString(";\n")
						?: "${transaction.currentStatement}"
				val message = "Transaction attempt #$repetitions failed: ${e.message}. Statement(s): $queriesToLog"
				exposedSQLException?.contexts?.forEach {
					transaction.getInterceptors().filterIsInstance<SqlLogger>().forEach { logger ->
						logger.log(it, transaction)
					}
				}
				exposedLogger.warn(message, e)
				transaction.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. See previous log line for statement", it) }
				repetitions++
				if (repetitions >= repetitionAttempts) {
					throw e
				}
			} catch (e: Throwable) {
				val currentStatement = transaction.currentStatement
				transaction.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it) }
				throw e
			} finally {
				ITransactionManager.resetCurrent(outerManager)
				val currentStatement = transaction.currentStatement
				try {
					currentStatement?.let {
						it.closeIfPossible()
						transaction.currentStatement = null
					}
					transaction.closeExecutedStatements()
				} catch (e: Exception) {
					exposedLogger.warn("Statements close failed", e)
				}
				transaction.closeLoggingException { exposedLogger.warn("Transaction close failed: ${it.message}. Statement: $currentStatement", it) }
			}
		}
	}

	return db.transactionManager.keepAndRestoreTransactionRefAfterRun(db) {
		run()
	}
}

private object NotInitializedManager : DaoTransactionManager(Database.connect("jdbc:h2:mem:test", manager = { DaoTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }), 0, 0) {
	override var defaultIsolationLevel: Int = -1

	override var defaultRepetitionAttempts: Int = -1

	override fun newTransaction(isolation: Int, outerTransaction: ITransaction?): ITransaction = error("Please call Database.connect() before using this code")

	override fun currentOrNull(): DaoTransaction? = error("Please call Database.connect() before using this code")
}

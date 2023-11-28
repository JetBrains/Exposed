package org.jetbrains.exposed.sql.transactions

import org.jetbrains.annotations.TestOnly
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom

/**
 * [TransactionManager] implementation registered to the provided database value [db].
 *
 * [setupTxConnection] can be provided to override the default configuration of transaction settings when a
 * connection is retrieved from the database.
 */
class ThreadLocalTransactionManager(
    private val db: Database,
    private val setupTxConnection: ((ExposedConnection<*>, TransactionInterface) -> Unit)? = null
) : TransactionManager {
    @Volatile
    override var defaultRepetitionAttempts: Int = db.config.defaultRepetitionAttempts
        @Deprecated("Use DatabaseConfig to define the defaultRepetitionAttempts")
        @TestOnly
        set

    @Volatile
    override var defaultMinRepetitionDelay: Long = db.config.defaultMinRepetitionDelay
        @Deprecated("Use DatabaseConfig to define the defaultMinRepetitionDelay")
        @TestOnly
        set

    @Volatile
    override var defaultMaxRepetitionDelay: Long = db.config.defaultMaxRepetitionDelay
        @Deprecated("Use DatabaseConfig to define the defaultMaxRepetitionDelay")
        @TestOnly
        set

    @Volatile
    override var defaultIsolationLevel: Int = db.config.defaultIsolationLevel
        get() {
            if (field == -1) {
                field = Database.getDefaultIsolationLevel(db)
            }
            return field
        }

        @Deprecated("Use DatabaseConfig to define the defaultIsolationLevel")
        @TestOnly
        set

    @Volatile
    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    /** A thread local variable storing the current transaction. */
    val threadLocal = ThreadLocal<Transaction>()

    override fun toString(): String {
        return "ThreadLocalTransactionManager[${hashCode()}](db=$db)"
    }

    override fun newTransaction(isolation: Int, readOnly: Boolean, outerTransaction: Transaction?): Transaction {
        val transaction = outerTransaction?.takeIf { !db.useNestedTransactions } ?: Transaction(
            ThreadLocalTransaction(
                db = db,
                readOnly = outerTransaction?.readOnly ?: readOnly,
                transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                setupTxConnection = setupTxConnection,
                threadLocal = threadLocal,
                outerTransaction = outerTransaction
            )
        )

        return transaction.apply { bindTransactionToThread(this) }
    }

    override fun currentOrNull(): Transaction? = threadLocal.get()

    override fun bindTransactionToThread(transaction: Transaction?) {
        if (transaction != null) {
            threadLocal.set(transaction)
        } else {
            threadLocal.remove()
        }
    }

    private class ThreadLocalTransaction(
        override val db: Database,
        private val setupTxConnection: ((ExposedConnection<*>, TransactionInterface) -> Unit)?,
        override val transactionIsolation: Int,
        override val readOnly: Boolean,
        val threadLocal: ThreadLocal<Transaction>,
        override val outerTransaction: Transaction?
    ) : TransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
                setupTxConnection?.invoke(this, this@ThreadLocalTransaction) ?: run {
                    // The order of setters here is important.
                    // Transaction isolation should go first as the readOnly or autoCommit can start transaction with wrong isolation level
                    // Some drivers start a transaction right after `setAutoCommit(false)`,
                    // which makes `setReadOnly` throw an exception if it is called after `setAutoCommit`
                    transactionIsolation = this@ThreadLocalTransaction.transactionIsolation
                    readOnly = this@ThreadLocalTransaction.readOnly
                    autoCommit = false
                }
            }
        }
        override val connection: ExposedConnection<*>
            get() = connectionLazy.value

        private val useSavePoints = outerTransaction != null && db.useNestedTransactions
        private var savepoint: ExposedSavepoint? = if (useSavePoints) connection.setSavepoint(savepointName) else null

        override fun commit() {
            if (connectionLazy.isInitialized()) {
                if (!useSavePoints) {
                    connection.commit()
                } else {
                    // do nothing in nested. close() will commit everything and release savepoint.
                }
            }
        }

        override fun rollback() {
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
}

/**
 * Creates a transaction then calls the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @return The final result of the [statement] block.
 * @sample org.jetbrains.exposed.sql.tests.h2.MultiDatabaseTest.testTransactionWithDatabase
 */
fun <T> transaction(db: Database? = null, statement: Transaction.() -> T): T =
    transaction(
        db.transactionManager.defaultIsolationLevel,
        db.transactionManager.defaultReadOnly,
        db,
        statement
    )

/**
 * Creates a transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @return The final result of the [statement] block.
 * @sample org.jetbrains.exposed.sql.tests.shared.ConnectionTimeoutTest.testTransactionRepetitionWithDefaults
 */
fun <T> transaction(
    transactionIsolation: Int,
    readOnly: Boolean = false,
    db: Database? = null,
    statement: Transaction.() -> T
): T = keepAndRestoreTransactionRefAfterRun(db) {
    val outer = TransactionManager.currentOrNull()

    if (outer != null && (db == null || outer.db == db)) {
        val outerManager = outer.db.transactionManager

        val transaction = outerManager.newTransaction(transactionIsolation, readOnly, outer)
        try {
            transaction.statement().also {
                if (outer.db.useNestedTransactions) {
                    transaction.commit()
                }
            }
        } finally {
            TransactionManager.resetCurrent(outerManager)
        }
    } else {
        val existingForDb = db?.transactionManager
        existingForDb?.currentOrNull()?.let { transaction ->
            val currentManager = outer?.db.transactionManager
            try {
                TransactionManager.resetCurrent(existingForDb)
                transaction.statement().also {
                    if (db.useNestedTransactions) {
                        transaction.commit()
                    }
                }
            } finally {
                TransactionManager.resetCurrent(currentManager)
            }
        } ?: inTopLevelTransaction(
            transactionIsolation,
            readOnly,
            db,
            null,
            statement
        )
    }
}

/**
 * Creates a transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** All changes in this transaction will be committed at the end of the [statement] block, even if
 * it is nested and even if `DatabaseConfig.useNestedTransactions` is set to `false`.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @return The final result of the [statement] block.
 * @sample org.jetbrains.exposed.sql.tests.shared.RollbackTransactionTest.testRollbackWithoutSavepoints
 */
fun <T> inTopLevelTransaction(
    transactionIsolation: Int,
    readOnly: Boolean = false,
    db: Database? = null,
    outerTransaction: Transaction? = null,
    statement: Transaction.() -> T
): T {
    fun run(): T {
        var repetitions = 0

        val outerManager = outerTransaction?.db.transactionManager.takeIf { it.currentOrNull() != null }

        var intermediateDelay: Long = 0
        var retryInterval: Long? = null

        while (true) {
            db?.let { db.transactionManager.let { m -> TransactionManager.resetCurrent(m) } }
            val transaction = db.transactionManager.newTransaction(transactionIsolation, readOnly, outerTransaction)

            @Suppress("TooGenericExceptionCaught")
            try {
                transaction.db.config.defaultSchema?.let { SchemaUtils.setSchema(it) }
                val answer = transaction.statement()
                transaction.commit()
                return answer
            } catch (cause: SQLException) {
                handleSQLException(cause, transaction, repetitions)
                repetitions++
                if (repetitions >= transaction.repetitionAttempts) {
                    throw cause
                }

                if (retryInterval == null) {
                    retryInterval = transaction.getRetryInterval()
                    intermediateDelay = transaction.minRepetitionDelay
                }
                // set delay value with an exponential backoff time period.
                val delay = when {
                    transaction.minRepetitionDelay < transaction.maxRepetitionDelay -> {
                        intermediateDelay += retryInterval * repetitions
                        ThreadLocalRandom.current().nextLong(intermediateDelay, intermediateDelay + retryInterval)
                    }

                    transaction.minRepetitionDelay == transaction.maxRepetitionDelay -> transaction.minRepetitionDelay
                    else -> 0
                }
                exposedLogger.warn("Wait $delay milliseconds before retrying")
                try {
                    Thread.sleep(delay)
                } catch (cause: InterruptedException) {
                    // Do nothing
                }
            } catch (cause: Throwable) {
                val currentStatement = transaction.currentStatement
                transaction.rollbackLoggingException {
                    exposedLogger.warn(
                        "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                        it
                    )
                }
                throw cause
            } finally {
                TransactionManager.resetCurrent(outerManager)
                closeStatementsAndConnection(transaction)
            }
        }
    }

    return keepAndRestoreTransactionRefAfterRun(db) {
        run()
    }
}

private fun <T> keepAndRestoreTransactionRefAfterRun(db: Database? = null, block: () -> T): T {
    val manager = db.transactionManager
    val currentTransaction = manager.currentOrNull()
    return try {
        block()
    } finally {
        manager.bindTransactionToThread(currentTransaction)
    }
}

internal fun handleSQLException(cause: SQLException, transaction: Transaction, repetitions: Int) {
    val exposedSQLException = cause as? ExposedSQLException
    val queriesToLog = exposedSQLException?.causedByQueries()?.joinToString(";\n") ?: "${transaction.currentStatement}"
    val message = "Transaction attempt #$repetitions failed: ${cause.message}. Statement(s): $queriesToLog"
    exposedSQLException?.contexts?.forEach {
        transaction.interceptors.filterIsInstance<SqlLogger>().forEach { logger ->
            logger.log(it, transaction)
        }
    }
    exposedLogger.warn(message, cause)
    transaction.rollbackLoggingException {
        exposedLogger.warn("Transaction rollback failed: ${it.message}. See previous log line for statement", it)
    }
}

internal fun closeStatementsAndConnection(transaction: Transaction) {
    val currentStatement = transaction.currentStatement
    @Suppress("TooGenericExceptionCaught")
    try {
        currentStatement?.let {
            it.closeIfPossible()
            transaction.currentStatement = null
        }
        transaction.closeExecutedStatements()
    } catch (cause: Exception) {
        exposedLogger.warn("Statements close failed", cause)
    }
    transaction.closeLoggingException {
        exposedLogger.warn("Transaction close failed: ${it.message}. Statement: $currentStatement", it)
    }
}

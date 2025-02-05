package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JdbcTransaction
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
    private val setupTxConnection: ((ExposedConnection<*>, JdbcTransactionInterface) -> Unit)? = null
) : TransactionManager {
    @Volatile
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    @Volatile
    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    @Volatile
    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    @Volatile
    override var defaultIsolationLevel: Int = db.config.defaultIsolationLevel
        get() {
            when {
                field == -1 -> {
                    if (db.connectsViaDataSource) loadDataSourceIsolationLevel = true
                    field = Database.getDefaultIsolationLevel(db)
                }
                db.connectsViaDataSource && loadDataSourceIsolationLevel -> {
                    if (db.dataSourceIsolationLevel != -1) {
                        loadDataSourceIsolationLevel = false
                        field = db.dataSourceIsolationLevel
                    }
                }
            }
            return field
        }

    /**
     * Whether the transaction isolation level of the underlying DataSource should be retrieved from the database.
     *
     * This should only be set to `true` if [Database.connectsViaDataSource] has also been set to `true` and if
     * an initial connection to the database has not already been made.
     */
    private var loadDataSourceIsolationLevel = false

    @Volatile
    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    /** A thread local variable storing the current transaction. */
    val threadLocal = ThreadLocal<JdbcTransaction>()

    override fun toString(): String {
        return "ThreadLocalTransactionManager[${hashCode()}](db=$db)"
    }

    override fun newTransaction(isolation: Int, readOnly: Boolean, outerTransaction: Transaction?): JdbcTransaction {
        val transaction = outerTransaction?.takeIf { !db.useNestedTransactions } as? JdbcTransaction
            ?: JdbcTransaction(
                ThreadLocalTransaction(
                    db = db,
                    readOnly = outerTransaction?.readOnly ?: readOnly,
                    transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                    setupTxConnection = setupTxConnection,
                    threadLocal = threadLocal,
                    outerTransaction = outerTransaction as? JdbcTransaction,
                    loadDataSourceIsolationLevel = loadDataSourceIsolationLevel,
                ),
            )

        return transaction.apply { bindTransactionToThread(this) }
    }

    override fun currentOrNull(): JdbcTransaction? = threadLocal.get()

    override fun bindTransactionToThread(transaction: Transaction?) {
        if (transaction != null) {
            threadLocal.set(transaction as JdbcTransaction)
        } else {
            threadLocal.remove()
        }
    }

    private class ThreadLocalTransaction(
        override val db: Database,
        private val setupTxConnection: ((ExposedConnection<*>, JdbcTransactionInterface) -> Unit)?,
        override val transactionIsolation: Int,
        override val readOnly: Boolean,
        val threadLocal: ThreadLocal<JdbcTransaction>,
        override val outerTransaction: JdbcTransaction?,
        private val loadDataSourceIsolationLevel: Boolean
    ) : JdbcTransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
                @Suppress("TooGenericExceptionCaught")
                try {
                    setupTxConnection?.invoke(this, this@ThreadLocalTransaction) ?: run {
                        // The order of setters here is important.
                        // Transaction isolation should go first as readOnly or autoCommit can start transaction with wrong isolation level
                        // Some drivers start a transaction right after `setAutoCommit(false)`,
                        // which makes `setReadOnly` throw an exception if it is called after `setAutoCommit`
                        if (db.connectsViaDataSource && loadDataSourceIsolationLevel && db.dataSourceIsolationLevel == -1) {
                            // retrieves the setting of the datasource connection & caches it
                            db.dataSourceIsolationLevel = transactionIsolation
                            db.dataSourceReadOnly = readOnly
                        } else if (
                            !db.connectsViaDataSource ||
                            db.dataSourceIsolationLevel != this@ThreadLocalTransaction.transactionIsolation ||
                            db.dataSourceReadOnly != this@ThreadLocalTransaction.readOnly
                        ) {
                            // only set the level if there is no cached datasource value or if the value differs
                            transactionIsolation = this@ThreadLocalTransaction.transactionIsolation
                            readOnly = this@ThreadLocalTransaction.readOnly
                        }
                        autoCommit = false
                    }
                } catch (e: Exception) {
                    try {
                        close()
                    } catch (closeException: Exception) {
                        e.addSuppressed(closeException)
                    }
                    throw e
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
fun <T> transaction(db: Database? = null, statement: JdbcTransaction.() -> T): T =
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
    statement: JdbcTransaction.() -> T
): T = keepAndRestoreTransactionRefAfterRun(db) {
    val outer = TransactionManager.currentOrNull() as? JdbcTransaction

    if (outer != null && (db == null || outer.db == db)) {
        val outerManager = outer.db.transactionManager

        val transaction = outerManager.newTransaction(transactionIsolation, readOnly, outer) as JdbcTransaction
        @Suppress("TooGenericExceptionCaught")
        try {
            transaction.statement().also {
                if (outer.db.useNestedTransactions) {
                    transaction.commit()
                }
            }
        } catch (cause: SQLException) {
            val currentStatement = transaction.currentStatement
            transaction.rollbackLoggingException {
                exposedLogger.warn(
                    "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                    it
                )
            }
            throw cause
        } catch (cause: Throwable) {
            if (outer.db.useNestedTransactions) {
                val currentStatement = transaction.currentStatement
                transaction.rollbackLoggingException {
                    exposedLogger.warn(
                        "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                        it
                    )
                }
            }
            throw cause
        } finally {
            TransactionManager.resetCurrent(outerManager)
        }
    } else {
        val existingForDb = db?.transactionManager
        existingForDb?.currentOrNull()?.let { transaction ->
            val currentManager = outer?.db.transactionManager
            try {
                TransactionManager.resetCurrent(existingForDb)
                (transaction as JdbcTransaction).statement().also {
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
    outerTransaction: JdbcTransaction? = null,
    statement: JdbcTransaction.() -> T
): T {
    fun run(): T {
        var attempts = 0

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
                handleSQLException(cause, transaction, attempts)
                attempts++
                if (attempts >= transaction.maxAttempts) {
                    throw cause
                }

                if (retryInterval == null) {
                    retryInterval = transaction.getRetryInterval()
                    intermediateDelay = transaction.minRetryDelay
                }
                // set delay value with an exponential backoff time period.
                val delay = when {
                    transaction.minRetryDelay < transaction.maxRetryDelay -> {
                        intermediateDelay += retryInterval * attempts
                        ThreadLocalRandom.current().nextLong(intermediateDelay, intermediateDelay + retryInterval)
                    }

                    transaction.minRetryDelay == transaction.maxRetryDelay -> transaction.minRetryDelay
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

internal fun handleSQLException(cause: SQLException, transaction: JdbcTransaction, attempts: Int) {
    val exposedSQLException = cause as? ExposedSQLException
    val queriesToLog = exposedSQLException?.causedByQueries()?.joinToString(";\n") ?: "${transaction.currentStatement}"
    val message = "Transaction attempt #$attempts failed: ${cause.message}. Statement(s): $queriesToLog"
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

internal fun closeStatementsAndConnection(transaction: JdbcTransaction) {
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

package org.jetbrains.exposed.v1.r2dbc.transactions

import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.statements.api.ExposedSavepoint
import org.jetbrains.exposed.v1.core.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.v1.r2dbc.transactions.mtc.MappedTransactionContext
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.CoroutineContext

/**
 * [TransactionManager] implementation registered to the provided database value [db].
 *
 * [setupTxConnection] can be provided to override the default configuration of transaction settings when a
 * connection is retrieved from the database.
 */
class TransactionManager(
    private val db: R2dbcDatabase,
    private val setupTxConnection:
    ((R2dbcExposedConnection<*>, R2dbcTransactionInterface) -> Unit)? = null
) : TransactionManagerApi {
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    /** The default transaction isolation level. Unless specified, the database-specific level will be used. */
    var defaultIsolationLevel: IsolationLevel? = (db.config as R2dbcDatabaseConfig).defaultR2dbcIsolationLevel
        get() = if (field == null) R2dbcDatabase.getDefaultIsolationLevel(db) else field

    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    /** A unique key for storing coroutine context elements, as [TransactionContextHolder]. */
    private val contextKey = object : CoroutineContext.Key<TransactionContextHolder> {}

    internal suspend fun getCurrentContextTransaction(): R2dbcTransaction? {
        return currentCoroutineContext()[contextKey]?.transaction
    }

    internal suspend fun createTransactionContext(transaction: R2dbcTransaction?): CoroutineContext {
        val currentTransaction = transaction ?: getCurrentContextTransaction()
        MappedTransactionContext.setTransaction(currentTransaction)
        return TransactionContextHolder(currentTransaction, contextKey)
    }

    override fun toString(): String {
        return "R2dbcTransactionManager[${hashCode()}](db=$db)"
    }

    /**
     * Returns an [R2dbcTransaction] instance.
     *
     * The returned value may be a new transaction, or it may return the [outerTransaction] if called from within
     * an existing transaction with the database not configured to `useNestedTransactions`.
     */
    fun newTransaction(
        isolation: IsolationLevel = defaultIsolationLevel!!,
        readOnly: Boolean = defaultReadOnly,
        outerTransaction: R2dbcTransaction? = null
    ): R2dbcTransaction {
        val transaction = outerTransaction?.takeIf { !db.useNestedTransactions }
            ?: R2dbcTransaction(
                R2dbcLocalTransaction(
                    db = db,
                    readOnly = outerTransaction?.readOnly ?: readOnly,
                    transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                    setupTxConnection = setupTxConnection,
                    outerTransaction = outerTransaction,
                ),
            )

        return transaction.apply { bindTransactionToThread(this) }
    }

    override fun currentOrNull(): R2dbcTransaction? {
        return MappedTransactionContext.getTransactionOrNull()
    }

    override fun bindTransactionToThread(transaction: Transaction?) {
        if (transaction != null) {
            MappedTransactionContext.setTransaction(transaction as R2dbcTransaction)
        } else {
            MappedTransactionContext.clean()
        }
    }

    companion object {
        /**
         * The database to use by default in all transactions.
         *
         * **Note** If this value is not set, the last [R2dbcDatabase] instance created will be used.
         */
        var defaultDatabase: R2dbcDatabase?
            @Synchronized
            @OptIn(InternalApi::class)
            get() = CoreTransactionManager.getDefaultDatabaseOrFirst() as? R2dbcDatabase

            @Synchronized
            @OptIn(InternalApi::class)
            set(value) {
                CoreTransactionManager.setDefaultDatabase(value)
            }

        /** Associates the provided [database] with a specific [manager]. */
        @Synchronized
        fun registerManager(database: R2dbcDatabase, manager: TransactionManager) {
            @OptIn(InternalApi::class)
            CoreTransactionManager.registerDatabaseManager(database, manager)
        }

        /**
         * Clears any association between the provided [database] and its [TransactionManager],
         * and ensures that the [database] instance will not be available for use in future transactions.
         */
        @Synchronized
        fun closeAndUnregister(database: R2dbcDatabase) {
            @OptIn(InternalApi::class)
            CoreTransactionManager.closeAndUnregisterDatabase(database)
        }

        /**
         * Returns the [TransactionManager] instance that is associated with the provided [database],
         * or `null` if  a manager has not been registered for the [database].
         *
         * **Note** If the provided [database] is `null`, this will return the current thread's [TransactionManager]
         * instance, which may not be initialized if `Database.connect()` was not called at some point previously.
         */
        fun managerFor(database: R2dbcDatabase?): TransactionManager? = if (database != null) {
            @OptIn(InternalApi::class)
            CoreTransactionManager.getDatabaseManager(database) as? TransactionManager
        } else {
            manager
        }

        /** The current thread's [TransactionManager] instance. */
        val manager: TransactionManager
            @OptIn(InternalApi::class)
            get() = CoreTransactionManager.getCurrentThreadManager() as TransactionManager

        /** Sets the current thread's copy of the [TransactionManager] instance to the specified [manager]. */
        fun resetCurrent(manager: TransactionManager?) {
            @OptIn(InternalApi::class)
            CoreTransactionManager.resetCurrentThreadManager(manager)
        }

        /** Returns the current [R2dbcTransaction], or creates a new transaction with the provided [isolation] level. */
        fun currentOrNew(isolation: IsolationLevel): R2dbcTransaction = currentOrNull() ?: manager.newTransaction(isolation)

        /** Returns the current [R2dbcTransaction], or `null` if none exists. */
        fun currentOrNull(): R2dbcTransaction? = manager.currentOrNull()

        /**
         * Returns the current [R2dbcTransaction].
         *
         * @throws [IllegalStateException] If no transaction exists.
         */
        fun current(): R2dbcTransaction = currentOrNull() ?: error("No transaction in context.")

        /** Whether any [TransactionManager] instance has been initialized by a database. */
        fun isInitialized(): Boolean {
            @OptIn(InternalApi::class)
            return CoreTransactionManager.getDefaultDatabaseOrFirst() != null
        }
    }

    private class R2dbcLocalTransaction(
        override val db: R2dbcDatabase,
        private val setupTxConnection:
        ((R2dbcExposedConnection<*>, R2dbcTransactionInterface) -> Unit)?,
        override val transactionIsolation: IsolationLevel,
        override val readOnly: Boolean,
        override val outerTransaction: R2dbcTransaction?,
    ) : R2dbcTransactionInterface {

        private var connectionLazy: R2dbcExposedConnection<*>? = null

        private suspend fun getConnection(): R2dbcExposedConnection<*> = outerTransaction?.connection()
            ?.also {
                if (useSavePoints) {
                    savepoint = it.setSavepoint(savepointName)
                }
            }
            ?: db.connector().apply {
                @Suppress("TooGenericExceptionCaught")
                try {
                    setupTxConnection?.invoke(this, this@R2dbcLocalTransaction) ?: run {
                        setTransactionIsolation(this@R2dbcLocalTransaction.transactionIsolation)
                        setReadOnly(this@R2dbcLocalTransaction.readOnly)
                        // potentially redundant if R2dbcConnectionImpl calls beginTransaction(), which disables autoCommit
                        setAutoCommit(false)
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

        override suspend fun connection(): R2dbcExposedConnection<*> = connectionLazy
            ?: getConnection().also { connectionLazy = it }

        private val useSavePoints = outerTransaction != null && db.useNestedTransactions

        private var savepoint: ExposedSavepoint? = null

        override suspend fun commit() {
            if (connectionLazy.isInitialized()) {
                if (!useSavePoints) {
                    connection().commit()
                } else {
                    // do nothing in nested. close() will commit everything and release savepoint.
                }
            }
        }

        override suspend fun rollback() {
            if (connectionLazy.isInitialized() && !connection().isClosed()) {
                if (useSavePoints && savepoint != null) {
                    connection().rollback(savepoint!!)
                    savepoint = connection().setSavepoint(savepointName)
                } else {
                    connection().rollback()
                }
            }
        }

        override suspend fun close() {
            try {
                if (!useSavePoints) {
                    if (connectionLazy.isInitialized()) connection().close()
                } else {
                    savepoint?.let {
                        connection().releaseSavepoint(it)
                        savepoint = null
                    }
                }
            } finally {
                MappedTransactionContext.setTransaction(outerTransaction)
            }
        }

        private fun R2dbcExposedConnection<*>?.isInitialized(): Boolean = this != null

        private val savepointName: String
            get() {
                var nestedLevel = 0
                var currentTransaction = outerTransaction
                while (currentTransaction?.outerTransaction != null) {
                    nestedLevel++
                    currentTransaction = currentTransaction.outerTransaction
                }
                return "Exposed_savepoint_$nestedLevel"
            }
    }
}

/**
 * Singleton coroutine context element storing its associated transaction
 * & the unique key for its [TransactionManager.transactionLocal].
 */
private data class TransactionContextHolder(
    val transaction: R2dbcTransaction?,
    override val key: CoroutineContext.Key<*>
) : CoroutineContext.Element

@Deprecated(
    message = "This method overload will be removed in release 1.0.0. It should be replaced with either overload" +
        "that does not take a `CoroutineContext` as an argument.",
    level = DeprecationLevel.ERROR
)
@Suppress("UnusedParameter")
suspend fun <T> suspendTransaction(
    context: CoroutineContext? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean? = null,
    db: R2dbcDatabase? = null,
    statement: suspend R2dbcTransaction.() -> T
): T = suspendTransaction(
    transactionIsolation ?: db.transactionManager.defaultIsolationLevel ?: error("Default transaction isolation not set"),
    readOnly ?: db.transactionManager.defaultReadOnly,
    db,
    statement
)

@Deprecated(
    message = "This method overload will be removed in release 1.0.0. It should be replaced with either overload" +
        "that does not take a `CoroutineContext` as an argument, and that is wrapped with `async { }`.",
    level = DeprecationLevel.ERROR
)
@Suppress("UnusedParameter")
suspend fun <T> suspendTransactionAsync(
    context: CoroutineContext? = null,
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean? = null,
    statement: suspend R2dbcTransaction.() -> T
): Deferred<T> = CompletableDeferred(
    suspendTransaction(
        transactionIsolation ?: db.transactionManager.defaultIsolationLevel ?: error("Default transaction isolation not set"),
        readOnly ?: db.transactionManager.defaultReadOnly,
        db,
        statement
    )
)

/**
 * Creates a transaction then calls the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [R2dbcDatabase] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @return The final result of the [statement] block.
 */
suspend fun <T> suspendTransaction(db: R2dbcDatabase? = null, statement: suspend R2dbcTransaction.() -> T): T {
    val defaultIsolation = db.transactionManager.defaultIsolationLevel
    require(defaultIsolation != null) { "A default isolation level for this transaction has not been set" }

    return suspendTransaction(
        defaultIsolation,
        db.transactionManager.defaultReadOnly,
        db,
        statement
    )
}

/**
 * Creates a transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [R2dbcDatabase] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 */
suspend fun <T> suspendTransaction(
    transactionIsolation: IsolationLevel,
    readOnly: Boolean = false,
    db: R2dbcDatabase? = null,
    statement: suspend R2dbcTransaction.() -> T
): T = keepAndRestoreTransactionRefAfterRun(db) {
    val outer = TransactionManager.currentOrNull()

    if (outer != null && (db == null || outer.db == db)) {
        val outerManager = outer.db.transactionManager

        val transaction = outerManager.newTransaction(transactionIsolation, readOnly, outer)
        withTransactionContext(transaction) {
            @Suppress("TooGenericExceptionCaught")
            try {
                transaction.statement().also {
                    if (outer.db.useNestedTransactions) {
                        transaction.commit()
                    }
                }
            } catch (cause: R2dbcException) {
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
            }
        }
    } else {
        db?.transactionManager?.getCurrentContextTransaction()?.let { transaction ->
            withTransactionContext(transaction) {
                transaction.statement().also {
                    if (transaction.db.useNestedTransactions) {
                        transaction.commit()
                    }
                }
            }
        }
            ?: inTopLevelSuspendTransaction(
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
 * it is nested and even if `R2dbcDatabaseConfig.useNestedTransactions` is set to `false`.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [R2dbcDatabase] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @return The final result of the [statement] block.
 */
suspend fun <T> inTopLevelSuspendTransaction(
    transactionIsolation: IsolationLevel,
    readOnly: Boolean = false,
    db: R2dbcDatabase? = null,
    outerTransaction: R2dbcTransaction? = null,
    statement: suspend R2dbcTransaction.() -> T
): T {
    suspend fun run(): T {
        var attempts = 0
        var intermediateDelay: Long = 0
        var retryInterval: Long? = null

        val outerManager = outerTransaction?.db.transactionManager.takeIf { it.currentOrNull() != null }

        while (true) {
            db?.let { db.transactionManager.let { m -> TransactionManager.resetCurrent(m) } }
            val transaction = db.transactionManager.newTransaction(transactionIsolation, readOnly, outerTransaction)
            val context = db.transactionManager.createTransactionContext(transaction)

            @Suppress("TooGenericExceptionCaught")
            try {
                val answer: T
                withContext(context) {
                    transaction.db.config.defaultSchema?.let { SchemaUtils.setSchema(it) }
                    answer = transaction.statement()
                    transaction.commit()
                }
                return answer
            } catch (cause: R2dbcException) {
                handleR2dbcException(cause, transaction, attempts)
                attempts++
                if (attempts >= transaction.maxAttempts) {
                    throw cause
                }

                if (retryInterval == null) {
                    retryInterval = transaction.getRetryInterval()
                    intermediateDelay = transaction.minRetryDelay
                }
                // set delay value with an exponential backoff time period.
                val retryDelay = when {
                    transaction.minRetryDelay < transaction.maxRetryDelay -> {
                        intermediateDelay += retryInterval * attempts
                        ThreadLocalRandom.current().nextLong(intermediateDelay, intermediateDelay + retryInterval)
                    }

                    transaction.minRetryDelay == transaction.maxRetryDelay -> transaction.minRetryDelay
                    else -> 0
                }
                exposedLogger.warn("Wait $retryDelay milliseconds before retrying")
                try {
                    delay(retryDelay)
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

private suspend fun <T> keepAndRestoreTransactionRefAfterRun(db: R2dbcDatabase? = null, block: suspend () -> T): T {
    val manager = db.transactionManager
    val currentTransaction = manager.currentOrNull()
    return try {
        block()
    } finally {
        manager.bindTransactionToThread(currentTransaction)
    }
}

internal suspend fun handleR2dbcException(cause: R2dbcException, transaction: R2dbcTransaction, attempts: Int) {
    val exposedR2dbcException = cause as? ExposedR2dbcException
    val queriesToLog = exposedR2dbcException?.causedByQueries()?.joinToString(";\n") ?: "${transaction.currentStatement}"
    val message = "Transaction attempt #$attempts failed: ${cause.message}. Statement(s): $queriesToLog"
    exposedR2dbcException?.contexts?.forEach {
        transaction.interceptors.filterIsInstance<SqlLogger>().forEach { logger ->
            logger.log(it, transaction)
        }
    }
    exposedLogger.warn(message, cause)
    transaction.rollbackLoggingException {
        exposedLogger.warn("Transaction rollback failed: ${it.message}. See previous log line for statement", it)
    }
}

internal suspend fun closeStatementsAndConnection(transaction: R2dbcTransaction) {
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

/**
 * The method creates context with provided transaction and runs code block within that context.
 *
 * @param transaction The transaction to be used in the context.
 * @param body The code block to be executed in the context.
 * @return The result of executing the code block.
 */
internal suspend fun <T> withTransactionContext(transaction: R2dbcTransaction, body: suspend () -> T): T {
    val outerTransaction = transaction.outerTransaction

    val context = transaction.db.transactionManager.createTransactionContext(transaction)

    return try {
        TransactionManager.resetCurrent(transaction.db.transactionManager)
        MappedTransactionContext.setTransaction(transaction)
        withContext(context) {
            body()
        }
    } finally {
        outerTransaction?.let { MappedTransactionContext.setTransaction(it) }
            ?: MappedTransactionContext.clean()

        TransactionManager.resetCurrent(outerTransaction?.db.transactionManager)
    }
}

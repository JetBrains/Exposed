package org.jetbrains.exposed.r2dbc.sql.transactions

import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.r2dbc.exceptions.ExposedR2dbcException
import org.jetbrains.exposed.r2dbc.sql.R2dbcDatabase
import org.jetbrains.exposed.r2dbc.sql.R2dbcDatabaseConfig
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.r2dbc.sql.mtc.MappedTransactionContext
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import org.jetbrains.exposed.sql.transactions.CoreTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManagerApi
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * [TransactionManager] implementation registered to the provided database value [db].
 *
 * [setupTxConnection] can be provided to override the default configuration of transaction settings when a
 * connection is retrieved from the database.
 */
class TransactionManager(
    private val db: R2dbcDatabase,
    private val setupTxConnection: ((R2dbcExposedConnection<*>, R2dbcTransactionInterface) -> Unit)? = null
) : TransactionManagerApi {
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    /** The default transaction isolation level. Unless specified, the database-specific level will be used. */
    var defaultIsolationLevel: IsolationLevel? = (db.config as R2dbcDatabaseConfig).defaultR2dbcIsolationLevel
        get() = if (field == null) R2dbcDatabase.getDefaultIsolationLevel(db) else field

    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    val threadLocal = ThreadLocal<R2dbcTransaction>()

    override fun toString(): String {
        return "R2dbcTransactionManager[${hashCode()}](db=$db)"
    }

    /**
     * Returns a [R2dbcTransaction] instance.
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
                R2dbcThreadLocalTransaction(
                    db = db,
                    readOnly = outerTransaction?.readOnly ?: readOnly,
                    transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                    setupTxConnection = setupTxConnection,
                    threadLocal = threadLocal,
                    outerTransaction = outerTransaction,
                ),
            )

        return transaction.apply { bindTransactionToThread(this) }
    }

    override fun currentOrNull(): R2dbcTransaction? {
        return threadLocal.get() ?: MappedTransactionContext.getTransactionOrNull()
    }

    override fun bindTransactionToThread(transaction: Transaction?) {
        if (transaction != null) {
            threadLocal.set(transaction as R2dbcTransaction)
            MappedTransactionContext.setTransaction(transaction)
        } else {
            threadLocal.remove()
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

        /** Returns the current [Transaction], or creates a new transaction with the provided [isolation] level. */
        fun currentOrNew(isolation: IsolationLevel): R2dbcTransaction = currentOrNull() ?: manager.newTransaction(isolation)

        /** Returns the current [Transaction], or `null` if none exists. */
        fun currentOrNull(): R2dbcTransaction? = manager.currentOrNull()

        /**
         * Returns the current [Transaction].
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

    private class R2dbcThreadLocalTransaction(
        override val db: R2dbcDatabase,
        private val setupTxConnection: ((R2dbcExposedConnection<*>, R2dbcTransactionInterface) -> Unit)?,
        override val transactionIsolation: IsolationLevel,
        override val readOnly: Boolean,
        val threadLocal: ThreadLocal<R2dbcTransaction>,
        override val outerTransaction: R2dbcTransaction?,
    ) : R2dbcTransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
                @Suppress("TooGenericExceptionCaught")
                try {
                    // TODO assess need for property suspend setters vs Lazy usage
                    setupTxConnection?.invoke(this, this@R2dbcThreadLocalTransaction)
//                        ?: runBlocking {
//                            setTransactionIsolation(this@R2dbcThreadLocalTransaction.transactionIsolation)
//                            setReadOnly(this@R2dbcThreadLocalTransaction.readOnly)
//                            // potentially redundant if R2dbcConnectionImpl calls beginTransaction(), which disables autoCommit
//                            setAutoCommit(false)
//                        }
                } catch (e: Exception) {
                    try {
                        // suspend alternative for lazy?
//                        close()
                    } catch (closeException: Exception) {
                        e.addSuppressed(closeException)
                    }
                    throw e
                }
            }
        }

        override val connection: R2dbcExposedConnection<*>
            get() = connectionLazy.value

        private val useSavePoints = outerTransaction != null && db.useNestedTransactions

        // Todo replace runBlocking()
        // TODO swap out lazy for fake connection in transaction manager +/- other lazy uses in connectionImpl
        // property needs to (possibly) be initialized with return value of a suspend function;
        // this suspend function must be called as soon as transaction is opened, so lazySuspend options not sufficient;
        // need something like initSuspend { }
        // Option 1: launch coroutine
        // Option 2: suspend operator fun invoke() -> all invoking functions are not suspending though...
        // Option 3: suspend factory method -> same reason as above...
        // Option 4: re-assess whether connection.setSavepoint() must suspend???
        // OG below:
//        private var savepoint: ExposedSavepoint? = if (useSavePoints) connection.setSavepoint(savepointName) else null
        private var savepoint: ExposedSavepoint? = if (useSavePoints) {
            runBlocking { connection.setSavepoint(savepointName) }
        } else {
            null
        }

        override suspend fun commit() {
            if (connectionLazy.isInitialized()) {
                if (!useSavePoints) {
                    connection.commit()
                } else {
                    // do nothing in nested. close() will commit everything and release savepoint.
                }
            }
        }

        override suspend fun rollback() {
            if (connectionLazy.isInitialized() && !connection.isClosed()) {
                if (useSavePoints && savepoint != null) {
                    connection.rollback(savepoint!!)
                    savepoint = connection.setSavepoint(savepointName)
                } else {
                    connection.rollback()
                }
            }
        }

        override suspend fun close() {
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
                MappedTransactionContext.setTransaction(outerTransaction)
            }
        }

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

// TODO naming of new Api
// push decision to public slack poll

/**
 * Creates a new `TransactionScope` then calls the specified suspending [statement], suspends until it completes,
 * and returns the result.
 *
 * The `TransactionScope` is derived from a new `Transaction` and a given coroutine [context],
 * or the current [CoroutineContext] if no [context] is provided.
 */
suspend fun <T> suspendTransaction(
    context: CoroutineContext? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean? = null,
    db: R2dbcDatabase? = null,
    statement: suspend R2dbcTransaction.() -> T
): T =
    withTransactionScope(context, null, db, transactionIsolation, readOnly) {
        suspendedTransactionAsyncInternal(true, statement).await()
    }

/**
 * Calls the specified suspending [statement], suspends until it completes, and returns the result.
 *
 * The resulting `TransactionScope` is derived from the current [CoroutineContext] if the latter already holds
 * [this] `Transaction`; otherwise, a new scope is created using [this] `Transaction` and a given coroutine [context].
 */
suspend fun <T> R2dbcTransaction.suspendTransaction(
    context: CoroutineContext? = null,
    statement: suspend R2dbcTransaction.() -> T
): T {
    val innerShouldBeNested = this.db.useNestedTransactions == true
    return withTransactionScope(context, this, db = null, transactionIsolation = null, readOnly = null) {
        suspendedTransactionAsyncInternal(innerShouldBeNested, statement).await()
    }
}

/**
 * Creates a new `TransactionScope` and returns its future result as an implementation of [Deferred].
 *
 * The `TransactionScope` is derived from a new `Transaction` and a given coroutine [context],
 * or the current [CoroutineContext] if no [context] is provided.
 */
suspend fun <T> suspendTransactionAsync(
    context: CoroutineContext? = null,
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean? = null,
    statement: suspend R2dbcTransaction.() -> T
): Deferred<T> {
    val currentTransaction = TransactionManager.currentOrNull()
    return withTransactionScope(context, null, db, transactionIsolation, readOnly) {
        suspendedTransactionAsyncInternal(!holdsSameTransaction(currentTransaction), statement)
    }
}

private suspend fun R2dbcTransaction.closeAsync() {
    val currentTransaction = TransactionManager.currentOrNull()
    try {
        val temporaryManager = this.db.transactionManager
        temporaryManager.bindTransactionToThread(this)
        TransactionManager.resetCurrent(temporaryManager)
    } finally {
        closeStatementsAndConnection(this)
        val transactionManager = currentTransaction?.db?.transactionManager
        transactionManager?.bindTransactionToThread(currentTransaction)
        TransactionManager.resetCurrent(transactionManager)
    }
}

private suspend fun <T> withTransactionScope(
    context: CoroutineContext?,
    currentTransaction: R2dbcTransaction?,
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel?,
    readOnly: Boolean?,
    body: suspend TransactionScope.() -> T
): T {
    val currentScope = coroutineContext[TransactionScope]

    @OptIn(InternalApi::class)
    suspend fun newScope(currentTransaction: R2dbcTransaction?): T {
        val currentDatabase: R2dbcDatabase? = currentTransaction?.db
            ?: db
            ?: CoreTransactionManager.getDefaultDatabase() as? R2dbcDatabase
        val manager = currentDatabase?.transactionManager ?: TransactionManager.manager

        val tx = lazy(LazyThreadSafetyMode.NONE) {
            manager.newTransaction(
                isolation = transactionIsolation ?: manager.defaultIsolationLevel ?: error("Default transaction isolation not set"),
                readOnly = readOnly ?: manager.defaultReadOnly,
                outerTransaction = currentTransaction
            )
        }

        val element = TransactionCoroutineElement(tx, manager)

        val newContext = context ?: coroutineContext

        return try {
            TransactionScope(tx, newContext + element).body()
        } finally {
            // TODO Is it enough to clean the context? How to not forget to do that in new usages?
            element.cleanCurrentTransaction()
        }
    }

    val sameTransaction = currentScope?.holdsSameTransaction(currentTransaction) == true
    val sameContext = context == coroutineContext
    return when {
        currentScope == null -> newScope(currentTransaction)
        sameTransaction && sameContext -> currentScope.body()
        else -> newScope(currentTransaction)
    }
}

private suspend fun R2dbcTransaction.resetIfClosed(): R2dbcTransaction {
    return if (connection.isClosed()) {
        // Repetition attempts will throw org.h2.jdbc.JdbcSQLException: The object is already closed
        // unless the transaction is reset before every attempt (after the 1st failed attempt)
        val currentManager = db.transactionManager
        currentManager.bindTransactionToThread(this)
        TransactionManager.resetCurrent(currentManager)
        currentManager.newTransaction(transactionIsolation, readOnly, outerTransaction)
    } else {
        this
    }
}

@Suppress("CyclomaticComplexMethod")
private fun <T> TransactionScope.suspendedTransactionAsyncInternal(
    shouldCommit: Boolean,
    statement: suspend R2dbcTransaction.() -> T
): Deferred<T> = async {
    var attempts = 0
    var intermediateDelay: Long = 0
    var retryInterval: Long? = null

    var answer: T
    while (true) {
        val transaction = if (attempts == 0) tx.value else tx.value.resetIfClosed()

        @Suppress("TooGenericExceptionCaught")
        try {
            transaction.db.config.defaultSchema?.let { SchemaUtils.setSchema(it) }
            answer = transaction.statement().apply {
                if (shouldCommit) transaction.commit()
            }
            break
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
            // set delay value with an exponential backoff time period
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
                exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it)
            }
            throw cause
        } finally {
            if (shouldCommit) transaction.closeAsync()
        }
    }
    answer
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

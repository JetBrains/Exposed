package org.jetbrains.exposed.sql.transactions

import org.jetbrains.annotations.TestOnly
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint

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
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    @Volatile
    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    @Volatile
    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMaxAttempts"),
        level = DeprecationLevel.ERROR
    )
    override var defaultRepetitionAttempts: Int
        get() = defaultMaxAttempts
        set(value) { defaultMaxAttempts = value }

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMinRetryDelay"),
        level = DeprecationLevel.ERROR
    )
    override var defaultMinRepetitionDelay: Long
        get() = defaultMinRetryDelay
        set(value) { defaultMinRetryDelay = value }

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMaxRetryDelay"),
        level = DeprecationLevel.ERROR
    )
    override var defaultMaxRepetitionDelay: Long
        get() = defaultMaxRetryDelay
        set(value) { defaultMaxRetryDelay = value }

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

        @Deprecated("Use DatabaseConfig to define the defaultIsolationLevel", level = DeprecationLevel.ERROR)
        @TestOnly
        set

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
        val transaction = outerTransaction
            ?.takeIf { !db.useNestedTransactions } as? JdbcTransaction
            ?: JdbcTransaction(
                ThreadLocalTransaction(
                    db = db,
                    readOnly = outerTransaction?.readOnly ?: readOnly,
                    transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                    setupTxConnection = setupTxConnection,
                    threadLocal = threadLocal,
                    outerTransaction = outerTransaction as? JdbcTransaction,
                    loadDataSourceIsolationLevel = loadDataSourceIsolationLevel,
                )
            )

        return transaction.apply { bindTransactionToThread(this) }
    }

    override fun currentOrNull(): Transaction? = threadLocal.get()

    override fun bindTransactionToThread(transaction: Transaction?) {
        if (transaction != null) {
            threadLocal.set(transaction as JdbcTransaction)
        } else {
            threadLocal.remove()
        }
    }

    private class ThreadLocalTransaction(
        override val db: Database,
        private val setupTxConnection: ((ExposedConnection<*>, TransactionInterface) -> Unit)?,
        override val transactionIsolation: Int,
        override val readOnly: Boolean,
        val threadLocal: ThreadLocal<JdbcTransaction>,
        override val outerTransaction: JdbcTransaction?,
        private val loadDataSourceIsolationLevel: Boolean
    ) : TransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
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
                    currenTransaction = currenTransaction.outerTransaction as JdbcTransaction
                }
                return "Exposed_savepoint_$nestedLevel"
            }
    }
}

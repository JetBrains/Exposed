package org.jetbrains.exposed.sql.transactions

import org.jetbrains.annotations.TestOnly
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint

@Deprecated(
    message = "This class will be removed entirely in future releases.",
    replaceWith = ReplaceWith("TransactionManager"),
    level = DeprecationLevel.WARNING
)
class ThreadLocalTransactionManager(
    private val db: Database,
    private val setupTxConnection: ((ExposedConnection<*>, TransactionInterface) -> Unit)? = null
) : TransactionManagerApi {
    @Volatile
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    @Volatile
    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    @Volatile
    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    @Volatile
    var defaultIsolationLevel: Int = db.config.defaultIsolationLevel
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

        @Deprecated("Use DatabaseConfig to define the defaultIsolationLevel", level = DeprecationLevel.HIDDEN)
        @TestOnly
        set

    private var loadDataSourceIsolationLevel = false

    @Volatile
    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    val threadLocal = ThreadLocal<JdbcTransaction>()

    override fun toString(): String {
        return "ThreadLocalTransactionManager[${hashCode()}](db=$db)"
    }

    fun newTransaction(
        isolation: Int = defaultIsolationLevel,
        readOnly: Boolean = defaultReadOnly,
        outerTransaction: JdbcTransaction? = null
    ): JdbcTransaction {
        val transaction = outerTransaction?.takeIf { !db.useNestedTransactions }
            ?: JdbcTransaction(
                ThreadLocalTransaction(
                    db = db,
                    readOnly = outerTransaction?.readOnly ?: readOnly,
                    transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                    setupTxConnection = setupTxConnection,
                    threadLocal = threadLocal,
                    outerTransaction = outerTransaction,
                    loadDataSourceIsolationLevel = loadDataSourceIsolationLevel,
                )
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
        private val setupTxConnection: ((ExposedConnection<*>, TransactionInterface) -> Unit)?,
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

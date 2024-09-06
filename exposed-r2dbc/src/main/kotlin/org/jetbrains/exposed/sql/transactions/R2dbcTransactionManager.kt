package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint

// this might be quite unnecessary given the amount of redundancy! Can the behavior that needs to change be extracted?
class R2dbcTransactionManager(
    private val db: R2dbcDatabase,
    private val setupTxConnection: ((ExposedConnection<*>, TransactionInterface) -> Unit)? = null
) : TransactionManager {
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

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

    override var defaultIsolationLevel: Int = db.config.defaultIsolationLevel
        get() = if (field == -1) R2dbcDatabase.getDefaultIsolationLevel(db) else field

    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    // coroutine equivalent as context element?
    val threadLocal = ThreadLocal<Transaction>()

    override fun toString(): String {
        return "R2dbcTransactionManager[${hashCode()}](db=$db)"
    }

    override fun newTransaction(isolation: Int, readOnly: Boolean, outerTransaction: Transaction?): Transaction {
        val transaction = outerTransaction?.takeIf { !db.useNestedTransactions } ?: Transaction(
            R2dbcThreadLocalTransaction(
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

    private class R2dbcThreadLocalTransaction(
        override val db: R2dbcDatabase,
        private val setupTxConnection: ((ExposedConnection<*>, TransactionInterface) -> Unit)?,
        override val transactionIsolation: Int,
        override val readOnly: Boolean,
        val threadLocal: ThreadLocal<Transaction>,
        override val outerTransaction: Transaction?
    ) : TransactionInterface {
        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
                setupTxConnection?.invoke(this, this@R2dbcThreadLocalTransaction)
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
                var currentTransaction = outerTransaction
                while (currentTransaction?.outerTransaction != null) {
                    nestedLevel++
                    currentTransaction = currentTransaction.outerTransaction
                }
                return "Exposed_savepoint_$nestedLevel"
            }
    }
}

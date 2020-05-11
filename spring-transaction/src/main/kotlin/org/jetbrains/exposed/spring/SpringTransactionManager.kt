package org.jetbrains.exposed.spring

import org.jetbrains.exposed.dao.DaoTransaction
import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.dao.ICache
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactionManager
import org.jetbrains.exposed.sql.transactions.*
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource


class SpringTransactionManager(private val _dataSource: DataSource,
                               @Volatile override var defaultRepetitionAttempts: Int = DEFAULT_REPETITION_ATTEMPTS
) : DataSourceTransactionManager(_dataSource), ITransactionManager {

    init {
        this.isRollbackOnCommitFailure = true
    }

    private val db = Database.connect(_dataSource) { this }

    @Volatile override var defaultIsolationLevel: Int = -1
        get() {
            if (field == -1) {
                field = Database.getDefaultIsolationLevel(db)
            }
            return field
        }

    private val springTxKey = "SPRING_TX_KEY"

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        super.doBegin(transaction, definition)

        if (TransactionSynchronizationManager.hasResource(_dataSource)) {
            currentOrNull() ?: initTransaction()
        }
        if (!TransactionSynchronizationManager.hasResource(springTxKey)) {
            TransactionSynchronizationManager.bindResource(springTxKey, transaction)
        }
    }

    override fun doCleanupAfterCompletion(transaction: Any) {
        super.doCleanupAfterCompletion(transaction)
        if (!TransactionSynchronizationManager.hasResource(_dataSource)) {
            TransactionSynchronizationManager.unbindResourceIfPossible(this)
        }
        ITransactionManager.resetCurrent(null)
    }

    override fun doSuspend(transaction: Any): Any {
        TransactionSynchronizationManager.unbindResourceIfPossible(this)
        return super.doSuspend(transaction)
    }

    override fun doCommit(status: DefaultTransactionStatus) {
        try {
            currentOrNull()?.commit()
        } catch (e: Exception) {
            throw TransactionSystemException(e.message.orEmpty(), e)
        }
    }

    override fun doRollback(status: DefaultTransactionStatus) {
        try {
            currentOrNull()?.rollback()
        } catch (e: Exception) {
            throw TransactionSystemException(e.message.orEmpty(), e)
        }
    }

    override fun newTransaction(isolation: Int, outerTransaction: ITransaction?): ITransaction {
        val tDefinition = DefaultTransactionDefinition().apply { isolationLevel = isolation }

        getTransaction(tDefinition)

        return currentOrNull() ?: initTransaction()
    }

    private fun initTransaction(): ITransaction {
        val connection = (TransactionSynchronizationManager.getResource(_dataSource) as ConnectionHolder).connection

        val transactionImpl = SpringTransaction(JdbcConnectionImpl(connection), db, defaultIsolationLevel, currentOrNull())
        ITransactionManager.resetCurrent(this)
        return transactionImpl.apply {
            TransactionSynchronizationManager.bindResource(this@SpringTransactionManager, this)
        }
    }

    override fun currentOrNull(): ITransaction? = TransactionSynchronizationManager.getResource(this) as ITransaction?

    override fun <T> keepAndRestoreTransactionRefAfterRun(db: Database?, block: () -> T): T {
        val manager = db.transactionManager
        val currentTransaction = manager.currentOrNull()
        return try {
            block()
        } finally {
            manager.bindTransactionToThread(currentTransaction)
        }
    }

    override fun bindTransactionToThread(transaction: ITransaction?) {
        if (transaction != null) {
            bindResourceForSure(this, transaction)
        } else {
            TransactionSynchronizationManager.unbindResourceIfPossible(this)
        }
    }

    private fun bindResourceForSure(key: Any, value: Any) {
        TransactionSynchronizationManager.unbindResourceIfPossible(key)
        TransactionSynchronizationManager.bindResource(key, value)
    }

    private inner class SpringTransaction(
        override val connection: ExposedConnection<*>,
        override val db: Database,
        override val transactionIsolation: Int,
        override val outerTransaction: ITransaction?
    ) : DaoTransaction(db, transactionIsolation, outerTransaction, null, false) {

        override fun txCommit() {
            connection.run {
                if (!autoCommit) {
                    commit()
                }
            }
        }

        override fun txRollback() {
            connection.rollback()
        }

        override fun close() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.getResource(springTxKey)?.let { springTx ->
                    this@SpringTransactionManager.doCleanupAfterCompletion(springTx)
                    TransactionSynchronizationManager.unbindResource(springTxKey)
                }
            }
        }
    }

}

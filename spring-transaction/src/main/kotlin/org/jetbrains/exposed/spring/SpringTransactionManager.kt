package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.DEFAULT_READ_ONLY
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource

class SpringTransactionManager(
    _dataSource: DataSource,
    databaseConfig: DatabaseConfig = DatabaseConfig{ },
    private val showSql: Boolean = false,
    @Volatile override var defaultReadOnly: Boolean = DEFAULT_READ_ONLY,
    @Volatile override var defaultRepetitionAttempts: Int = DEFAULT_REPETITION_ATTEMPTS
) : DataSourceTransactionManager(_dataSource), TransactionManager {

    init {
        this.isRollbackOnCommitFailure = true
    }

    private val db = Database.connect(
        datasource = _dataSource,
        databaseConfig = databaseConfig
    ) { this }

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

        if (TransactionSynchronizationManager.hasResource(obtainDataSource())) {
            currentOrNull() ?: initTransaction()
        }
        if (!TransactionSynchronizationManager.hasResource(springTxKey)) {
            TransactionSynchronizationManager.bindResource(springTxKey, transaction)
        }
    }

    override fun doCleanupAfterCompletion(transaction: Any) {
        super.doCleanupAfterCompletion(transaction)
        if (!TransactionSynchronizationManager.hasResource(obtainDataSource())) {
            TransactionSynchronizationManager.unbindResourceIfPossible(this)
            TransactionSynchronizationManager.unbindResource(springTxKey)
        }
        if (TransactionSynchronizationManager.isSynchronizationActive() && TransactionSynchronizationManager.getSynchronizations().isEmpty()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        TransactionManager.resetCurrent(null)
    }

    override fun doSuspend(transaction: Any): Any {
        TransactionSynchronizationManager.unbindResourceIfPossible(this)
        return super.doSuspend(transaction)
    }

    override fun doCommit(status: DefaultTransactionStatus) {
        @Suppress("TooGenericExceptionCaught")
        try {
            currentOrNull()?.commit()
        } catch (e: Exception) {
            throw TransactionSystemException(e.message.orEmpty(), e)
        }
    }

    override fun doRollback(status: DefaultTransactionStatus) {
        @Suppress("TooGenericExceptionCaught")
        try {
            currentOrNull()?.rollback()
        } catch (e: Exception) {
            throw TransactionSystemException(e.message.orEmpty(), e)
        }
    }

    override fun newTransaction(isolation: Int, readOnly: Boolean, outerTransaction: Transaction?): Transaction {
        val tDefinition = DefaultTransactionDefinition().apply {
            isReadOnly = readOnly
            isolationLevel = isolation
        }

        getTransaction(tDefinition)

        return currentOrNull() ?: initTransaction()
    }

    private fun initTransaction(): Transaction {
        val connection = (TransactionSynchronizationManager.getResource(obtainDataSource()) as ConnectionHolder).connection

        val transactionImpl = try {
            SpringTransaction(JdbcConnectionImpl(connection), db, defaultIsolationLevel, defaultReadOnly, currentOrNull())
        } catch (e: Exception) {
            exposedLogger.error("Failed to start transaction. Connection will be closed.", e)
            connection.close()
            throw e
        }
        TransactionManager.resetCurrent(this)
        return Transaction(transactionImpl).apply {
            TransactionSynchronizationManager.bindResource(this@SpringTransactionManager, this)
            if (showSql) {
                addLogger(StdOutSqlLogger)
            }
        }
    }

    override fun currentOrNull(): Transaction? = TransactionSynchronizationManager.getResource(this) as Transaction?
    override fun bindTransactionToThread(transaction: Transaction?) {
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
        override val readOnly: Boolean,
        override val outerTransaction: Transaction?
    ) : TransactionInterface {

        override fun commit() {
            connection.commit()
        }

        override fun rollback() {
            connection.rollback()
        }

        override fun close() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.getResource(springTxKey)?.let { springTx ->
                    this@SpringTransactionManager.doCleanupAfterCompletion(springTx)
                }
            }
        }
    }
}

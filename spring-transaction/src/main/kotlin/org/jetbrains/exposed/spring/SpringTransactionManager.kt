package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.DEFAULT_ISOLATION_LEVEL
import org.jetbrains.exposed.sql.transactions.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Connection
import javax.sql.DataSource


class SpringTransactionManager(private val _dataSource: DataSource,
                               @Volatile override var defaultIsolationLevel: Int = DEFAULT_ISOLATION_LEVEL,
                               @Volatile override var defaultRepetitionAttempts: Int = DEFAULT_REPETITION_ATTEMPTS
) : DataSourceTransactionManager(_dataSource), TransactionManager {

    private val db = Database.connect(_dataSource) { this }

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        super.doBegin(transaction, definition)

        if (TransactionSynchronizationManager.hasResource(_dataSource)) {
            currentOrNull() ?: initTransaction()
        }
    }

    override fun doCleanupAfterCompletion(transaction: Any) {
        super.doCleanupAfterCompletion(transaction)
        if (!TransactionSynchronizationManager.hasResource(_dataSource)) {
            TransactionSynchronizationManager.unbindResourceIfPossible(this)
        }
        TransactionManager.resetCurrent(null)
    }

    override fun doSuspend(transaction: Any): Any {
        TransactionSynchronizationManager.unbindResourceIfPossible(this)
        return super.doSuspend(transaction)
    }

    override fun doCommit(status: DefaultTransactionStatus) {
        currentOrNull()?.commit()
    }

    override fun newTransaction(isolation: Int, outerTransaction: Transaction?): Transaction {
        val tDefinition = dataSource?.connection?.transactionIsolation?.takeIf { it != isolation }?.let {
                DefaultTransactionDefinition().apply { isolationLevel = isolation }
        }

        getTransaction(tDefinition)

        return currentOrNull() ?: initTransaction()
    }

    private fun initTransaction(): Transaction {
        val connection = (TransactionSynchronizationManager.getResource(dataSource) as ConnectionHolder).connection
        val transactionImpl = SpringTransaction(connection, db, defaultIsolationLevel, currentOrNull())
        TransactionManager.resetCurrent(this)
        return Transaction(transactionImpl).apply {
            TransactionSynchronizationManager.bindResource(this@SpringTransactionManager, this)
        }
    }

    override fun currentOrNull(): Transaction? = TransactionSynchronizationManager.getResource(this) as Transaction?

    private class SpringTransaction(override val connection: Connection, override val db: Database, override val transactionIsolation: Int, override val outerTransaction: Transaction?) : TransactionInterface {

        override fun commit() {
            connection.run {
                if (!autoCommit) {
                    commit()
                }
            }
        }

        override fun rollback() {
            connection.rollback()
        }

        override fun close() { }

    }

}

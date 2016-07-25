package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Connection
import javax.sql.DataSource


class SpringTransactionManager(dataSource: DataSource) : DataSourceTransactionManager(dataSource), TransactionManager {

    private val db = Database.connect(dataSource, { this } )

    override fun doBegin(transaction: Any?, definition: TransactionDefinition?) {
        super.doBegin(transaction, definition)

        if (TransactionSynchronizationManager.hasResource(dataSource)) {
            currentOrNull() ?: initTransaction()
        }
    }

    override fun doCleanupAfterCompletion(transaction: Any) {
        super.doCleanupAfterCompletion(transaction)
        if (!TransactionSynchronizationManager.hasResource(dataSource)) {
            TransactionSynchronizationManager.unbindResourceIfPossible(this)
        }
        TransactionManager.currentThreadManager.remove()
    }

    override fun doSuspend(transaction: Any?): Any? {
        TransactionSynchronizationManager.unbindResourceIfPossible(this)
        return super.doSuspend(transaction)
    }

    override fun newTransaction(isolation: Int): Transaction {
        val tDefinition = if (dataSource.connection.transactionIsolation != isolation) {
                DefaultTransactionDefinition().apply { isolationLevel = isolation }
            } else null

        getTransaction(tDefinition)

        return initTransaction()
    }

    private fun initTransaction(): Transaction {
        val connection = (TransactionSynchronizationManager.getResource(dataSource) as ConnectionHolder).connection
        val transactionImpl = SpringTransaction(connection, db, currentOrNull())
        return Transaction(transactionImpl).apply {
            TransactionSynchronizationManager.bindResource(this@SpringTransactionManager, this)
        }
    }

    override fun currentOrNull(): Transaction? = TransactionSynchronizationManager.getResource(this) as Transaction?

    private class SpringTransaction(override val connection: Connection, override val db: Database, override val outerTransaction: Transaction?) : TransactionInterface {

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

package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.TransactionAbstraction
import org.jetbrains.exposed.sql.TransactionProvider
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Connection
import javax.sql.DataSource


class ExposedTransactionManager(dataSource: DataSource) : DataSourceTransactionManager(dataSource), TransactionProvider {

    private val db = Database.connect(dataSource, { this } )

    override fun newTransaction(isolation: Int): Transaction {
        val tDefinition = if (dataSource.connection.transactionIsolation != isolation) {
            DefaultTransactionDefinition().apply { isolationLevel = isolation }
        } else null
        val tObject = (getTransaction(tDefinition) as DefaultTransactionStatus).transaction as JdbcTransactionObjectSupport
        return Transaction(SpringTransaction(tObject, db)).apply {
            TransactionSynchronizationManager.bindResource(this@ExposedTransactionManager, this)
        }
    }

    override fun close() {
        TransactionSynchronizationManager.unbindResourceIfPossible(this)
    }

    override fun currentOrNull(): Transaction? {
        return TransactionSynchronizationManager.getResource(this) as Transaction? ?: newTransaction(TransactionDefinition.ISOLATION_DEFAULT)
    }

    private class SpringTransaction(private val tObject: JdbcTransactionObjectSupport, override val db: Database) : TransactionAbstraction {

        override val connection: Connection get() = tObject.connectionHolder.connection

        override fun commit() {
            tObject.connectionHolder.connection.run {
                if (!autoCommit) {
                    commit()
                }
            }
        }

        override fun rollback() {
            tObject.connectionHolder.connection.rollback()
        }

        override fun close() {
            tObject.connectionHolder.connection.close()
        }

    }

}

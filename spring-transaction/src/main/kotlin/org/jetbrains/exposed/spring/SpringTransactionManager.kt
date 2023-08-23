package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import javax.sql.DataSource

class SpringTransactionManager(
    private val dataSource: DataSource,
    databaseConfig: DatabaseConfig,
) : AbstractPlatformTransactionManager() {

    private var _transactionManager: TransactionManager?

    @Suppress("TooGenericExceptionCaught")
    private val threadLocalTransactionManager: TransactionManager
        get() = _transactionManager ?: throw Error()

    init {
        Database.connect(
            datasource = dataSource,
            databaseConfig = databaseConfig
        ).apply {
            _transactionManager = this.transactionManager
        }
    }

    override fun doGetTransaction(): Any {
        val outer = threadLocalTransactionManager.currentOrNull()

        return ExposedTransactionObject(
            manager = threadLocalTransactionManager,
            outerTransaction = outer
        )
    }

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        val trxObject = transaction as ExposedTransactionObject

        val currentTransactionManager = trxObject.manager
        TransactionManager.resetCurrent(currentTransactionManager)

        currentTransactionManager.currentOrNull()
            ?: currentTransactionManager.newTransaction(
                isolation = definition.isolationLevel,
                readOnly = definition.isReadOnly,
            )
    }

    override fun doCommit(status: DefaultTransactionStatus) {
        val trxObject = status.transaction as ExposedTransactionObject
        TransactionManager.resetCurrent(trxObject.manager)
        trxObject.commit()
    }

    override fun doRollback(status: DefaultTransactionStatus) {
        val trxObject = status.transaction as ExposedTransactionObject
        TransactionManager.resetCurrent(trxObject.manager)
        trxObject.rollback()
    }

    override fun doCleanupAfterCompletion(transaction: Any) {
        val trxObject = transaction as ExposedTransactionObject
        trxObject.setCurrentToOuter()
        TransactionManager.resetCurrent(null)
    }

    private data class ExposedTransactionObject(
        val manager: TransactionManager,
        private val outerTransaction: Transaction?,
    ) {

        fun setCurrentToOuter() {
            manager.bindTransactionToThread(outerTransaction)
        }

        private fun hasOuterTransaction(): Boolean {
            return outerTransaction != null
        }

        @Suppress("TooGenericExceptionCaught")
        fun commit() {
            try {
                if (hasOuterTransaction().not()) {
                    manager.currentOrNull()?.commit()
                }
            } catch (e: Exception) {
                throw TransactionSystemException(e.message.orEmpty(), e)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun rollback() {
            try {
                if (hasOuterTransaction().not()) {
                    manager.currentOrNull()?.rollback()
                }
            } catch (e: Exception) {
                throw TransactionSystemException(e.message.orEmpty(), e)
            }
        }
    }
}

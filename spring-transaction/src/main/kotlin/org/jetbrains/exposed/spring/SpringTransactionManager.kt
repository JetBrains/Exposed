package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.SmartTransactionObject
import javax.sql.DataSource

class SpringTransactionManager(
    dataSource: DataSource,
    databaseConfig: DatabaseConfig = DatabaseConfig {},
    private val showSql: Boolean = false,
) : AbstractPlatformTransactionManager() {

    private var _database: Database

    private var _transactionManager: TransactionManager

    private val threadLocalTransactionManager: TransactionManager
        get() = _transactionManager

    init {
        _database = Database.connect(
            datasource = dataSource, databaseConfig = databaseConfig
        ).apply {
            _transactionManager = this.transactionManager
        }

        isNestedTransactionAllowed = databaseConfig.useNestedTransactions
    }

    /**
     * ExposedConnection implements savepoint by itself
     * `useSavepointForNestedTransaction` is use `SavepointManager` for nested transaction
     *
     * So we don't need to use java savepoint for nested transaction
     */
    override fun useSavepointForNestedTransaction() = false

    override fun doGetTransaction(): Any {
        val outerManager = TransactionManager.manager
        val outer = threadLocalTransactionManager.currentOrNull()

        return ExposedTransactionObject(
            manager = threadLocalTransactionManager,
            outerManager = outerManager,
            outerTransaction = outer,
        )
    }

    override fun doSuspend(transaction: Any): Any {
        val trxObject = transaction as ExposedTransactionObject
        val currentManager = trxObject.manager

        return (currentManager.currentOrNull() as Any).apply {
            currentManager.bindTransactionToThread(null)
        }
    }

    override fun doResume(transaction: Any?, suspendedResources: Any) {
        threadLocalTransactionManager.bindTransactionToThread(suspendedResources as Transaction?)
    }

    override fun isExistingTransaction(transaction: Any): Boolean {
        val trxObject = transaction as ExposedTransactionObject
        return trxObject.getCurrentTransaction() != null
    }

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        val trxObject = transaction as ExposedTransactionObject

        val currentTransactionManager = trxObject.manager
        TransactionManager.resetCurrent(currentTransactionManager)

        currentTransactionManager.newTransaction(
            isolation = definition.isolationLevel, readOnly = definition.isReadOnly, outerTransaction = currentTransactionManager.currentOrNull()
        ).apply {
            if (showSql) {
                addLogger(StdOutSqlLogger)
            }
        }
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

        trxObject.cleanUpTransactionIfIsPossible {
            closeStatementsAndConnections(it)
        }

        trxObject.setCurrentToOuter()
    }

    private fun closeStatementsAndConnections(transaction: Transaction) {
        val currentStatement = transaction.currentStatement
        @Suppress("TooGenericExceptionCaught")
        try {
            currentStatement?.let {
                it.closeIfPossible()
                transaction.currentStatement = null
            }
            transaction.closeExecutedStatements()
        } catch (error: Exception) {
            exposedLogger.warn("Statements close failed", error)
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            transaction.close()
        } catch (error: Exception) {
            exposedLogger.warn("Transaction close failed: ${error.message}. Statement: $currentStatement", error)
        }
    }

    override fun doSetRollbackOnly(status: DefaultTransactionStatus) {
        val trxObject = status.transaction as ExposedTransactionObject
        trxObject.setRollbackOnly()
    }

    private data class ExposedTransactionObject(
        val manager: TransactionManager,
        val outerManager: TransactionManager,
        private val outerTransaction: Transaction?,
    ) : SmartTransactionObject {

        private var isRollback: Boolean = false

        fun cleanUpTransactionIfIsPossible(block: (transaction: Transaction) -> Unit) {
            val currentTransaction = getCurrentTransaction()
            if (currentTransaction != null) {
                block(currentTransaction)
            }
        }

        fun setCurrentToOuter() {
            manager.bindTransactionToThread(outerTransaction)
            TransactionManager.resetCurrent(outerManager)
        }

        @Suppress("TooGenericExceptionCaught")
        fun commit() {
            try {
                manager.currentOrNull()?.commit()
            } catch (error: Exception) {
                throw TransactionSystemException(error.message.orEmpty(), error)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun rollback() {
            try {
                manager.currentOrNull()?.rollback()
            } catch (error: Exception) {
                throw TransactionSystemException(error.message.orEmpty(), error)
            }
        }

        fun getCurrentTransaction(): Transaction? = manager.currentOrNull()

        /**
         * implementation of @SmartTransactionObject
         */
        fun setRollbackOnly() {
            isRollback = true
        }

        override fun isRollbackOnly() = isRollback

        override fun flush() {
            // Do noting
        }
    }
}

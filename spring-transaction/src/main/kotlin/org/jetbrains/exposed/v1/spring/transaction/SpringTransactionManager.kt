package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import org.springframework.jdbc.datasource.ConnectionHandle
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.SmartTransactionObject
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Connection
import javax.sql.DataSource

/**
 * Transaction Manager implementation that builds on top of Spring's standard transaction workflow.
 *
 * @param dataSource The data source that produces `Connection` objects.
 * @param databaseConfig The configuration that defines custom properties to be used with connections.
 * If none is specified, the default configuration values will be used.
 * @property showSql Whether transaction queries should be logged. Defaults to `false`.
 * @sample org.jetbrains.exposed.spring.TestConfig
 */
class SpringTransactionManager(
    private val dataSource: DataSource,
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
        ).apply {
            // hold on to existing Spring JDBC connection holders
            connectionHolder = TransactionSynchronizationManager.getResource(dataSource) as? ConnectionHolder
        }
    }

    override fun doSuspend(transaction: Any): Any {
        val trxObject = transaction as ExposedTransactionObject
        val currentManager = trxObject.manager

        return SuspendedObject(
            transaction = currentManager.currentOrNull() as JdbcTransaction,
            manager = currentManager,
            // unbind Spring JDBC connection reference
            connectionHolder = TransactionSynchronizationManager.unbindResource(dataSource) as ConnectionHolder,
        ).apply {
            currentManager.bindTransactionToThread(null)
            TransactionManager.resetCurrent(null)
            trxObject.connectionHolder = null
        }
    }

    override fun doResume(transaction: Any?, suspendedResources: Any) {
        val suspendedObject = suspendedResources as SuspendedObject

        // resume exposed transaction
        TransactionManager.resetCurrent(suspendedObject.manager)
        threadLocalTransactionManager.bindTransactionToThread(suspendedObject.transaction)
        // resume Spring JDBC transaction
        TransactionSynchronizationManager.bindResource(dataSource, suspendedObject.connectionHolder)
    }

    private data class SuspendedObject(
        val transaction: JdbcTransaction,
        val manager: TransactionManager,
        val connectionHolder: ConnectionHolder,
    )

    override fun isExistingTransaction(transaction: Any): Boolean {
        val trxObject = transaction as ExposedTransactionObject
        return trxObject.getCurrentTransaction() != null
    }

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        val trxObject = transaction as ExposedTransactionObject

        // Exposed transaction
        val currentTransactionManager = trxObject.manager
        TransactionManager.resetCurrent(threadLocalTransactionManager)

        val transaction = currentTransactionManager.newTransaction(
            isolation = definition.isolationLevel,
            readOnly = definition.isReadOnly,
            outerTransaction = currentTransactionManager.currentOrNull()
        ).apply {
            if (definition.timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                queryTimeout = definition.timeout
            }

            if (showSql) {
                addLogger(StdOutSqlLogger)
            }
        }

        // Spring JDBC transaction
        @Suppress("TooGenericExceptionCaught")
        try {
            if (trxObject.connectionHolder == null) {
                trxObject.connectionHolder = ConnectionHolder(ExposedConnectionHandle(transaction))
                trxObject.isNewConnectionHolder = true
            }

            trxObject.connectionHolder?.isSynchronizedWithTransaction = true

            // Bind the connection holder to the thread.
            if (trxObject.isNewConnectionHolder) {
                TransactionSynchronizationManager.bindResource(dataSource, trxObject.connectionHolder!!)
            }
        } catch (ex: Throwable) {
            trxObject.connectionHolder = null
            throw CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex)
        }
    }

    override fun doCommit(status: DefaultTransactionStatus) {
        val trxObject = status.transaction as ExposedTransactionObject
        TransactionManager.resetCurrent(trxObject.manager)
        trxObject.commit()
        // Spring JDBC implicitly committed since they share connection
    }

    override fun doRollback(status: DefaultTransactionStatus) {
        val trxObject = status.transaction as ExposedTransactionObject
        TransactionManager.resetCurrent(trxObject.manager)
        trxObject.rollback()
        // Spring JDBC implicitly rolled back since they share connection
    }

    override fun doCleanupAfterCompletion(transaction: Any) {
        val trxObject = transaction as ExposedTransactionObject

        // Clean up Exposed
        trxObject.cleanUpTransactionIfIsPossible {
            closeStatementsAndConnections(it)
        }
        trxObject.setCurrentToOuter()

        // Clean up Spring JDBC
        if (trxObject.isNewConnectionHolder) {
            TransactionSynchronizationManager.unbindResource(dataSource)
            trxObject.connectionHolder?.released()
        }
        trxObject.connectionHolder?.clear()
    }

    private fun closeStatementsAndConnections(transaction: JdbcTransaction) {
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
        private val outerTransaction: JdbcTransaction?,
    ) : SmartTransactionObject {

        private var isRollback: Boolean = false
        var isNewConnectionHolder: Boolean = false
        var connectionHolder: ConnectionHolder? = null

        fun cleanUpTransactionIfIsPossible(block: (transaction: JdbcTransaction) -> Unit) {
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

        fun getCurrentTransaction(): JdbcTransaction? = manager.currentOrNull()

        fun setRollbackOnly() {
            isRollback = true
        }

        override fun isRollbackOnly() = isRollback

        override fun flush() {
            // Do nothing
        }
    }

    /**
     * This can be inserted into a Spring JDBC [ConnectionHolder], which makes Spring JDBC see the same
     * connection as is currently held and managed by the exposed [Transaction].
     *
     * When installed using [TransactionSynchronizationManager.bindResource], Spring JDBC constructs like
     * JdbcTemplate and JdbcClient will see the same connection as Exposed and partake
     * in the same transaction with the same underlying autocommit-disabled connection.
     */
    private class ExposedConnectionHandle(
        val transaction: JdbcTransaction
    ) : ConnectionHandle {
        override fun getConnection(): Connection {
            return transaction.connection.connection as Connection
        }
    }
}

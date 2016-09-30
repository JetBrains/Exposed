package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import java.sql.Connection
import java.sql.SQLException

class ThreadLocalTransactionManager(private val db: Database) : TransactionManager {

    val threadLocal = ThreadLocal<Transaction>()

    override fun newTransaction(isolation: Int): Transaction = Transaction(ThreadLocalTransaction(db, isolation, threadLocal)).apply {
        threadLocal.set(this)
    }

    override fun currentOrNull(): Transaction? = threadLocal.get()

    private class ThreadLocalTransaction(override val db: Database, isolation: Int, val threadLocal: ThreadLocal<Transaction>) : TransactionInterface {

        override val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
            db.connector().apply {
                autoCommit = false
                transactionIsolation = isolation
            }
        }

        override val outerTransaction = threadLocal.get()

        override fun commit() {
            connection.commit()
        }

        override fun rollback() {
            if (!connection.isClosed) {
                connection.rollback()
            }
        }

        override fun close() {
            connection.close()
            threadLocal.set(outerTransaction)
        }

    }
}

fun <T> transaction(statement: Transaction.() -> T): T = transaction(Connection.TRANSACTION_REPEATABLE_READ, 3, statement)

fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, statement: Transaction.() -> T): T {
    val outer = TransactionManager.currentOrNull()

    return if (outer != null) {
        outer.statement()
    }
    else {
        inTopLevelTransaction(transactionIsolation, repetitionAttempts, statement)
    }
}

fun <T> inTopLevelTransaction(transactionIsolation: Int, repetitionAttempts: Int, statement: Transaction.() -> T): T {
    var repetitions = 0

    while (true) {

        val transaction = TransactionManager.currentThreadManager.get().newTransaction(transactionIsolation)

        try {
            val answer = transaction.statement()
            transaction.commit()
            return answer
        }
        catch (e: SQLException) {
            val currentStatement = transaction.currentStatement
            exposedLogger.info("Transaction attempt #$repetitions: ${e.message}. Statement: $currentStatement", e)
            transaction.rollback()
            repetitions++
            if (repetitions >= repetitionAttempts) {
                throw e
            }
        }
        catch (e: Throwable) {
            transaction.rollback()
            throw e
        }
        finally {
            transaction.currentStatement = null
            transaction.lastExecutedStatement = null
            transaction.close()
            TransactionManager.currentThreadManager.remove()
        }
    }
}
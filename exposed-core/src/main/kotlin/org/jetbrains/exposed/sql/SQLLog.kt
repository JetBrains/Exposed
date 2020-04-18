package org.jetbrains.exposed.sql
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.ITransaction
import org.jetbrains.exposed.sql.transactions.ITransactionManager
import org.slf4j.LoggerFactory
import java.util.*

interface SqlLogger {
    fun log (context: StatementContext, transaction: ITransaction)
}

val exposedLogger = LoggerFactory.getLogger("Exposed")!!

inline fun <R> logTimeSpent(message: String, block: ()->R) : R {
    val start = System.currentTimeMillis()
    val answer = block()
    exposedLogger.info(message + " took " + (System.currentTimeMillis() - start) + "ms")
    return answer
}

object StdOutSqlLogger : SqlLogger {
    override fun log (context: StatementContext, transaction: ITransaction) {
        System.out.println("SQL: ${context.expandArgs(transaction)}")
    }
}

object Slf4jSqlDebugLogger : SqlLogger {
    override fun log (context: StatementContext, transaction: ITransaction) {
        if (exposedLogger.isDebugEnabled) {
            exposedLogger.debug(context.expandArgs(ITransactionManager.current()))
        }
    }
}

class CompositeSqlLogger : SqlLogger, StatementInterceptor {
    private val loggers: ArrayList<SqlLogger> = ArrayList(2)

    fun addLogger (logger: SqlLogger) {
        loggers.add(logger)
    }

    fun removeLogger (logger: SqlLogger) {
        loggers.remove(logger)
    }

    override fun log (context: StatementContext, transaction: ITransaction) {
        for (logger in loggers) {
            logger.log(context, transaction)
        }
    }

    override fun afterExecution(transaction: ITransaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {
        contexts.forEach {
            log(it, transaction)
        }
    }
}

fun ITransaction.addLogger(vararg logger: SqlLogger) : CompositeSqlLogger {
    return CompositeSqlLogger().apply {
        logger.forEach { this.addLogger(it) }
        registerInterceptor(this)
    }
}
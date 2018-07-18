package org.jetbrains.exposed.sql
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.util.*

interface SqlLogger {
    fun log (context: StatementContext, transaction: Transaction)
}

val exposedLogger = LoggerFactory.getLogger("Exposed")!!

inline fun <R> logTimeSpent(message: String, block: ()->R) : R {
    val start = System.currentTimeMillis()
    val answer = block()
    exposedLogger.info(message + " took " + (System.currentTimeMillis() - start) + "ms")
    return answer
}

object StdOutSqlLogger : SqlLogger {
    override fun log (context: StatementContext, transaction: Transaction) {
        System.out.println("SQL: ${context.expandArgs(transaction)}")
    }
}

object Slf4jSqlDebugLogger : SqlLogger {
    override fun log (context: StatementContext, transaction: Transaction) {
        if (exposedLogger.isDebugEnabled) {
            exposedLogger.debug(context.expandArgs(TransactionManager.current()))
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

    override fun log (context: StatementContext, transaction: Transaction) {
        for (logger in loggers) {
            logger.log(context, transaction)
        }
    }

    override fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatement) {
        contexts.forEach {
            log(it, transaction)
        }
    }
}

fun Transaction.addLogger(vararg logger: SqlLogger) : CompositeSqlLogger {
    return CompositeSqlLogger().apply {
        logger.forEach { this.addLogger(it) }
        registerInterceptor(this)
    }
}
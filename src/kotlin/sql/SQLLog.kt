package kotlin.sql
import org.apache.log4j.Logger
import java.sql.PreparedStatement
import java.util.ArrayList
import java.util.Collections
import java.util.Stack

public trait SqlLogger {
    fun log (stmt: String, args: List<Pair<ColumnType, Any?>> = ArrayList<Pair<ColumnType, Any?>>());
}

fun expandArgs (sql: String, args: List<Pair<ColumnType, Any?>>) : String {
    if (args.size == 0)
        return sql

    val result = StringBuilder()
    val quoteStack = Stack<Char>()
    var argIndex = 0
    var lastPos = 0
    for (i in 0..sql.length-1) {
        val char = sql[i]
        if (char == '?') {
            if (quoteStack.empty) {
                result.append(sql.substring(lastPos, i))
                lastPos = i+1
                result.append(args[argIndex].first.valueToString(args[argIndex].second))
                ++argIndex
            }
            continue
        }

        if (char == '\'' || char == '\"') {
            if (quoteStack.empty) {
                quoteStack.push(char)
            } else {
                val currentQuote = quoteStack.peek()
                if (currentQuote == char)
                    quoteStack.pop()
                else
                    quoteStack.push(char)
            }
        }
    }

    if (lastPos < sql.length)
        result.append(sql.substring(lastPos))

    return result.toString()
}

public class StdOutSqlLogger : SqlLogger {
    override fun log(stmt: String, args: List<Pair<ColumnType, Any?>>) {
        System.out.println(stmt)
    }
}

public class Log4jSqlLogger(loggerName: String = "Exposed"): SqlLogger {
    private val logger = Logger.getLogger("Exposed")
    override fun log(stmt: String, args: List<Pair<ColumnType, Any?>>) {
        logger?.debug(stmt)
    }
}

public class CompositeSqlLogger(val session: Session) : SqlLogger {
    private val loggers: ArrayList<SqlLogger> = ArrayList<SqlLogger>()

    public fun addLogger (logger: SqlLogger) {
        loggers.add(logger)
    }

    public fun removeLogger (logger: SqlLogger) {
        loggers.remove(logger)
    }

    override fun log(stmt: String, args: List<Pair<ColumnType, Any?>>) {
        session.statementCount++
        for (logger in loggers) {
            logger.log(stmt, args)
        }
    }
}

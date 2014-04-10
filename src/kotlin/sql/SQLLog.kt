package kotlin.sql
import org.apache.log4j.Logger
import java.sql.PreparedStatement
import java.util.ArrayList
import java.util.Collections

public trait SqlLogger {
    fun log (stmt: String);
}

public fun SqlLogger.log (stmt: StringBuilder) {
    log (stmt.toString())
}

public fun SqlLogger.log (stmt: PreparedStatement) {
//    stmt.setObject()
    log (stmt.toString()!!)
}

public class StdOutSqlLogger : SqlLogger {
    override fun log(stmt: String) {
        System.out.println(stmt)
    }
}

public class Log4jSqlLogger(loggerName: String = "Exposed"): SqlLogger {
    private val logger = Logger.getLogger("Exposed")
    override fun log(stmt: String) {
        logger?.debug(stmt)
    }
}

public class CompositeSqlLogger : SqlLogger {
    private val loggers: ArrayList<SqlLogger> = ArrayList<SqlLogger>()

    public fun addLogger (logger: SqlLogger) {
        loggers.add(logger)
    }

    public fun removeLogger (logger: SqlLogger) {
        loggers.remove(logger)
    }

    override fun log(stmt: String) {
        for (logger in loggers) {
            logger.log(stmt)
        }
    }
}
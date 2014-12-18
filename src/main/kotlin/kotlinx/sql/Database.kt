package kotlinx.sql

import java.sql.DriverManager
import java.sql.Connection
import kotlin.properties.Delegates
import javax.sql.DataSource
import org.joda.time.DateTimeZone
import kotlinx.dao.EntityCache
import java.sql.SQLException

public class Database private(val connector: () -> Connection) {

    val url: String by Delegates.lazy {
        val connection = connector()
        try {
            connection.getMetaData()!!.getURL()!!
        }
        finally {
            connection.close()
        }
    }

    // Overloading methods instead of default parameters for Java conpatibility
    fun <T> withSession(statement: Session.() -> T): T = withSession(Connection.TRANSACTION_REPEATABLE_READ, 3, statement)

    fun <T> withSession(transactionIsolation: Int, repetitionAttempts: Int, statement: Session.() -> T): T {
        val outer = Session.tryGet()

        return if (outer != null) {
            outer.statement()
        }
        else {
            inNewTransaction(transactionIsolation, repetitionAttempts, statement)
        }
    }

    fun <T> inNewTransaction(transactionIsolation: Int, repetitionAttempts: Int, statement: Session.() -> T): T {
        var repetitions = 0

        while (true) {

            val session = Session(this, {
                val connection = connector()
                connection.setAutoCommit(false)
                connection.setTransactionIsolation(transactionIsolation)
                connection
            })

            try {
                val answer = session.statement()
                session.commit()
                return answer
            }
            catch (e: SQLException) {
                exposedLogger.info("Session retpetition=$repetitions: ${e.getMessage()}")
                session.rollback()
                repetitions++
                if (repetitions >= repetitionAttempts) {
                    throw e
                }
            }
            catch (e: Throwable) {
                session.rollback()
                throw e
            }
            finally {
                session.close()
            }
        }
    }

    public class object {
        public val timeZone: DateTimeZone = DateTimeZone.UTC

        public fun connect(datasource: DataSource): Database {
            return Database {
                datasource.getConnection()!!
            }
        }

        public fun connect(url: String, driver: String, user: String = "", password: String = ""): Database {
            Class.forName(driver).newInstance()

            return Database {
                DriverManager.getConnection(url, user, password)
            }
        }
    }
}

val Database.name : String get() {
    return url.let {
        val query = it.substring(it.lastIndexOf("/")+1)
        val params = query.indexOf('?')
        if (params > 0) {
            query.substring(0, params)
        }
        else {
            query
        }
    }
}

enum class DatabaseVendor {
        MySql Oracle SQLServer PostgreSQL H2
}

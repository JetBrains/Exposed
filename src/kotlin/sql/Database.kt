package kotlin.sql

import java.sql.DriverManager
import java.sql.Connection
import kotlin.properties.Delegates
import javax.sql.DataSource
import org.joda.time.DateTimeZone

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

    fun <T> withSession(statement: Session.() -> T): T {
        val connection = connector()
        val session = Session(connection)
        try {
            Session.threadLocal.set(session)
            connection.setAutoCommit(false)
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ)

            val answer = session.statement()
            connection.commit()
            return answer
        }
        catch (e: Throwable) {
            connection.rollback()
            throw e
        }
        finally {
            connection.close()
            Session.threadLocal.set(null)
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
            val connection = DriverManager.getConnection(url, user, password)

            return Database {
                EternalConnection(connection)
            }
        }
    }
}

class EternalConnection(val delegate: Connection) : Connection by delegate {
    override fun close() {
        // Do nothing
    }
}

enum class DatabaseVendor {
        MySql Oracle SQLServer PostgreSQL H2
}

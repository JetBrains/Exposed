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
            session.close()
            connection.close()
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
    return url.let { it.substring(it.lastIndexOf("/")+1)}
}

/*
fun Database.connectOther (otherName: String): Database {
    if (otherName == name) return this
    val otherUrl = url.let { it.substring(0, it.length - name.length) } + otherName
    val connection = connector()
    try {
        val otherDriver = connection.getMetaData()!!.getDriverName()!!
        val otherUser = connection.getMetaData()!!.getUserName() ?: ""
        val otherPassword = connection.getClientInfo()!!["password"] as? String ?: ""
        return Database.connect(otherUrl, otherDriver, otherUser, otherPassword)
    }
    finally {
        connection.close()
    }
}
*/

enum class DatabaseVendor {
        MySql Oracle SQLServer PostgreSQL H2
}

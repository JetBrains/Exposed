package kotlin.sql

import java.sql.DriverManager
import java.sql.Driver
import java.sql.Connection
import kotlin.properties.Delegates

class Database(val url: String, driver: String, var user: String = "", val password: String = "") {
    val driver: Driver = Class.forName(driver).newInstance() as Driver

    var _connection: Connection? = null

    val connection: Connection get() {
        return synchronized(this) {
            if (_connection?.isClosed() ?: false) {
                _connection = null
            }

            if (_connection == null) {
                val c = if (user != "") DriverManager.getConnection(url, user, password) else DriverManager.getConnection(url)
                c.setAutoCommit(false)
                _connection = c
            }
            _connection!!
        }
    }

    fun shutDown() {
        _connection?.close()
    }

    fun <T> withSession(statement: Session.() -> T): T {
        val session = Session(connection, driver)
        try {
            Session.threadLocal.set(session)
            val answer = session.statement()
            connection.commit()
            return answer
        }
        finally {
            Session.threadLocal.set(null)
        }
    }
}

package kotlin.sql

import java.sql.DriverManager
import java.sql.Driver
import java.sql.Connection
import kotlin.properties.Delegates

class Database(val url: String, driver: String, var user: String = "", val password: String = "") {
    val driver: Driver = Class.forName(driver).newInstance() as Driver
    val connection: Connection by Delegates.blockingLazy {
        val c = if (user != "") DriverManager.getConnection(url, user, password) else DriverManager.getConnection(url)
        c.setAutoCommit(false)
        c
    }

    fun shutDown() {
        connection.close()
    }

    fun <T> withSession(statement: Session.() -> T): T {
        val session = Session(connection, driver)
        Session.threadLocal.set(session)
        val answer = session.statement()
        connection.commit()
        Session.threadLocal.set(null)
        return answer
    }
}

package kotlin.sql

import java.sql.DriverManager
import java.sql.Driver

class Database(val url: String, driver: String, var user: String = "", val password: String = "") {
    val driver: Driver = Class.forName(driver).newInstance() as Driver

    fun withSession(statement: Session.() -> Unit) {
        val connection = if (user != "") DriverManager.getConnection(url, user, password) else DriverManager.getConnection(url)
        connection.setAutoCommit(false)
        val session = Session(connection, driver)
        Session.threadLocal.set(session)
        session.statement()
        connection.commit()
        Session.threadLocal.set(null)
        connection.close()
    }
}

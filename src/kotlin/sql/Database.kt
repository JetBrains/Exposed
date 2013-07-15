package kotlin.sql

import java.sql.DriverManager

class Database(val url: String, val driver: String) {
    {
        Class.forName(driver)
    }

    fun withSession(statement: Session.() -> Unit) {
        val connection = DriverManager.getConnection(url)
        connection.setAutoCommit(false)
        Session(connection).statement()
        connection.commit()
        connection.close()
    }
}
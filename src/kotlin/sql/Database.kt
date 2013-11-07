package kotlin.sql

import java.sql.DriverManager
import java.sql.Driver
import java.sql.Connection
import kotlin.properties.Delegates
import javax.sql.DataSource
import com.mchange.v2.c3p0.ComboPooledDataSource

class Database(val url: String, val driver: String, val user: String = "", val password: String = "") {
    val datasource: ComboPooledDataSource by Delegates.blockingLazy {
        val cpds = ComboPooledDataSource()
        cpds.setDriverClass(driver)
        cpds.setJdbcUrl(url)
        cpds.setMaxStatements(180)
        cpds.setMaxIdleTime(200)
        cpds.setIdleConnectionTestPeriod(300)
        if (user != "") {
            cpds.setUser(user);
            cpds.setPassword(password);
        }

        cpds
    }

    fun shutDown() {
        datasource.close()
    }

    fun <T> withSession(statement: Session.() -> T): T {
        val connection = datasource.getConnection()!!
        val session = Session(connection, driver)
        try {
            Session.threadLocal.set(session)
            val answer = session.statement()
            connection.close()
            return answer
        }
        finally {
            Session.threadLocal.set(null)
        }
    }
}

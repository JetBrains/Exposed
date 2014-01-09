package kotlin.sql

import java.sql.DriverManager
import java.sql.Driver
import java.sql.Connection
import kotlin.properties.Delegates
import javax.sql.DataSource
import com.mchange.v2.c3p0.ComboPooledDataSource
import org.joda.time.DateTimeZone

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

    val vendor: DatabaseVendor by Delegates.lazy {
        when(driver) {
            "com.mysql.jdbc.Driver" -> DatabaseVendor.MySql
            "oracle.jdbc.driver.OracleDriver" -> DatabaseVendor.Oracle
            "com.microsoft.sqlserver.jdbc.SQLServerDriver" -> DatabaseVendor.SQLServer
            "org.postgresql.Driver" -> DatabaseVendor.PostgreSQL
            "org.h2.Driver" -> DatabaseVendor.H2
            else -> error("Unknown DB driver $driver")
        }
    }

    fun <T> withSession(statement: Session.() -> T): T {
        return withSession(datasource, vendor, statement)
    }

    class object {
        public val timeZone: DateTimeZone = DateTimeZone.UTC
        public fun <T> withSession(datasource: DataSource, vendor: DatabaseVendor, statement: Session.() -> T): T {
            val connection = datasource.getConnection()!!
            val session = Session(connection, vendor)
            try {
                Session.threadLocal.set(session)
                if (vendor == DatabaseVendor.MySql) {
                    val stmt = connection.createStatement()!!
                    val timeZoneString = if (timeZone == DateTimeZone.UTC) "+0:00" else timeZone.toString()
                    stmt.execute("SET TIME_ZONE = '$timeZoneString'")
                }

                val answer = session.statement()
                connection.close()
                return answer
            }
            finally {
                Session.threadLocal.set(null)
            }
        }
    }
}

enum class DatabaseVendor {
        MySql Oracle SQLServer PostgreSQL H2
}

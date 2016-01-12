package org.jetbrains.exposed.sql

import org.h2.jdbc.JdbcConnection
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.SQLException
import javax.sql.DataSource

class Database private constructor(val connector: () -> Connection) {

    val metadata: DatabaseMetaData get() = Transaction.currentOrNull()?.connection?.metaData ?: with(connector()) {
        try {
            metaData
        }
        finally {
            close()
        }
    }

    val url: String by lazy { metadata.url }

    val vendor: DatabaseVendor by lazy {
        val url = url
        when {
            url.startsWith("jdbc:mysql") -> DatabaseVendor.MySql
            url.startsWith("jdbc:oracle") -> DatabaseVendor.Oracle
            url.startsWith("jdbc:sqlserver") -> DatabaseVendor.SQLServer
            url.startsWith("jdbc:postgresql") -> DatabaseVendor.PostgreSQL
            url.startsWith("jdbc:h2") -> DatabaseVendor.H2
            else -> error("Unknown database type $url")
        }
    }

    fun vendorSupportsForUpdate(): Boolean {
        return vendor != DatabaseVendor.H2
    }

    fun vendorCompatibleWith(): DatabaseVendor {
        if (vendor == DatabaseVendor.H2) {
            return ((Transaction.current().connection as? JdbcConnection)?.session as? org.h2.engine.Session)?.database?.mode?.let { mode ->
                DatabaseVendor.values().singleOrNull { it.name.equals(mode.name, true) }
            } ?: vendor
        }

        return vendor
    }


    // Overloading methods instead of default parameters for Java compatibility
    fun <T> transaction(statement: Transaction.() -> T): T = transaction(Connection.TRANSACTION_REPEATABLE_READ, 3, statement)

    fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, statement: Transaction.() -> T): T {
        val outer = Transaction.currentOrNull()

        return if (outer != null) {
            outer.statement()
        }
        else {
            inTopLevelTransaction(transactionIsolation, repetitionAttempts, statement)
        }
    }

    fun <T> inTopLevelTransaction(transactionIsolation: Int, repetitionAttempts: Int, statement: Transaction.() -> T): T {
        var repetitions = 0

        while (true) {

            val transaction = Transaction(this, {
                val connection = connector()
                connection.autoCommit = false
                connection.transactionIsolation = transactionIsolation
                connection
            })

            try {
                val answer = transaction.statement()
                transaction.commit()
                return answer
            }
            catch (e: SQLException) {
                exposedLogger.info("Transaction attempt #$repetitions: ${e.message}", e)
                transaction.rollback()
                repetitions++
                if (repetitions >= repetitionAttempts) {
                    throw e
                }
            }
            catch (e: Throwable) {
                transaction.rollback()
                throw e
            }
            finally {
                transaction.close()
            }
        }
    }

    companion object {
        fun connect(datasource: DataSource): Database {
            return Database {
                datasource.connection!!
            }
        }

        fun connect(url: String, driver: String, user: String = "", password: String = ""): Database {
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
        MySql, Oracle, SQLServer, PostgreSQL, H2
}

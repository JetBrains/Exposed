package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.*
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.CopyOnWriteArrayList
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

    internal val dialect by lazy {
        val name = url.removePrefix("jdbc:").substringBefore(':')
        dialects.firstOrNull {name == it.name} ?: error("No dialect registered for $name. URL=$url")
    }

    val vendor: String get() = dialect.name


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
        private val dialects = CopyOnWriteArrayList<DatabaseDialect>()

        init {
            registerDialect(H2Dialect)
            registerDialect(MysqlDialect)
        }

        fun registerDialect(dialect: DatabaseDialect) {
            dialects.add(0, dialect)
        }

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


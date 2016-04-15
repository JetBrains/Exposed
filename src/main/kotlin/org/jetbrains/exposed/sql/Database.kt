package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
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


    companion object {
        private val dialects = CopyOnWriteArrayList<DatabaseDialect>()

        init {
            registerDialect(H2Dialect)
            registerDialect(MysqlDialect)
            registerDialect(PostgreSQLDialect)
        }

        fun registerDialect(dialect: DatabaseDialect) {
            dialects.add(0, dialect)
        }

        fun connect(datasource: DataSource, provider: (Database) -> TransactionProvider = { ThreadLocalTransactionProvider(it) }): Database {
            return Database { datasource.connection!! }.apply {
                TransactionProvider.provider = provider(this)
            }
        }

        fun connect(url: String, driver: String, user: String = "", password: String = "",
                    provider: (Database) -> TransactionProvider = { ThreadLocalTransactionProvider(it) }): Database {
            Class.forName(driver).newInstance()

            return Database { DriverManager.getConnection(url, user, password) }.apply {
                TransactionProvider.provider = provider(this)
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


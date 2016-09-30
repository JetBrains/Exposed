package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.sql.DataSource

class Database private constructor(val connector: () -> Connection) {

    internal val metadata: DatabaseMetaData get() = TransactionManager.currentOrNull()?.connection?.metaData ?: with(connector()) {
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

    val keywords by lazy(LazyThreadSafetyMode.NONE) { ANSI_SQL_2003_KEYWORDS + metadata.sqlKeywords.split(',') }
    val identityQuoteString by lazy(LazyThreadSafetyMode.NONE) { metadata.identifierQuoteString!! }
    val extraNameCharacters by lazy(LazyThreadSafetyMode.NONE) { metadata.extraNameCharacters!!}
    val supportsAlterTableWithAddColumn by lazy(LazyThreadSafetyMode.NONE) { metadata.supportsAlterTableWithAddColumn()}
    val supportsMultipleOpenResults by lazy(LazyThreadSafetyMode.NONE) { metadata.supportsMultipleOpenResults()}

    val checkedIdentities = object : LinkedHashMap<String, Boolean> (100) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size >= 1000
    }

    fun needQuotes (identity: String) : Boolean {
        return checkedIdentities.getOrPut(identity.toLowerCase()) {
            keywords.any { identity.equals(it, true) } || !identity.isIdentifier()
        }
    }

    private fun String.isIdentifier() = !isEmpty() && first().isIdentifierStart() && all { it.isIdentifierStart() || it in '0'..'9' }
    private fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_' || this in extraNameCharacters

    companion object {
        private val dialects = CopyOnWriteArrayList<DatabaseDialect>()

        init {
            registerDialect(H2Dialect)
            registerDialect(MysqlDialect)
            registerDialect(PostgreSQLDialect)
            registerDialect(SQLiteDialect)
        }

        fun registerDialect(dialect: DatabaseDialect) {
            dialects.add(0, dialect)
        }

        fun connect(datasource: DataSource, manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it) }): Database {
            return Database { datasource.connection!! }.apply {
                TransactionManager._manager = manager(this)
            }
        }

        fun connect(url: String, driver: String, user: String = "", password: String = "",
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it) }): Database {
            Class.forName(driver).newInstance()

            return Database { DriverManager.getConnection(url, user, password) }.apply {
                TransactionManager._manager = manager(this)
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


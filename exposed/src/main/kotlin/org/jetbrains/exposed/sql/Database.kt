package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.DEFAULT_ISOLATION_LEVEL
import org.jetbrains.exposed.sql.transactions.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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

    val dialect by lazy {
        val name = url.removePrefix("jdbc:").substringBefore(':')
        dialects[name.toLowerCase()]?.invoke() ?: error("No dialect registered for $name. URL=$url")
    }

    val vendor: String get() = dialect.name

    val version by lazy {
        metadata.let { BigDecimal("${it.databaseMajorVersion}.${it.databaseMinorVersion}") }
    }

    fun isVersionCovers(version: BigDecimal) = this.version >= version

    val keywords by lazy(LazyThreadSafetyMode.NONE) { ANSI_SQL_2003_KEYWORDS + VENDORS_KEYWORDS[currentDialect.name].orEmpty() + metadata.sqlKeywords.split(',') }
    val identityQuoteString by lazy(LazyThreadSafetyMode.NONE) { metadata.identifierQuoteString!!.trim() }
    val extraNameCharacters by lazy(LazyThreadSafetyMode.NONE) { metadata.extraNameCharacters!!}
    val supportsAlterTableWithAddColumn by lazy(LazyThreadSafetyMode.NONE) { metadata.supportsAlterTableWithAddColumn()}
    val supportsMultipleResultSets by lazy(LazyThreadSafetyMode.NONE) { metadata.supportsMultipleResultSets()}
    val shouldQuoteIdentifiers by lazy(LazyThreadSafetyMode.NONE) {
        !metadata.storesMixedCaseQuotedIdentifiers() && metadata.supportsMixedCaseQuotedIdentifiers()
    }

    val checkedIdentities = object : LinkedHashMap<String, Boolean> (100) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size >= 1000
    }

    fun needQuotes (identity: String) : Boolean {
        return checkedIdentities.getOrPut(identity.toLowerCase()) {
            keywords.any { identity.equals(it, true) } || !identity.isIdentifier()
        }
    }

    var defaultFetchSize: Int? = null
        private set

    fun defaultFetchSize(size: Int): Database {
        defaultFetchSize = size
        return this
    }

    private fun String.isIdentifier() = !isEmpty() && first().isIdentifierStart() && all { it.isIdentifierStart() || it in '0'..'9' }
    private fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_' || this in extraNameCharacters

    companion object {
        private val dialects = ConcurrentHashMap<String, () ->DatabaseDialect>()

        init {
            registerDialect(H2Dialect.dialectName) { H2Dialect() }
            registerDialect(MysqlDialect.dialectName) { MysqlDialect() }
            registerDialect(PostgreSQLDialect.dialectName) { PostgreSQLDialect() }
            registerDialect(SQLiteDialect.dialectName) { SQLiteDialect() }
            registerDialect(OracleDialect.dialectName) { OracleDialect() }
            registerDialect(SQLServerDialect.dialectName) { SQLServerDialect() }
            registerDialect(MariaDBDialect.dialectName) { MariaDBDialect() }
        }

        fun registerDialect(prefix:String, dialect: () -> DatabaseDialect) {
            dialects[prefix] = dialect
        }

        private fun doConnect(getNewConnection: () -> Connection, setupConnection: (Connection) -> Unit = {},
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return Database {
                getNewConnection().apply { setupConnection(this) }
            }.apply {
                TransactionManager.registerManager(this, manager(this))
            }
        }

        fun connect(datasource: DataSource, setupConnection: (Connection) -> Unit = {},
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return doConnect( { datasource.connection!! }, setupConnection, manager )
        }

        fun connect(getNewConnection: () -> Connection,
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return doConnect( getNewConnection, manager = manager )
        }
        fun connect(url: String, driver: String, user: String = "", password: String = "", setupConnection: (Connection) -> Unit = {},
                    manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) }): Database {
            Class.forName(driver).newInstance()

            return doConnect( { DriverManager.getConnection(url, user, password) }, setupConnection, manager )
        }
    }
}

val Database.name : String get() = url.substringAfterLast('/').substringBefore('?')

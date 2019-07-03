package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

class Database private constructor(val connector: () -> Connection) {

    var useNestedTransactions: Boolean = false

    internal fun <T> metadata(body: DatabaseMetaData.() -> T) : T {
        val transaction = TransactionManager.currentOrNull()
        return if (transaction == null) {
            val connection = connector()
            try {
                connection.metaData.body()
            } finally {
                connection.close()
            }
        } else
            transaction.connection.metaData.body()
    }

    val url: String by lazy { metadata { url } }

    val dialect by lazy {
        val name = url.removePrefix("jdbc:").substringBefore(':')
        dialects[name.toLowerCase()]?.invoke() ?: error("No dialect registered for $name. URL=$url")
    }

    val vendor: String get() = dialect.name

    val version by lazy {
        metadata { BigDecimal("$databaseMajorVersion.$databaseMinorVersion") }
    }

    fun isVersionCovers(version: BigDecimal) = this.version >= version

    val supportsAlterTableWithAddColumn by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsAlterTableWithAddColumn() } }
    val supportsMultipleResultSets by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsMultipleResultSets() } }

    internal val identifierManager by lazy { metadata { IdentifierManager(this) } }

    internal class IdentifierManager(metadata: DatabaseMetaData) {
        internal val quoteString = metadata.identifierQuoteString!!.trim()
        private val isUpperCaseIdentifiers = metadata.storesUpperCaseIdentifiers()
        private val isUpperCaseQuotedIdentifiers = metadata.storesUpperCaseQuotedIdentifiers()
        private val isLowerCaseIdentifiers = metadata.storesLowerCaseIdentifiers()
        private val isLowerCaseQuotedIdentifiers = metadata.storesLowerCaseQuotedIdentifiers()
        private val supportsMixedIdentifiers = metadata.supportsMixedCaseIdentifiers()
        private val supportsMixedQuotedIdentifiers = metadata.supportsMixedCaseQuotedIdentifiers()
        private val supportsMixedId = metadata.supportsMixedCaseIdentifiers()
        private val supportsQuotedMixedId = metadata.supportsMixedCaseQuotedIdentifiers()
        val keywords = ANSI_SQL_2003_KEYWORDS + VENDORS_KEYWORDS[currentDialect.name].orEmpty() + metadata.sqlKeywords.split(',')
        private val extraNameCharacters = metadata.extraNameCharacters!!
        private val isOracle = metadata.databaseProductName == "Oracle"
        private val identifierLengthLimit = run {
            if (isOracle)
                128
            else
                metadata.maxColumnNameLength.takeIf { it > 0 } ?: Int.MAX_VALUE
        }

        val checkedIdentities = object : LinkedHashMap<String, Boolean> (100) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size >= 1000
        }

        private fun String.isIdentifier() = !isEmpty() && first().isIdentifierStart() && all { it.isIdentifierStart() || it in '0'..'9' }
        private fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_' || this in extraNameCharacters

        fun needQuotes (identity: String) : Boolean {
            return checkedIdentities.getOrPut(identity.toLowerCase()) {
                keywords.any { identity.equals(it, true) } || !identity.isIdentifier()
            }
        }

        private fun String.isAlreadyQuoted()  = startsWith(quoteString) && endsWith(quoteString)

        fun shouldQuoteIdentifier(identity: String) : Boolean {
            val alreadyQuoted = identity.isAlreadyQuoted()
            val alreadyLower = identity.equals(identity.toLowerCase())
            val alreadyUpper = identity.equals(identity.toUpperCase())
            return when {
                alreadyQuoted -> false
                supportsMixedId -> false
                alreadyLower && isLowerCaseIdentifiers -> false
                alreadyUpper && isUpperCaseIdentifiers -> false
                isOracle -> false
                supportsQuotedMixedId && (!alreadyLower && !alreadyUpper) -> true
                else -> false
            }
        }

        fun inProperCase(identity: String) : String {
            val alreadyQuoted = identity.isAlreadyQuoted()
            return when {
                alreadyQuoted && supportsMixedQuotedIdentifiers -> identity
                alreadyQuoted && isUpperCaseQuotedIdentifiers -> identity.toUpperCase()
                alreadyQuoted && isLowerCaseQuotedIdentifiers -> identity.toLowerCase()
                supportsMixedIdentifiers -> identity
                isOracle -> identity.toUpperCase()
                isUpperCaseIdentifiers -> identity.toUpperCase()
                isLowerCaseIdentifiers -> identity.toLowerCase()
                else -> identity
            }
        }

        fun quoteIfNecessary (identity: String) : String {
            return if (identity.contains('.'))
                identity.split('.').joinToString(".") {quoteTokenIfNecessary(it)}
            else {
                quoteTokenIfNecessary(identity)
            }
        }

        fun quoteIdentifierWhenWrongCaseOrNecessary(identity: String) : String {
            val inProperCase = inProperCase(identity)
            return if (shouldQuoteIdentifier(identity) && inProperCase != identity)
                quote(identity)
            else
                quoteIfNecessary(inProperCase)
        }

        fun cutIfNecessaryAndQuote(identity: String) = quoteIfNecessary(identity.take(identifierLengthLimit))

        private fun quoteTokenIfNecessary(token: String) : String = if (needQuotes(token)) quote(token) else token

        private fun quote(identity: String) = "$quoteString$identity$quoteString".trim()
    }

    var defaultFetchSize: Int? = null
        private set

    fun defaultFetchSize(size: Int): Database {
        defaultFetchSize = size
        return this
    }

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

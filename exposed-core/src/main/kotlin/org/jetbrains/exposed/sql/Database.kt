package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.ConnectionPoolDataSource
import javax.sql.DataSource

class Database private constructor(private val resolvedVendor: String? = null, val connector: () -> ExposedConnection<*>) {

    var useNestedTransactions: Boolean = false

    internal fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T {
        val transaction = TransactionManager.currentOrNull()
        return if (transaction == null) {
            val connection = connector()
            try {
                connection.metadata(body)
            } finally {
                connection.close()
            }
        } else
            transaction.connection.metadata(body)
    }

    val url: String by lazy { metadata { url } }
    val vendor: String by lazy {
        resolvedVendor ?: metadata { databaseDialectName }
    }

    val dialect by lazy {
        dialects[vendor.toLowerCase()]?.invoke() ?: error("No dialect registered for $name. URL=$url")
    }

    val version by lazy { metadata { version } }

    fun isVersionCovers(version: BigDecimal) = this.version >= version

    val supportsAlterTableWithAddColumn by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsAlterTableWithAddColumn } }
    val supportsMultipleResultSets by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsMultipleResultSets } }

    val identifierManager by lazy { metadata { identifierManager } }

    var defaultFetchSize: Int? = null
        private set

    fun defaultFetchSize(size: Int): Database {
        defaultFetchSize = size
        return this
    }

    companion object {
        private val dialects = ConcurrentHashMap<String, () -> DatabaseDialect>()

        private val connectionInstanceImpl: DatabaseConnectionAutoRegistration =
            ServiceLoader.load(DatabaseConnectionAutoRegistration::class.java, Database::class.java.classLoader).firstOrNull() ?: error("Can't load implementation for ${DatabaseConnectionAutoRegistration::class.simpleName}")

        private val driverMapping = mutableMapOf(
            "jdbc:h2" to "org.h2.Driver",
            "jdbc:postgresql" to "org.postgresql.Driver",
            "jdbc:pgsql" to "com.impossibl.postgres.jdbc.PGDriver",
            "jdbc:mysql" to "com.mysql.cj.jdbc.Driver",
            "jdbc:mariadb" to "org.mariadb.jdbc.Driver",
            "jdbc:oracle" to "oracle.jdbc.OracleDriver",
            "jdbc:sqlite" to "org.sqlite.JDBC",
            "jdbc:sqlserver" to "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "jdbc:as400" to "com.ibm.as400.access.AS400JDBCDriver"
        )
        private val dialectMapping = mutableMapOf(
            "jdbc:h2" to H2Dialect.dialectName,
            "jdbc:postgresql" to PostgreSQLDialect.dialectName,
            "jdbc:pgsql" to PostgreSQLNGDialect.dialectName,
            "jdbc:mysql" to MysqlDialect.dialectName,
            "jdbc:mariadb" to MariaDBDialect.dialectName,
            "jdbc:oracle" to OracleDialect.dialectName,
            "jdbc:sqlite" to SQLiteDialect.dialectName,
            "jdbc:sqlserver" to SQLServerDialect.dialectName,
            "jdbc:as400" to DB2Dialect.dialectName
        )

        init {
            registerDialect(H2Dialect.dialectName) { H2Dialect() }
            registerDialect(MysqlDialect.dialectName) { MysqlDialect() }
            registerDialect(PostgreSQLDialect.dialectName) { PostgreSQLDialect() }
            registerDialect(PostgreSQLNGDialect.dialectName) { PostgreSQLNGDialect() }
            registerDialect(SQLiteDialect.dialectName) { SQLiteDialect() }
            registerDialect(OracleDialect.dialectName) { OracleDialect() }
            registerDialect(SQLServerDialect.dialectName) { SQLServerDialect() }
            registerDialect(MariaDBDialect.dialectName) { MariaDBDialect() }
            registerDialect(DB2Dialect.dialectName) { DB2Dialect() }
        }

        fun registerDialect(prefix: String, dialect: () -> DatabaseDialect) {
            dialects[prefix] = dialect
        }

        fun registerJdbcDriver(prefix: String, driverClassName: String, dialect: String) {
            driverMapping[prefix] = driverClassName
            dialectMapping[prefix] = dialect
        }

        private fun doConnect(
            explicitVendor: String?,
            getNewConnection: () -> Connection,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return Database(explicitVendor) {
                connectionInstanceImpl(getNewConnection().apply { setupConnection(this) })
            }.apply {
                TransactionManager.registerManager(this, manager(this))
            }
        }

        fun connect(
            datasource: DataSource,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return doConnect(explicitVendor = null, getNewConnection = { datasource.connection!! }, setupConnection = setupConnection, manager = manager)
        }

        @Deprecated(level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("connectPool(datasource, setupConnection, manager)"), message = "Use connectPool instead")
        fun connect(
            datasource: ConnectionPoolDataSource,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return doConnect(explicitVendor = null, getNewConnection = { datasource.pooledConnection.connection!! }, setupConnection = setupConnection, manager = manager)
        }

        fun connectPool(
            datasource: ConnectionPoolDataSource,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return doConnect(explicitVendor = null, getNewConnection = { datasource.pooledConnection.connection!! }, setupConnection = setupConnection, manager = manager)
        }

        fun connect(
            getNewConnection: () -> Connection,
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            return doConnect(explicitVendor = null, getNewConnection = getNewConnection, manager = manager)
        }

        fun connect(
            url: String,
            driver: String = getDriver(url),
            user: String = "",
            password: String = "",
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            Class.forName(driver).newInstance()

            return doConnect(getDialectName(url), { DriverManager.getConnection(url, user, password) }, setupConnection, manager)
        }

        fun getDefaultIsolationLevel(db: Database): Int =
            when (db.vendor) {
                SQLiteDialect.dialectName -> Connection.TRANSACTION_SERIALIZABLE
                OracleDialect.dialectName -> Connection.TRANSACTION_READ_COMMITTED
                PostgreSQLDialect.dialectName -> Connection.TRANSACTION_READ_COMMITTED
                PostgreSQLNGDialect.dialectName -> Connection.TRANSACTION_READ_COMMITTED
                else -> DEFAULT_ISOLATION_LEVEL
            }

        private fun getDriver(url: String) = driverMapping.entries.firstOrNull { (prefix, _) ->
            url.startsWith(prefix)
        }?.value ?: error("Database driver not found for $url")

        private fun getDialectName(url: String) = dialectMapping.entries.firstOrNull { (prefix, _) ->
            url.startsWith(prefix)
        }?.value ?: error("Can't resolve dialect for connection: $url")
    }
}

interface DatabaseConnectionAutoRegistration : (Connection) -> ExposedConnection<*>

val Database.name: String get() = url.substringAfterLast('/').substringBefore('?')

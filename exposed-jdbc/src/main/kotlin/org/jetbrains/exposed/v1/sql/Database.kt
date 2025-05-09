package org.jetbrains.exposed.v1.sql

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.v1.sql.statements.api.JdbcExposedDatabaseMetadata
import org.jetbrains.exposed.v1.sql.transactions.TransactionManager
import org.jetbrains.exposed.v1.sql.vendors.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * Class representing the underlying database to which connections are made and on which transaction tasks are performed.
 */
class Database private constructor(
    resolvedVendor: String? = null,
    config: DatabaseConfig,
    val connector: () -> ExposedConnection<*>
) : DatabaseApi(resolvedVendor, config) {
    internal fun <T> metadata(body: JdbcExposedDatabaseMetadata.() -> T): T {
        val transaction = TransactionManager.currentOrNull()
        return if (transaction == null) {
            val connection = connector()
            try {
                connection.metadata(body)
            } finally {
                connection.close()
            }
        } else {
            transaction.connection.metadata(body)
        }
    }

    override val url: String by lazy { metadata { url } }

    override val vendor: String by lazy {
        resolvedVendor ?: metadata { databaseDialectName }
    }

    override val dialect: DatabaseDialect by lazy {
        config.explicitDialect
            ?: run {
                @OptIn(InternalApi::class)
                dialects[vendor.lowercase()]?.invoke()
            }
            ?: error("No dialect registered for $name. URL=$url")
    }

    /** The name of the database as a [DatabaseDialectMetadata]. */
    val dialectMetadata: DatabaseDialectMetadata by lazy {
        dialectsMetadata[vendor.lowercase()]?.invoke()
            ?: error("No dialect metadata registered for $name. URL=$url")
    }

    override val dialectMode: String? by lazy { metadata { databaseDialectMode } }

    override val version: BigDecimal by lazy { metadata { version } }

    override fun isVersionCovers(version: BigDecimal): Boolean = this.version >= version

    /** The major version number of the database as a [Int]. */
    val majorVersion by lazy { metadata { majorVersion } }

    /** The minor version number of the database as a [Int]. */
    val minorVersion by lazy { metadata { minorVersion } }

    override fun isVersionCovers(majorVersion: Int, minorVersion: Int): Boolean =
        this.majorVersion > majorVersion || (this.majorVersion == majorVersion && this.minorVersion >= minorVersion)

    override val fullVersion: String by lazy { metadata { databaseProductVersion } }

    override val supportsAlterTableWithAddColumn: Boolean by lazy(
        LazyThreadSafetyMode.NONE
    ) { metadata { supportsAlterTableWithAddColumn } }

    override val supportsAlterTableWithDropColumn: Boolean by lazy(
        LazyThreadSafetyMode.NONE
    ) { metadata { supportsAlterTableWithDropColumn } }

    override val supportsMultipleResultSets: Boolean by lazy(
        LazyThreadSafetyMode.NONE
    ) { metadata { supportsMultipleResultSets } }

    override val identifierManager: IdentifierManagerApi by lazy { metadata { identifierManager } }

    /** Whether [Database.connect] was invoked with a [DataSource] argument. */
    internal var connectsViaDataSource = false
        private set

    /**
     * The transaction isolation level defined by a [DataSource] connection.
     *
     * This should only hold a value other than -1 if [connectsViaDataSource] has been set to `true`.
     */
    internal var dataSourceIsolationLevel: Int = -1

    /**
     * The read-only setting defined by a [DataSource] connection.
     *
     * This value should only be adjusted if [connectsViaDataSource] has been set to `true`.
     */
    internal var dataSourceReadOnly: Boolean = false

    companion object {
        private val connectionInstanceImpl: DatabaseConnectionAutoRegistration by lazy {
            ServiceLoader.load(DatabaseConnectionAutoRegistration::class.java, Database::class.java.classLoader)
                .firstOrNull()
                ?: error("Can't load implementation for ${DatabaseConnectionAutoRegistration::class.simpleName}")
        }

        private val driverMapping = mutableMapOf(
            "jdbc:h2" to "org.h2.Driver",
            "jdbc:postgresql" to "org.postgresql.Driver",
            "jdbc:pgsql" to "com.impossibl.postgres.jdbc.PGDriver",
            "jdbc:mysql" to "com.mysql.cj.jdbc.Driver",
            "jdbc:mariadb" to "org.mariadb.jdbc.Driver",
            "jdbc:oracle" to "oracle.jdbc.OracleDriver",
            "jdbc:sqlite" to "org.sqlite.JDBC",
            "jdbc:sqlserver" to "com.microsoft.sqlserver.jdbc.SQLServerDriver"
        )
        private val dialectMapping = mutableMapOf(
            "jdbc:h2" to H2Dialect.dialectName,
            "jdbc:postgresql" to PostgreSQLDialect.dialectName,
            "jdbc:pgsql" to PostgreSQLNGDialect.dialectName,
            "jdbc:mysql" to MysqlDialect.dialectName,
            "jdbc:mariadb" to MariaDBDialect.dialectName,
            "jdbc:oracle" to OracleDialect.dialectName,
            "jdbc:sqlite" to SQLiteDialect.dialectName,
            "jdbc:sqlserver" to SQLServerDialect.dialectName
        )

        private val dialectsMetadata = ConcurrentHashMap<String, () -> DatabaseDialectMetadata>()

        init {
            registerDialect(H2Dialect.dialectName) { H2Dialect() }
            registerDialect(MysqlDialect.dialectName) { MysqlDialect() }
            registerDialect(PostgreSQLDialect.dialectName) { PostgreSQLDialect() }
            registerDialect(PostgreSQLNGDialect.dialectName) { PostgreSQLNGDialect() }
            registerDialect(SQLiteDialect.dialectName) { SQLiteDialect() }
            registerDialect(OracleDialect.dialectName) { OracleDialect() }
            registerDialect(SQLServerDialect.dialectName) { SQLServerDialect() }
            registerDialect(MariaDBDialect.dialectName) { MariaDBDialect() }

            registerDialectMetadata(H2Dialect.dialectName) { H2DialectMetadata() }
            registerDialectMetadata(MysqlDialect.dialectName) { MysqlDialectMetadata() }
            registerDialectMetadata(PostgreSQLDialect.dialectName) { PostgreSQLDialectMetadata() }
            registerDialectMetadata(PostgreSQLNGDialect.dialectName) { PostgreSQLNGDialectMetadata() }
            registerDialectMetadata(SQLiteDialect.dialectName) { SQLiteDialectMetadata() }
            registerDialectMetadata(OracleDialect.dialectName) { OracleDialectMetadata() }
            registerDialectMetadata(SQLServerDialect.dialectName) { SQLServerDialectMetadata() }
            registerDialectMetadata(MariaDBDialect.dialectName) { MariaDBDialectMetadata() }
        }

        /** Registers a new [DatabaseDialectMetadata] with the identifier [prefix]. */
        fun registerDialectMetadata(prefix: String, metadata: () -> DatabaseDialectMetadata) {
            dialectsMetadata[prefix.lowercase()] = metadata
        }

        /** Registers a new JDBC driver, using the specified [driverClassName], with the identifier [prefix]. */
        fun registerJdbcDriver(prefix: String, driverClassName: String, dialect: String) {
            driverMapping[prefix] = driverClassName
            dialectMapping[prefix] = dialect
        }

        @OptIn(InternalApi::class)
        private fun doConnect(
            explicitVendor: String?,
            config: DatabaseConfig?,
            connectionAutoRegistration: DatabaseConnectionAutoRegistration,
            getNewConnection: () -> Connection,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManagerApi = { TransactionManager(it) }
        ): Database {
            return Database(explicitVendor, config ?: DatabaseConfig.invoke()) {
                connectionAutoRegistration(getNewConnection().apply { setupConnection(this) })
            }.apply {
                CoreTransactionManager.registerDatabaseManager(this, manager(this))
                // TODO ABOVE should be replaced with BELOW when ThreadLocalTransactionManager is fully deprecated
                // TransactionManager.registerManager(this, manager(this))
            }
        }

        /**
         * Creates a [Database] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param datasource The [DataSource] object to be used as a means of getting a connection.
         * @param connectionAutoRegistration The connection provider for database. If not provided,
         * a service loader will be used to locate and load a provider for [DatabaseConnectionAutoRegistration].
         * @param setupConnection Any setup that should be applied to each new connection.
         * @param databaseConfig Configuration parameters for this [Database] instance.
         * @param manager The [TransactionManager] responsible for new transactions that use this [Database] instance.
         */
        fun connect(
            datasource: DataSource,
            setupConnection: (Connection) -> Unit = {},
            databaseConfig: DatabaseConfig? = null,
            connectionAutoRegistration: DatabaseConnectionAutoRegistration = connectionInstanceImpl,
            manager: (Database) -> TransactionManagerApi = { TransactionManager(it) }
        ): Database {
            return doConnect(
                explicitVendor = null,
                config = databaseConfig,
                getNewConnection = { datasource.connection!! },
                setupConnection = setupConnection,
                manager = manager,
                connectionAutoRegistration = connectionAutoRegistration
            ).apply {
                connectsViaDataSource = true
            }
        }

        /**
         * Creates a [Database] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param getNewConnection A function that returns a new connection.
         * @param connectionAutoRegistration The connection provider for database. If not provided,
         * a service loader will be used to locate and load a provider for [DatabaseConnectionAutoRegistration].
         * @param databaseConfig Configuration parameters for this [Database] instance.
         * @param manager The [TransactionManager] responsible for new transactions that use this [Database] instance.
         */
        fun connect(
            getNewConnection: () -> Connection,
            databaseConfig: DatabaseConfig? = null,
            connectionAutoRegistration: DatabaseConnectionAutoRegistration = connectionInstanceImpl,
            manager: (Database) -> TransactionManagerApi = { TransactionManager(it) }
        ): Database {
            return doConnect(
                explicitVendor = null,
                config = databaseConfig,
                getNewConnection = getNewConnection,
                manager = manager,
                connectionAutoRegistration = connectionAutoRegistration
            )
        }

        /**
         * Creates a [Database] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param url The URL that represents the database when getting a connection.
         * @param connectionAutoRegistration The connection provider for database. If not provided,
         * a service loader will be used to locate and load a provider for [DatabaseConnectionAutoRegistration].
         * @param driver The JDBC driver class. If not provided, the specified [url] will be used to find
         * a match from the existing driver mappings.
         * @param user The database user that owns the new connections.
         * @param password The password specific for the database [user].
         * @param setupConnection Any setup that should be applied to each new connection.
         * @param databaseConfig Configuration parameters for this [Database] instance.
         * @param manager The [TransactionManager] responsible for new transactions that use this [Database] instance.
         */
        @Suppress("UnusedParameter", "LongParameterList")
        fun connect(
            url: String,
            driver: String = getDriver(url),
            user: String = "",
            password: String = "",
            setupConnection: (Connection) -> Unit = {},
            databaseConfig: DatabaseConfig? = null,
            connectionAutoRegistration: DatabaseConnectionAutoRegistration = connectionInstanceImpl,
            manager: (Database) -> TransactionManagerApi = { TransactionManager(it) }
        ): Database {
            Class.forName(driver).getDeclaredConstructor().newInstance()
            val dialectName = getDialectName(url) ?: error("Can't resolve dialect for connection: $url")
            return doConnect(
                explicitVendor = dialectName,
                config = databaseConfig,
                getNewConnection = { DriverManager.getConnection(url, user, password) },
                setupConnection = setupConnection,
                manager = manager,
                connectionAutoRegistration = connectionAutoRegistration,
            )
        }

        /** Returns the stored default transaction isolation level for a specific database. */
        fun getDefaultIsolationLevel(db: Database): Int =
            when (db.dialect) {
                is SQLiteDialect -> Connection.TRANSACTION_SERIALIZABLE
                is MysqlDialect -> Connection.TRANSACTION_REPEATABLE_READ
                else -> Connection.TRANSACTION_READ_COMMITTED
            }

        private fun getDriver(url: String) = driverMapping.entries.firstOrNull { (prefix, _) ->
            url.startsWith(prefix)
        }?.value ?: error("Database driver not found for $url")

        /** Returns the database name used internally for the provided connection [url]. */
        fun getDialectName(url: String) = dialectMapping.entries.firstOrNull { (prefix, _) ->
            url.startsWith(prefix)
        }?.value
    }
}

/** Represents an [ExposedConnection] that is loaded whenever a connection is accessed by a [Database] instance. */
interface DatabaseConnectionAutoRegistration : (Connection) -> ExposedConnection<*>

/** Returns the name of the database obtained from its connection URL. */
val Database.name: String
    get() = url.substringBefore('?').substringAfterLast('/')

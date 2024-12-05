package org.jetbrains.exposed.sql

import org.jetbrains.annotations.TestOnly
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.ConnectionPoolDataSource
import javax.sql.DataSource

/**
 * Class representing the underlying database to which connections are made and on which transaction tasks are performed.
 */
class Database private constructor(
    private val resolvedVendor: String? = null,
    val config: DatabaseConfig,
    val connector: () -> ExposedConnection<*>
) {
    /** Whether nested transaction blocks are configured to act like top-level transactions. */
    var useNestedTransactions: Boolean = config.useNestedTransactions
        @Deprecated("Use DatabaseConfig to define the useNestedTransactions", level = DeprecationLevel.ERROR)
        @TestOnly
        set

    override fun toString(): String =
        "ExposedDatabase[${hashCode()}]($resolvedVendor${config.explicitDialect?.let { ", dialect=$it" } ?: ""})"

    internal fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T {
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

    /** The connection URL for the database. */
    val url: String by lazy { metadata { url } }

    /** The name of the database based on the name of the underlying JDBC driver. */
    val vendor: String by lazy {
        resolvedVendor ?: metadata { databaseDialectName }
    }

    /** The name of the database as a [DatabaseDialect]. */
    val dialect by lazy {
        config.explicitDialect ?: dialects[vendor.lowercase()]?.invoke() ?: error("No dialect registered for $name. URL=$url")
    }

    /** The version number of the database as a [BigDecimal]. */
    val version by lazy { metadata { version } }

    /** Whether the version number of the database is equal to or greater than the provided [version]. */
    fun isVersionCovers(version: BigDecimal) = this.version >= version

    /** The major version number of the database as a [Int]. */
    val majorVersion by lazy { metadata { majorVersion } }

    /** The minor version number of the database as a [Int]. */
    val minorVersion by lazy { metadata { minorVersion } }

    /** Whether the version number of the database is equal to or greater than the provided [majorVersion] and [minorVersion]. */
    fun isVersionCovers(majorVersion: Int, minorVersion: Int) =
        this.majorVersion >= majorVersion && this.minorVersion >= minorVersion

    /** Whether the database supports ALTER TABLE with an add column clause. */
    val supportsAlterTableWithAddColumn by lazy(
        LazyThreadSafetyMode.NONE
    ) { metadata { supportsAlterTableWithAddColumn } }

    /** Whether the database supports ALTER TABLE with a drop column clause. */
    val supportsAlterTableWithDropColumn by lazy(
        LazyThreadSafetyMode.NONE
    ) { metadata { supportsAlterTableWithDropColumn } }

    /** Whether the database supports getting multiple result sets from a single execute. */
    val supportsMultipleResultSets by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsMultipleResultSets } }

    /** The database-specific class responsible for parsing and processing identifier tokens in SQL syntax. */
    val identifierManager by lazy { metadata { identifierManager } }

    /** The default number of results that should be fetched when queries are executed. */
    var defaultFetchSize: Int? = config.defaultFetchSize
        private set

    @Deprecated("Use DatabaseConfig to define the defaultFetchSize", level = DeprecationLevel.ERROR)
    @TestOnly
    fun defaultFetchSize(size: Int): Database {
        defaultFetchSize = size
        return this
    }

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
        internal val dialects = ConcurrentHashMap<String, () -> DatabaseDialect>()

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

        init {
            registerDialect(H2Dialect.dialectName) { H2Dialect() }
            registerDialect(MysqlDialect.dialectName) { MysqlDialect() }
            registerDialect(PostgreSQLDialect.dialectName) { PostgreSQLDialect() }
            registerDialect(PostgreSQLNGDialect.dialectName) { PostgreSQLNGDialect() }
            registerDialect(SQLiteDialect.dialectName) { SQLiteDialect() }
            registerDialect(OracleDialect.dialectName) { OracleDialect() }
            registerDialect(SQLServerDialect.dialectName) { SQLServerDialect() }
            registerDialect(MariaDBDialect.dialectName) { MariaDBDialect() }
        }

        /** Registers a new [DatabaseDialect] with the identifier [prefix]. */
        fun registerDialect(prefix: String, dialect: () -> DatabaseDialect) {
            dialects[prefix.lowercase()] = dialect
        }

        /** Registers a new JDBC driver, using the specified [driverClassName], with the identifier [prefix]. */
        fun registerJdbcDriver(prefix: String, driverClassName: String, dialect: String) {
            driverMapping[prefix] = driverClassName
            dialectMapping[prefix] = dialect
        }

        private fun doConnect(
            explicitVendor: String?,
            config: DatabaseConfig?,
            connectionAutoRegistration: DatabaseConnectionAutoRegistration,
            getNewConnection: () -> Connection,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it) }
        ): Database {
            return Database(explicitVendor, config ?: DatabaseConfig.invoke()) {
                connectionAutoRegistration(getNewConnection().apply { setupConnection(this) })
            }.apply {
                TransactionManager.registerManager(this, manager(this))
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
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it) }
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
         * JDBC driver implementations of `ConnectionPoolDataSource` are not actual connection pools, but rather a means
         * of obtaining the physical connections to then be used in a connection pool. It is a known issue, with no plans
         * to be fixed, that `SQLiteConnectionPoolDataSource`, for example, creates a new connection each time instead
         * of retrieving a pooled connection. Other implementations, like `PGConnectionPoolDataSource`, suggest that a
         * `DataSource` or a dedicated third-party connection pool library should be used instead.
         *
         * Please leave a comment on [YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-354/Deprecate-Database.connectPool-with-ConnectionPoolDataSource)
         * with a use-case if your driver implementation requires that this function remain part of the API.
         *
         * [SQLiteConnectionPoolDataSource issue #1011](https://github.com/xerial/sqlite-jdbc/issues/1011)
         *
         * [PGConnectionPoolDataSource source code](https://github.com/pgjdbc/pgjdbc/blob/master/pgjdbc/src/main/java/org/postgresql/ds/PGConnectionPoolDataSource.java)
         *
         * [MysqlConnectionPoolDataSource source code](https://github.com/spullara/mysql-connector-java/blob/master/src/main/java/com/mysql/jdbc/jdbc2/optional/MysqlConnectionPoolDataSource.java)
         */
        @Deprecated(
            message = "Use Database.connect() with a connection pool DataSource instead. This may be removed in future releases.",
            level = DeprecationLevel.ERROR
        )
        fun connectPool(
            datasource: ConnectionPoolDataSource,
            setupConnection: (Connection) -> Unit = {},
            databaseConfig: DatabaseConfig? = null,
            connectionAutoRegistration: DatabaseConnectionAutoRegistration = connectionInstanceImpl,
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it) }
        ): Database {
            return doConnect(
                explicitVendor = null,
                config = databaseConfig,
                getNewConnection = { datasource.pooledConnection.connection!! },
                setupConnection = setupConnection,
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
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it) }
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
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it) }
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

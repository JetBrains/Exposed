package org.jetbrains.exposed.sql

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.sql.statements.api.R2dbcExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcConnectionImpl
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcScope
import org.jetbrains.exposed.sql.statements.r2dbc.asInt
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import org.reactivestreams.Publisher
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Class representing the underlying R2DBC database to which connections are made and on which transaction tasks are performed.
 */
class R2dbcDatabase private constructor(
    resolvedVendor: String? = null,
    config: DatabaseConfig,
    val connector: () -> R2dbcExposedConnection<*>
) : DatabaseApi(resolvedVendor, config) {
    internal suspend fun <T> metadata(body: suspend R2dbcExposedDatabaseMetadata.() -> T): T {
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

    // must all properties be suspend functions to retrieve metadata
    // should there be properties that call private functions?
    /** The connection URL for the database. */
    val url: String = "" // metadata { getUrl() }

    /** The name of the database based on the name of the underlying JDBC driver. */
    val vendor: String = "" // resolvedVendor ?: metadata { getDatabaseDialectName() } }

    override val dialect: DatabaseDialect by lazy {
        config.explicitDialect
            ?: run {
                @OptIn(InternalApi::class)
                dialects[vendor.lowercase()]?.invoke()
            }
            ?: error("No dialect registered for $name. URL=$url")
    }

    val dialectMetadata: DatabaseDialectMetadata by lazy {
        dialectsMetadata[vendor.lowercase()]?.invoke()
            ?: error("No dialect metadata registered for $name. URL=$url")
    }

    override val dialectMode: String? = null // metadata { getDatabaseDialectMode() }

    override val version: BigDecimal = 1.toBigDecimal() // metadata { getVersion() }

    override fun isVersionCovers(version: BigDecimal): Boolean = this.version >= version

    override val majorVersion: Int = 1 // metadata { getMajorVersion() }

    override val minorVersion: Int = 1 // metadata { getMinorVersion() }

    override fun isVersionCovers(majorVersion: Int, minorVersion: Int): Boolean =
        this.majorVersion > majorVersion || (this.majorVersion == majorVersion && this.minorVersion >= minorVersion)

    override val fullVersion: String = "" // metadata { getDatabaseProductVersion() }

    override val supportsAlterTableWithAddColumn: Boolean = true // metadata { supportsAlterTableWithAddColumn }

    override val supportsAlterTableWithDropColumn: Boolean = true // metadata { supportsAlterTableWithDropColumn }

    override val supportsMultipleResultSets: Boolean = true // metadata { supportsMultipleResultSets }

    override val identifierManager: IdentifierManagerApi = TODO() // metadata { identifierManager }

    companion object {
        private val r2dbcDialectMapping = mutableMapOf(
            "r2dbc:h2" to H2Dialect.dialectName,
            "r2dbc:postgresql" to PostgreSQLDialect.dialectName,
            "r2dbc:mysql" to MysqlDialect.dialectName,
            "r2dbc:mariadb" to MariaDBDialect.dialectName,
            "r2dbc:oracle" to OracleDialect.dialectName,
            "r2dbc:mssql" to SQLServerDialect.dialectName
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
        private fun registerDialectMetadata(prefix: String, metadata: () -> DatabaseDialectMetadata) {
            dialectsMetadata[prefix.lowercase()] = metadata
        }

        /** Registers a new R2DBC driver, using the specified [dialect], with the identifier [prefix]. */
        fun registerR2dbcDriver(prefix: String, dialect: String) {
            r2dbcDialectMapping[prefix] = dialect
        }

        private fun doConnect(
            url: String,
            explicitVendor: String,
            config: DatabaseConfig?,
            getNewConnection: () -> Publisher<out Connection>,
            dispatcher: CoroutineDispatcher?,
            manager: (R2dbcDatabase) -> TransactionManager = { TransactionManager(it) }
        ): R2dbcDatabase {
            return R2dbcDatabase(url, config ?: DatabaseConfig.invoke()) {
                R2dbcConnectionImpl(explicitVendor, getNewConnection(), R2dbcScope(dispatcher))
            }.apply {
                TransactionManager.registerManager(this, manager(this))
            }
        }

        /**
         * Creates a [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param connectionOptions Builder options that represent the configuration state of the database when
         * getting a connection.
         * @param databaseConfig Configuration parameters for this [R2dbcDatabase] instance.
         * @param dispatcher [CoroutineDispatcher] responsible for determining the threads for execution.
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from values
         * provided to [connectionOptions].
         */
        fun connect(
            connectionOptions: ConnectionFactoryOptions,
            databaseConfig: DatabaseConfig? = null,
            dispatcher: CoroutineDispatcher? = null,
            manager: (R2dbcDatabase) -> TransactionManager = { TransactionManager(it) }
        ): R2dbcDatabase {
            val url = "r2dbc:${connectionOptions.getValue(ConnectionFactoryOptions.DRIVER)}"
            val dialectName = getR2dbcDialectName(url) ?: error("Can't resolve dialect for connection: $url")
            return doConnect(
                url = url,
                explicitVendor = dialectName,
                config = databaseConfig,
                getNewConnection = { ConnectionFactories.get(connectionOptions).create() },
                dispatcher = dispatcher,
                manager = manager
            )
        }

        /**
         * Creates a [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param connectionFactory The [ConnectionFactory] entry point for an R2DBC driver when getting a connection.
         * @param databaseConfig Configuration parameters for this [R2dbcDatabase] instance.
         * @param databaseDialect The specific [DatabaseDialect] that should be used for all connections from this instance.
         * @param dispatcher [CoroutineDispatcher] responsible for determining the threads for execution.
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect is either not provided to [databaseDialect]
         * or bot set as the `DatabaseConfig.explicitDialect` property.
         */
        fun connect(
            connectionFactory: ConnectionFactory,
            databaseConfig: DatabaseConfig? = null,
            databaseDialect: DatabaseDialect = databaseConfig?.explicitDialect ?: error("Can't resolve dialect for connection"),
            dispatcher: CoroutineDispatcher? = null,
            manager: (R2dbcDatabase) -> TransactionManager = { TransactionManager(it) }
        ): R2dbcDatabase {
            return doConnect(
                url = "",
                explicitVendor = databaseDialect.name,
                config = databaseConfig,
                getNewConnection = { connectionFactory.create() },
                dispatcher = dispatcher,
                manager = manager
            )
        }

        /**
         * Creates a [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param url The URL that represents the database when getting a connection.
         * @param databaseConfig Configuration parameters for this [R2dbcDatabase] instance.
         * @param dispatcher [CoroutineDispatcher] responsible for determining the threads for execution.
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from the provided [url].
         */
        fun connect(
            url: String,
            databaseConfig: DatabaseConfig? = null,
            dispatcher: CoroutineDispatcher? = null,
            manager: (R2dbcDatabase) -> TransactionManager = { TransactionManager(it) }
        ): R2dbcDatabase {
            val dialectName = getR2dbcDialectName(url) ?: error("Can't resolve dialect for connection: $url")
            return doConnect(
                url = url,
                explicitVendor = dialectName,
                config = databaseConfig,
                getNewConnection = { ConnectionFactories.get(url).create() },
                dispatcher = dispatcher,
                manager = manager
            )
        }

        /** Returns the stored default transaction isolation level for a specific database. */
        fun getDefaultIsolationLevel(db: R2dbcDatabase): Int =
            when (db.dialect) {
                is MysqlDialect -> IsolationLevel.REPEATABLE_READ.asInt()
                else -> IsolationLevel.READ_COMMITTED.asInt()
            }

        /** Returns the database name used internally for the provided connection [url]. */
        fun getR2dbcDialectName(url: String) = r2dbcDialectMapping.entries.firstOrNull { (prefix, _) ->
            url.startsWith(prefix)
        }?.value
    }
}

/** Returns the name of the database obtained from its connection URL. */
val R2dbcDatabase.name: String
    get() = url.substringBefore('?').substringAfterLast('/')

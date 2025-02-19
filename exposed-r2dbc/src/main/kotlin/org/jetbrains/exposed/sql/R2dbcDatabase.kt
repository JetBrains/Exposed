package org.jetbrains.exposed.sql

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.sql.statements.api.R2dbcExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcConnectionImpl
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcScope
import org.jetbrains.exposed.sql.statements.r2dbc.asInt
import org.jetbrains.exposed.sql.transactions.CoreTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManagerApi
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

    private var connectionUrl: String = ""

    override val url: String
        get() = connectionUrl

    override val vendor: String by lazy {
        // cleanup -> getDatabaseDialectName() does not actually need suspend; relocate or refactor?
        resolvedVendor ?: runBlocking { metadata { getDatabaseDialectName() } }
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

    // REVIEW: usage in core H2Dialect
    override val dialectMode: String? by lazy { runBlocking { metadata { getDatabaseDialectMode() } } }

    // cleanup -> getVersion() does not actually need suspend; relocate or refactor?
    override val version: BigDecimal by lazy { runBlocking { metadata { getVersion() } } }

    override fun isVersionCovers(version: BigDecimal): Boolean = this.version >= version

    /** The major version number of the database as a [Int]. */
    // cleanup -> getMajorVersion() does not actually need suspend; relocate or refactor?
    val majorVersion: Int by lazy { runBlocking { metadata { getMajorVersion() } } }

    /** The minor version number of the database as a [Int]. */
    // cleanup -> getMinorVersion() does not actually need suspend; relocate or refactor?
    val minorVersion: Int by lazy { runBlocking { metadata { getMinorVersion() } } }

    override fun isVersionCovers(majorVersion: Int, minorVersion: Int): Boolean =
        this.majorVersion > majorVersion || (this.majorVersion == majorVersion && this.minorVersion >= minorVersion)

    // cleanup -> getDatabaseProductVersion() does not actually need suspend; relocate or refactor?
    override val fullVersion: String by lazy { runBlocking { metadata { getDatabaseProductVersion() } } }

    // cleanup -> none of these properties need suspend; better to call MetadataProvider directly?
    override val supportsAlterTableWithAddColumn: Boolean by lazy { runBlocking { metadata { supportsAlterTableWithAddColumn } } }

    override val supportsAlterTableWithDropColumn: Boolean by lazy { runBlocking { metadata { supportsAlterTableWithDropColumn } } }

    override val supportsMultipleResultSets: Boolean by lazy { runBlocking { metadata { supportsMultipleResultSets } } }

    // cleanup -> definitely does not actually need suspend; relocate or refactor?
    override val identifierManager: IdentifierManagerApi by lazy { runBlocking { metadata { identifierManager } } }

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

        @OptIn(InternalApi::class)
        private fun doConnect(
            url: String,
            explicitVendor: String,
            config: DatabaseConfig?,
            getNewConnection: () -> Publisher<out Connection>,
            dispatcher: CoroutineDispatcher?,
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) }
        ): R2dbcDatabase {
            return R2dbcDatabase(explicitVendor, config ?: DatabaseConfig.invoke()) {
                R2dbcConnectionImpl(getNewConnection(), explicitVendor, R2dbcScope(dispatcher))
            }.apply {
                connectionUrl = url
                CoreTransactionManager.registerDatabaseManager(this, manager(this))
                // ABOVE should be replaced with BELOW when ThreadLocalTransactionManager is fully deprecated
                // TransactionManager.registerManager(this, manager(this))
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
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) }
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
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) }
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
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) }
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

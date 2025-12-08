package org.jetbrains.exposed.v1.r2dbc

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Version
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.r2dbc.statements.R2dbcConnectionImpl
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcExposedDatabaseMetadata
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcLocalMetadataImpl
import org.jetbrains.exposed.v1.r2dbc.transactions.R2dbcTransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.vendors.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Class representing the underlying R2DBC database to which connections are made and on which transaction tasks are performed.
 *
 * @param connector Accessor for retrieving database connections wrapped as [R2dbcExposedConnection]
 */
class R2dbcDatabase private constructor(
    resolvedVendor: String? = null,
    config: R2dbcDatabaseConfig,
    val connector: () -> R2dbcExposedConnection<*>
) : DatabaseApi(resolvedVendor, config) {
    /**
     * Calls the specified function [body] with an [R2dbcExposedDatabaseMetadata] implementation as its receiver and
     * returns the retrieved metadata from the database as a result. If called outside a transaction block, a
     * temporary connection is instantiated to call [body] before being closed.
     */
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
            transaction.connection().metadata(body)
        }
    }

    /**
     * Calls the specified function [body] with an [R2dbcExposedDatabaseMetadata] implementation as its receiver and
     * returns the retrieved metadata as a result, with the metadata most likely originating from the connection instance
     * itself. Metadata retrieval that requires sending a query to the database will not be accepted. If called outside
     * a transaction block, a temporary connection is instantiated to call [body] before being closed.
     */
    internal fun <T> connectionMetadata(body: R2dbcExposedDatabaseMetadata.() -> T): T = runBlocking { metadata(body) }

    /**
     * Calls the specified function [body] with an [R2dbcLocalMetadataImpl] implementation as its receiver and
     * returns the retrieved metadata from a local provider as a result.
     *
     * @throws IllegalStateException If metadata retrieval requires either a connection or sending a query to the database.
     */
    internal fun <T> localMetadata(body: R2dbcLocalMetadataImpl.() -> T): T = localMetadata.body()

    private val localMetadata by lazy {
        resolvedVendor?.let { R2dbcLocalMetadataImpl("", it) }
            ?: error("The exact vendor dialect could not be resolved.")
    }

    private var connectionUrl: String = ""

    override val url: String
        get() = connectionUrl

    override val vendor: String by lazy {
        resolvedVendor ?: connectionMetadata { getDatabaseDialectName() }
    }

    override val dialect: DatabaseDialect by lazy {
        config.explicitDialect
            ?: run {
                @OptIn(InternalApi::class)
                dialects[vendor.lowercase()]?.invoke()
            }
            ?: error("No dialect registered for the database connected using URL=$url")
    }

    /** The name of the database as a [DatabaseDialectMetadata]. */
    val dialectMetadata: DatabaseDialectMetadata by lazy {
        dialectsMetadata[vendor.lowercase()]?.invoke()
            ?: error("No dialect metadata registered for $name. URL=$url")
    }

    private var connectionUrlMode: String? = null

    override val dialectMode: String? by lazy {
        if (connectionUrlMode != H2_INVALID_MODE) {
            connectionUrlMode
        } else {
            // dialectMode is used by property h2Mode in many places in exposed-core & cannot be made to suspend.
            // this branch should only be reached if ConnectionFactoryOptions fails to return a database value,
            // which should be highly unlikely given that the parsing happens when R2dbcDatabase.connect() is called.
            runBlocking { metadata { getDatabaseDialectMode() } }
        }
    }

    override val version: Version by lazy { connectionMetadata { getVersion() } }

    override val fullVersion: String by lazy { connectionMetadata { getDatabaseProductVersion() } }

    override val supportsAlterTableWithAddColumn: Boolean by lazy { localMetadata { supportsAlterTableWithAddColumn } }

    override val supportsAlterTableWithDropColumn: Boolean by lazy { localMetadata { supportsAlterTableWithDropColumn } }

    override val supportsMultipleResultSets: Boolean by lazy { localMetadata { supportsMultipleResultSets } }

    override val supportsSelectForUpdate: Boolean by lazy { localMetadata { supportsSelectForUpdate } }

    override val identifierManager: IdentifierManagerApi by lazy { connectionMetadata { identifierManager } }

    companion object {

        private val dialectsMetadata = ConcurrentHashMap<String, () -> DatabaseDialectMetadata>()

        private val driverMapping = mutableMapOf(
            "r2dbc:h2" to "h2",
            "r2dbc:postgresql" to "postgresql",
            "r2dbc:mysql" to "mysql",
            "r2dbc:mariadb" to "mariadb",
            "r2dbc:oracle" to "oracle",
            "r2dbc:mssql" to "sqlserver",
            "r2dbc:pool" to "pool",
        )

        init {
            registerDialect(H2Dialect.dialectName) { H2Dialect() }
            registerDialect(MysqlDialect.dialectName) { MysqlDialect() }
            registerDialect(PostgreSQLDialect.dialectName) { PostgreSQLDialect() }
            registerDialect(OracleDialect.dialectName) { OracleDialect() }
            registerDialect(SQLServerDialect.dialectName) { SQLServerDialect() }
            registerDialect(MariaDBDialect.dialectName) { MariaDBDialect() }

            registerDialectMetadata(H2Dialect.dialectName) { H2DialectMetadata() }
            registerDialectMetadata(MysqlDialect.dialectName) { MysqlDialectMetadata() }
            registerDialectMetadata(PostgreSQLDialect.dialectName) { PostgreSQLDialectMetadata() }
            registerDialectMetadata(OracleDialect.dialectName) { OracleDialectMetadata() }
            registerDialectMetadata(SQLServerDialect.dialectName) { SQLServerDialectMetadata() }
            registerDialectMetadata(MariaDBDialect.dialectName) { MariaDBDialectMetadata() }
        }

        /** Registers a new [DatabaseDialectMetadata] with the identifier [prefix]. */
        fun registerDialectMetadata(prefix: String, metadata: () -> DatabaseDialectMetadata) {
            dialectsMetadata[prefix.lowercase()] = metadata
        }

        @OptIn(InternalApi::class)
        private fun doConnect(
            manager: (R2dbcDatabase) -> R2dbcTransactionManager = { TransactionManager(it) },
            connectionFactory: ConnectionFactory?,
            config: R2dbcDatabaseConfig,
        ): R2dbcDatabase {
            val options = config.connectionFactoryOptions
            val explicitVendor = config.explicitDialect
                ?.let { if (it is H2Dialect) H2Dialect.dialectName else it.name }
                ?: options.dialectName
            val factory = connectionFactory ?: ConnectionFactories.get(options)

            return R2dbcDatabase(explicitVendor, config) {
                R2dbcConnectionImpl(factory.create(), explicitVendor, config.typeMapping)
            }.apply {
                connectionUrl = options.urlString
                connectionUrlMode = options.urlMode
                TransactionManager.registerManager(this, manager(this))
                // ABOVE should be replaced with BELOW when ThreadLocalTransactionManager is fully deprecated
                // TransactionManager.registerManager(this, manager(this))
            }
        }

        /**
         * Creates an [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @param databaseConfig Builder of configuration parameters for this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from values
         * provided to [databaseConfig].
         */
        fun connect(
            manager: (R2dbcDatabase) -> R2dbcTransactionManager = { TransactionManager(it) },
            databaseConfig: R2dbcDatabaseConfig.Builder.() -> Unit = {}
        ): R2dbcDatabase = doConnect(manager, null, R2dbcDatabaseConfig(databaseConfig).build())

        /**
         * Creates an [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @param databaseConfig Builder of configuration parameters for this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from values
         * provided to [databaseConfig].
         */
        fun connect(
            manager: (R2dbcDatabase) -> R2dbcTransactionManager = { TransactionManager(it) },
            databaseConfig: R2dbcDatabaseConfig.Builder = R2dbcDatabaseConfig.Builder()
        ): R2dbcDatabase = doConnect(manager, null, databaseConfig.build())

        /**
         * Creates an [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param connectionFactory The [ConnectionFactory] entry point for an R2DBC driver when getting a connection.
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @param databaseConfig Configuration parameters for this [R2dbcDatabase] instance. At minimum,
         * a value for `explicitDialect` must be provided to prevent throwing an exception.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from the [connectionFactory]
         * or from values provided to [databaseConfig].
         */
        fun connect(
            connectionFactory: ConnectionFactory,
            databaseConfig: R2dbcDatabaseConfig.Builder,
            manager: (R2dbcDatabase) -> R2dbcTransactionManager = { TransactionManager(it) }
        ): R2dbcDatabase = doConnect(manager, connectionFactory, databaseConfig.build())

        /**
         * Creates an [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param url The URL that represents the database when getting a connection.
         * @param driver The R2DBC driver class. If not provided, the specified [url] will be used to find
         * a match from the existing driver mappings.
         * @param user The database user that owns the new connections.
         * @param password The password specific for the database [user].
         * @param databaseConfig Configuration parameters for this [R2dbcDatabase] instance.
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from the provided [url].
         */
        fun connect(
            url: String,
            driver: String = getDriver(url),
            user: String = "",
            password: String = "",
            manager: (R2dbcDatabase) -> R2dbcTransactionManager = { TransactionManager(it) },
            databaseConfig: R2dbcDatabaseConfig.Builder = R2dbcDatabaseConfig.Builder(),
        ): R2dbcDatabase {
            databaseConfig.setUrl(url)
            databaseConfig.connectionFactoryOptions {
                option(ConnectionFactoryOptions.DRIVER, driver)
                user.takeUnless { it.isEmpty() }
                    ?.let { option(ConnectionFactoryOptions.USER, it) }
                password.takeUnless { it.isEmpty() }
                    ?.let { option(ConnectionFactoryOptions.PASSWORD, it) }
            }

            return doConnect(manager, null, databaseConfig.build())
        }

        /**
         * Creates an [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param databaseConfig Configuration parameters for this [R2dbcDatabase] instance.
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from the provided [databaseConfig].
         */
        fun connect(
            databaseConfig: R2dbcDatabaseConfig,
            manager: (R2dbcDatabase) -> R2dbcTransactionManager = { TransactionManager(it) }
        ): R2dbcDatabase = doConnect(
            manager = manager,
            connectionFactory = null,
            config = databaseConfig,
        )

        /** Returns the stored default transaction isolation level for a specific database. */
        fun getDefaultIsolationLevel(db: R2dbcDatabase): IsolationLevel =
            when (db.dialect) {
                is MysqlDialect -> IsolationLevel.REPEATABLE_READ
                else -> IsolationLevel.READ_COMMITTED
            }

        private fun getDriver(url: String) = driverMapping.entries.firstOrNull { (prefix, _) ->
            url.startsWith(prefix)
        }?.value ?: error("Database driver not found for $url")
    }
}

/** Returns the name of the database obtained from its connection URL. */
val R2dbcDatabase.name: String
    get() {
        val propertyDelimiter = if (dialect is H2Dialect) ';' else '?'
        return url.substringBefore(propertyDelimiter).substringAfterLast('/')
    }

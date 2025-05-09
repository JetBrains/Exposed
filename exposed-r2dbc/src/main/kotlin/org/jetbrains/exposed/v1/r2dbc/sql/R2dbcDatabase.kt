package org.jetbrains.exposed.v1.r2dbc.sql

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.r2dbc.sql.statements.R2dbcConnectionImpl
import org.jetbrains.exposed.v1.r2dbc.sql.statements.api.R2dbcExposedDatabaseMetadata
import org.jetbrains.exposed.v1.r2dbc.sql.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.sql.vendors.*
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Class representing the underlying R2DBC database to which connections are made and on which transaction tasks are performed.
 */
class R2dbcDatabase private constructor(
    resolvedVendor: String? = null,
    config: R2dbcDatabaseConfig,
    val connector: () -> org.jetbrains.exposed.v1.r2dbc.sql.statements.api.R2dbcExposedConnection<*>
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
    // TODO for properties that do not actually query metadata (hard-coded), switch to metadat property
    override val supportsAlterTableWithAddColumn: Boolean by lazy { runBlocking { metadata { supportsAlterTableWithAddColumn } } }

    override val supportsAlterTableWithDropColumn: Boolean by lazy { runBlocking { metadata { supportsAlterTableWithDropColumn } } }

    override val supportsMultipleResultSets: Boolean by lazy { runBlocking { metadata { supportsMultipleResultSets } } }

    // cleanup -> definitely does not actually need suspend; relocate or refactor?
    override val identifierManager: IdentifierManagerApi by lazy { runBlocking { metadata { identifierManager } } }

    companion object {

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

        @OptIn(InternalApi::class)
        private fun doConnect(
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) },
            connectionFactory: ConnectionFactory?,
            config: R2dbcDatabaseConfig,
        ): R2dbcDatabase {
            val options = config.connectionFactoryOptions
            val explicitVendor = config.explicitDialect
                ?.let { if (it is H2Dialect) H2Dialect.dialectName else it.name }
                ?: options.dialectName
            val factory = connectionFactory ?: ConnectionFactories.get(options)

            return R2dbcDatabase(explicitVendor, config) {
                R2dbcConnectionImpl(factory.create(), explicitVendor, R2dbcScope(config.dispatcher), config.typeMapperRegistry)
            }.apply {
                connectionUrl = options.urlString
                CoreTransactionManager.registerDatabaseManager(this, manager(this))
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
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) },
            databaseConfig: R2dbcDatabaseConfig.Builder.() -> Unit = {}
        ): R2dbcDatabase = doConnect(manager, null, R2dbcDatabaseConfig(databaseConfig))

        /**
         * Creates an [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param connectionFactory The [ConnectionFactory] entry point for an R2DBC driver when getting a connection.
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @param databaseConfig Builder of configuration parameters for this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from the [connectionFactory]
         * or from values provided to [databaseConfig].
         */
        fun connect(
            connectionFactory: ConnectionFactory,
            databaseConfig: R2dbcDatabaseConfig = R2dbcDatabaseConfig.invoke(),
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) }
        ): R2dbcDatabase = doConnect(manager, connectionFactory, databaseConfig)

        /**
         * Creates an [R2dbcDatabase] instance.
         *
         * **Note:** This function does not immediately instantiate an actual connection to a database,
         * but instead provides the details necessary to do so whenever a connection is required by a transaction.
         *
         * @param url The URL that represents the database when getting a connection.
         * @param databaseConfig Configuration parameters for this [R2dbcDatabase] instance.
         * @param manager The [TransactionManager] responsible for new transactions that use this [R2dbcDatabase] instance.
         * @throws IllegalStateException If a corresponding database dialect cannot be resolved from the provided [url].
         */
        fun connect(
            url: String,
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) },
            databaseConfig: R2dbcDatabaseConfig.Builder.() -> Unit = {},
        ): R2dbcDatabase {
            val builder = R2dbcDatabaseConfig.Builder()
            builder.setUrl(url)
            databaseConfig(builder)

            return doConnect(manager, null, builder.build())
        }

        /**
         * Creates a [R2dbcDatabase] instance.
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
            manager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) }
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
    }
}

/** Returns the name of the database obtained from its connection URL. */
val R2dbcDatabase.name: String
    get() = url.substringBefore('?').substringAfterLast('/')

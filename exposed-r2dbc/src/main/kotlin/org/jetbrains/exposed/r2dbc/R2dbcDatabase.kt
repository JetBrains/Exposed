package org.jetbrains.exposed.r2dbc

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.statements.api.DatabaseApi
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcConnectionImpl
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcScope
import org.jetbrains.exposed.sql.statements.r2dbc.asInt
import org.jetbrains.exposed.sql.transactions.R2dbcTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import org.reactivestreams.Publisher

/**
 * Class representing the underlying R2DBC database to which connections are made
 * and on which transaction tasks are performed.
 */
class R2dbcDatabase private constructor(
    private val resolvedVendor: String? = null,
    config: DatabaseConfig,
    connector: () -> ExposedConnection<*>
) : DatabaseApi(config, connector) {
    override fun toString(): String =
        "ExposedR2dbcDatabase[${hashCode()}]($resolvedVendor${config.explicitDialect?.let { ", dialect=$it" } ?: ""})"

    override fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T {
        TODO("Not yet implemented")
    }

    override val url: String by lazy { metadata { url } }

    override val vendor: String by lazy {
        resolvedVendor ?: metadata { databaseDialectName }
    }

    override val version by lazy { metadata { version } }

    override val supportsAlterTableWithAddColumn by lazy { metadata { supportsAlterTableWithAddColumn } }

    override val supportsAlterTableWithDropColumn by lazy { metadata { supportsAlterTableWithDropColumn } }

    override val supportsMultipleResultSets by lazy { metadata { supportsMultipleResultSets } }

    override val identifierManager by lazy { metadata { identifierManager } }

    companion object {
        private val r2dbcDialectMapping = mutableMapOf(
            "r2dbc:h2" to H2Dialect.dialectName,
            "r2dbc:postgresql" to PostgreSQLDialect.dialectName,
            "r2dbc:mysql" to MysqlDialect.dialectName,
            "r2dbc:mariadb" to MariaDBDialect.dialectName,
            "r2dbc:oracle" to OracleDialect.dialectName,
            "r2dbc:mssql" to SQLServerDialect.dialectName
        )

        /** Registers a new R2DBC driver, using the specified [dialect], with the identifier [prefix]. */
        fun registerR2dbcDriver(prefix: String, dialect: String) {
            r2dbcDialectMapping[prefix] = dialect
        }

        private fun doConnect(
            explicitVendor: String,
            config: DatabaseConfig?,
            getNewConnection: () -> Publisher<out Connection>,
            dispatcher: CoroutineDispatcher?,
            manager: (R2dbcDatabase) -> TransactionManager = { R2dbcTransactionManager(it) }
        ): R2dbcDatabase {
            return R2dbcDatabase(explicitVendor, config ?: DatabaseConfig.invoke()) {
                R2dbcConnectionImpl(explicitVendor, getNewConnection(), R2dbcScope(dispatcher))
            }.apply {
                TransactionManager.registerManager(this, manager(this))
            }
        }

        /**
         * Creates an [R2dbcDatabase] instance.
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
            manager: (R2dbcDatabase) -> TransactionManager = { R2dbcTransactionManager(it) }
        ): R2dbcDatabase {
            val url = "r2dbc:${connectionOptions.getValue(ConnectionFactoryOptions.DRIVER)}"
            val dialectName = getR2dbcDialectName(url) ?: error("Can't resolve dialect for connection: $url")
            return doConnect(
                explicitVendor = dialectName,
                config = databaseConfig,
                getNewConnection = { ConnectionFactories.get(connectionOptions).create() },
                dispatcher = dispatcher,
                manager = manager
            )
        }

        /**
         * Creates an [R2dbcDatabase] instance.
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
            manager: (R2dbcDatabase) -> TransactionManager = { R2dbcTransactionManager(it) }
        ): R2dbcDatabase {
            return doConnect(
                explicitVendor = databaseDialect.name,
                config = databaseConfig,
                getNewConnection = { connectionFactory.create() },
                dispatcher = dispatcher,
                manager = manager
            )
        }

        /**
         * Creates an [R2dbcDatabase] instance.
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
            manager: (R2dbcDatabase) -> TransactionManager = { R2dbcTransactionManager(it) }
        ): R2dbcDatabase {
            val dialectName = getR2dbcDialectName(url) ?: error("Can't resolve dialect for connection: $url")
            return doConnect(
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

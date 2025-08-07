package org.jetbrains.exposed.v1.r2dbc

import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.DatabaseConfigImpl
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMapping
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcTypeMapping
import org.jetbrains.exposed.v1.r2dbc.statements.asInt

/**
 * A configuration for an [R2dbcDatabase] instance.
 *
 * Parameters set in this class apply to all transactions that use the [R2dbcDatabase] instance,
 * unless an applicable override is specified in an individual transaction block.
 */
interface R2dbcDatabaseConfig : DatabaseConfig {
    /**
     * The [CoroutineDispatcher] to be used when determining the scope of the underlying R2DBC database connection object.
     *
     * Default dispatcher is [Dispatchers.IO].
     */
    val dispatcher: CoroutineDispatcher

    /**
     * The [ConnectionFactoryOptions] state holder that should be associated to a [io.r2dbc.spi.ConnectionFactory]
     * when creating connections.
     *
     * By default, any R2DBC driver-specific [org.postgresql.core.ConnectionFactory] passed to `R2dbcDatabase.connect()`
     * will take priority over this state holder. If a factory is not used directly and this property is not manually
     * configured, the state object will be constructed by parsing the provided R2DBC connection url string.
     */
    val connectionFactoryOptions: ConnectionFactoryOptions

    /**
     * Registry storing all built-in [org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper] classes, as well as any
     * custom mappers implemented and detected by a `ServiceLoader`.
     *
     * By default, the registry includes all Exposed type mappers and any custom loaded classes.
     */
    val typeMapping: R2dbcTypeMapping

    /**
     * The default transaction [IsolationLevel]. If not specified, the database-specific level will be used.
     * This can be overridden on a per-transaction level by specifying the `transactionIsolation` parameter of
     * the [org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction].
     *
     * Check `R2dbcDatabase.getDefaultIsolationLevel()` for the database defaults.
     */
    val defaultR2dbcIsolationLevel: IsolationLevel?

    /**
     * Builder API responsible for constructing a custom [R2dbcDatabase] configuration parameter state.
     */
    class Builder : DatabaseConfig.Builder() {
        /**
         * The [ConnectionFactoryOptions] state holder that should be associated to a [io.r2dbc.spi.ConnectionFactory]
         * when creating connections.
         *
         * By default, any R2DBC driver-specific [org.postgresql.core.ConnectionFactory] passed to `R2dbcDatabase.connect()`
         * will take priority over this state holder. If a factory is not used directly and this property is not manually
         * configured, the state object will be constructed by parsing the provided R2DBC connection url string.
         */
        var connectionFactoryOptions: ConnectionFactoryOptions = ConnectionFactoryOptions.builder().build()

        /**
         * The [CoroutineDispatcher] to be used when determining the scope of the underlying R2DBC database connection object.
         *
         * Default dispatcher is [Dispatchers.IO].
         */
        var dispatcher: CoroutineDispatcher = Dispatchers.IO

        /**
         * Registry storing all built-in [org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper] classes, as well as any
         * custom mappers implemented and detected by a `ServiceLoader`.
         *
         * By default, the registry includes all Exposed type mappers and any custom loaded classes.
         */
        var typeMapping: R2dbcTypeMapping = R2dbcRegistryTypeMapping.default()

        /**
         * The default transaction [IsolationLevel]. If not specified, the database-specific level will be used.
         * This can be overridden on a per-transaction level by specifying the `transactionIsolation` parameter of
         * the [org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction].
         *
         * Check `R2dbcDatabase.getDefaultIsolationLevel()` for the database defaults.
         */
        var defaultR2dbcIsolationLevel: IsolationLevel? = null

        override var defaultIsolationLevel: Int
            get() = defaultR2dbcIsolationLevel?.asInt() ?: -1
            set(value) {
                error("Set a specific io.r2dbc.spi.IsolationLevel value directly to defaultR2dbcIsolationLevel instead")
            }

        /**
         * Parses an R2DBC connection [url] string and uses it to set the [R2dbcDatabaseConfig.connectionFactoryOptions]
         * property. The expected format is `r2dbc:driver[:protocol]://[user:password@]host[:port][/path][?option=value]`.
         */
        fun setUrl(url: String) {
            connectionFactoryOptions { from(ConnectionFactoryOptions.parse(url)) }
        }

        /**
         * Constructs a new [ConnectionFactoryOptions] state holder using a [ConnectionFactoryOptions.Builder] as the
         * block receiver and sets the new object as the value of the [R2dbcDatabaseConfig.connectionFactoryOptions] property.
         */
        fun connectionFactoryOptions(block: ConnectionFactoryOptions.Builder.() -> Unit) {
            val builder = ConnectionFactoryOptions.builder()
            connectionFactoryOptions.let { builder.from(it) }
            builder.apply(block)
            connectionFactoryOptions = builder.build()
        }

        fun build(): R2dbcDatabaseConfig {
            @OptIn(InternalApi::class)
            return R2dbcDatabaseConfigImpl(this)
        }
    }

    companion object {
        operator fun invoke(body: Builder.() -> Unit = {}): Builder = Builder().apply(body)
    }
}

@InternalApi
class R2dbcDatabaseConfigImpl(
    private val builder: R2dbcDatabaseConfig.Builder
) : R2dbcDatabaseConfig, DatabaseConfig by DatabaseConfigImpl(builder) {
    override val dispatcher: CoroutineDispatcher
        get() = builder.dispatcher
    override val connectionFactoryOptions: ConnectionFactoryOptions
        get() = builder.connectionFactoryOptions
    override val typeMapping: R2dbcTypeMapping
        get() = builder.typeMapping
    override val defaultR2dbcIsolationLevel: IsolationLevel?
        get() = builder.defaultR2dbcIsolationLevel
}

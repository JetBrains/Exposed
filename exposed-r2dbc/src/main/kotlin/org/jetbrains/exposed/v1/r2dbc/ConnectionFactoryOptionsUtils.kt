package org.jetbrains.exposed.v1.r2dbc

import io.r2dbc.spi.ConnectionFactoryOptions
import org.jetbrains.exposed.v1.core.vendors.*

/**
 * Returns the exact [VendorDialect] used by this [ConnectionFactoryOptions] state holder.
 *
 * @throws IllegalStateException If [ConnectionFactoryOptions] returns a string value without a `VendorDialect` mapping.
 */
val ConnectionFactoryOptions.dialect: VendorDialect.DialectNameProvider
    get() {
        val dbDialect = getValue(ConnectionFactoryOptions.DRIVER)?.toString()
            ?.takeUnless { it == "pool" }
            ?: getValue(ConnectionFactoryOptions.PROTOCOL)?.toString()?.substringBefore(':')

        return when (dbDialect) {
            "h2" -> H2Dialect
            "postgresql" -> PostgreSQLDialect
            "mysql" -> MysqlDialect
            "mariadb" -> MariaDBDialect
            "oracle" -> OracleDialect
            "mssql" -> SQLServerDialect
            else -> error("Unsupported driver dialect detected: $dbDialect")
        }
    }

/** Returns the string [VendorDialect] name used by this [ConnectionFactoryOptions] state holder. */
val ConnectionFactoryOptions.dialectName: String get() = dialect.dialectName

/** Returns the constructed url connection string used by this [ConnectionFactoryOptions] state holder. */
val ConnectionFactoryOptions.urlString: String get() {
    val driver = getValue(ConnectionFactoryOptions.DRIVER)
    val host = getValue(ConnectionFactoryOptions.HOST)
    val port = getValue(ConnectionFactoryOptions.PORT)
    val database = getValue(ConnectionFactoryOptions.DATABASE)

    return buildString {
        append("r2dbc:$driver://")

        if (host != null) append(host)
        if (port != null) append(":$port")
        if (database != null) append("/$database")
    }
}

/**
 * Returns the optional mode applied to the connection url by this [ConnectionFactoryOptions] state holder.
 * Any dialect other than H2 will always return `null`.
 */
internal val ConnectionFactoryOptions.urlMode: String?
    get() {
        if (getValue(ConnectionFactoryOptions.DRIVER) != "h2") return null

        val database = getValue(ConnectionFactoryOptions.DATABASE)
            ?.toString()
            ?: return H2_INVALID_MODE

        return database
            .substringAfter("MODE=", missingDelimiterValue = "")
            .ifEmpty { null }
            ?.substringBefore(";")
    }

internal const val H2_INVALID_MODE = "H2 Invalid Mode"

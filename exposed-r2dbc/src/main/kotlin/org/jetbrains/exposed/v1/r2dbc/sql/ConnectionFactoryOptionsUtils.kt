package org.jetbrains.exposed.v1.r2dbc.sql

import io.r2dbc.spi.ConnectionFactoryOptions
import org.jetbrains.exposed.v1.sql.vendors.*

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
            else -> error("Unsupported dialect: $dbDialect")
        }
    }

val ConnectionFactoryOptions.dialectName: String get() = dialect.dialectName

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

package org.jetbrains.exposed.v1.jdbc

import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcConnectionImpl
import java.sql.Connection

/**
 * Class responsible for the actual loading when a [Database] instance accesses a connection.
 */
class ExposedConnectionImpl : DatabaseConnectionAutoRegistration {
    override fun invoke(connection: Connection) = JdbcConnectionImpl(connection)
}

package org.jetbrains.exposed.v1.jdbc

import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcConnectionImpl
import java.sql.Connection

/**
 * Class responsible for the actual loading whenever a connection is accessed by a [Database] instance.
 */
class ExposedConnectionImpl : DatabaseConnectionAutoRegistration {
    override fun invoke(connection: Connection) = JdbcConnectionImpl(connection)
}

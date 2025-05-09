package org.jetbrains.exposed.v1.jdbc

import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcConnectionImpl
import java.sql.Connection

class ExposedConnectionImpl : DatabaseConnectionAutoRegistration {
    override fun invoke(connection: Connection) = JdbcConnectionImpl(connection)
}

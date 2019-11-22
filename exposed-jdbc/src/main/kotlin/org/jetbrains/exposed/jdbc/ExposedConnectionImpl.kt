package org.jetbrains.exposed.jdbc

import org.jetbrains.exposed.sql.DatabaseConnectionAutoRegistration
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import java.sql.Connection

class ExposedConnectionImpl : DatabaseConnectionAutoRegistration {
    override fun invoke(connection: Connection) = JdbcConnectionImpl(connection)
}
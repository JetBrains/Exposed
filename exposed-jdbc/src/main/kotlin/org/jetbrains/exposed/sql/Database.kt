package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import java.sql.Connection

@Suppress("unused")
object ExposedConnectionImpl : DatabaseConnectionAutoRegistration {
    override fun invoke(connection: Connection) = JdbcConnectionImpl(connection)
}
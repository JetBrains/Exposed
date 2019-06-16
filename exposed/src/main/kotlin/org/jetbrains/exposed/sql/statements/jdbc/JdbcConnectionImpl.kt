package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import java.sql.Connection

class JdbcConnectionImpl(val connection: Connection) : ExposedConnection {

    override fun commit() {
        connection.commit()
    }

    override fun rollback() {
        connection.rollback()
    }

    override val isClosed get() = connection.isClosed
    override fun close() {
        connection.close()
    }

    override var autoCommit: Boolean
        get() = connection.autoCommit
        set(value) { connection.autoCommit = value }

    override fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T {
        return if (transaction == null) {
            val connection = connector()
            try {
                JdbcDatabaseMetadataImpl(connection.metaData).body()
            } finally {
                connection.close()
            }
        } else
            JdbcDatabaseMetadataImpl(transaction.connection as JdbcConnectionImpl).metaData).body()
    }
}
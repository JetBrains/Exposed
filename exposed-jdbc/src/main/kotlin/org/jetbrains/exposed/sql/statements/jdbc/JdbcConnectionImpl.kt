package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import java.sql.Connection
import java.sql.PreparedStatement

class JdbcConnectionImpl(override val connection: Connection) : ExposedConnection<Connection> {

    // Oracle driver could throw excpection on catalog
    override val catalog: String by lazy {
        try { connection.catalog } catch (_: Exception) { null } ?: connection.metaData.userName ?: ""
    }

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

    override var transactionIsolation: Int
        get() = connection.transactionIsolation
        set(value) { connection.transactionIsolation = value }

    private val metadata by lazy {
        JdbcDatabaseMetadataImpl(catalog, connection.metaData)
    }

    override fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T = metadata.body()

    override fun prepareStatement(sql: String, returnKeys: Boolean) : PreparedStatementApi {
        val generated = if (returnKeys)
            PreparedStatement.RETURN_GENERATED_KEYS
        else
            PreparedStatement.NO_GENERATED_KEYS
        return PreparedStatementImpl(connection.prepareStatement(sql, generated), returnKeys)
    }

    override fun prepareStatement(sql: String, columns: Array<String>): PreparedStatementApi {
        return PreparedStatementImpl(connection.prepareStatement(sql, columns), true)
    }

    override fun setSavepoint(name: String): ExposedSavepoint {
        return JdbcSavepoint(name, connection.setSavepoint(name))
    }

    override fun releaseSavepoint(savepoint: ExposedSavepoint) {
        connection.releaseSavepoint((savepoint as JdbcSavepoint).savepoint)
    }

    override fun rollback(savepoint: ExposedSavepoint) {
        connection.rollback((savepoint as JdbcSavepoint).savepoint)
    }
}
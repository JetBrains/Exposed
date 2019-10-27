package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import java.io.InputStream
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class JdbcPreparedStatementImpl(val statement: PreparedStatement, val wasGeneratedKeysRequested: Boolean) : PreparedStatementApi {
    override val resultSet: ResultSet?
        get() = if (wasGeneratedKeysRequested) statement.generatedKeys else statement.resultSet

    override var fetchSize: Int?
        get() = statement.fetchSize
        set(value) { value?.let { statement.fetchSize = value } }

    override fun addBatch() {
        statement.addBatch()
    }

    override fun executeQuery(): ResultSet = statement.executeQuery()

    override fun executeUpdate(): Int = statement.executeUpdate()

    override fun set(index: Int, value: Any?) {
        statement.setObject(index, value)
    }

    override fun setInputStream(index: Int, inputStream: InputStream?) {
        inputStream?.let {
            statement.setBinaryStream(index, inputStream, inputStream.available())
        } ?: statement.setNull(index, Types.LONGVARBINARY)

    }

    override fun closeIfPossible() {
        if (!statement.isClosed)
            statement.close()
    }

    override fun executeBatch(): List<Int> = statement.executeBatch().toList()
}
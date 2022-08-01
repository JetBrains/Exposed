package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.BinaryColumnType
import org.jetbrains.exposed.sql.BlobColumnType
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.DB2Dialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.db2ResultSetCompat
import java.io.InputStream
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class JdbcPreparedStatementImpl(val statement: PreparedStatement, val wasGeneratedKeysRequested: Boolean) : PreparedStatementApi {

    override val resultSet: ResultSet?
        get() = if (wasGeneratedKeysRequested) {
            // https://stackoverflow.com/questions/41725492/how-to-get-auto-generated-keys-of-batch-insert-statement
            // https://github.com/ebean-orm/ebean/blob/15f1bd310bee6edddbf11dd51feb2f43c4f85528/ebean-core/src/main/java/io/ebeaninternal/server/persist/DB2GetKeys.java
            if (currentDialect is DB2Dialect) {
                statement.db2ResultSetCompat
            } else {
                statement.generatedKeys
            }
        } else statement.resultSet

    override var fetchSize: Int?
        get() = statement.fetchSize
        set(value) { value?.let { statement.fetchSize = value } }

    override fun addBatch() {
        statement.addBatch()
    }

    override fun executeQuery(): ResultSet = statement.executeQuery()

    override fun executeUpdate(): Int = statement.executeUpdate()

    override fun set(index: Int, value: Any) {
        statement.setObject(index, value)
    }

    override fun setNull(index: Int, columnType: IColumnType) {
        if (columnType is BinaryColumnType || columnType is BlobColumnType)
            statement.setNull(index, Types.LONGVARBINARY)
        else
            statement.setObject(index, null)
    }

    override fun setInputStream(index: Int, inputStream: InputStream) {
        statement.setBinaryStream(index, inputStream, inputStream.available())
    }

    override fun closeIfPossible() {
        if (!statement.isClosed)
            statement.close()
    }

    override fun executeBatch(): List<Int> = statement.executeBatch().toList()

    override fun cancel() {
        if (!statement.isClosed)
            statement.cancel()
    }
}

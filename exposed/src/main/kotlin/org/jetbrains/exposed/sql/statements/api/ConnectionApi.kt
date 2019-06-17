package org.jetbrains.exposed.sql.statements.api

class ExposedBlob(val bytes: ByteArray)

interface ExposedConnection<OriginalConnection> {
    val isClosed: Boolean
    fun commit()
    fun rollback()
    fun close()
    var autoCommit: Boolean
    var transactionIsolation: Int
    val connection: OriginalConnection

    fun prepareStatement(sql: String, returnKeys: Boolean) : PreparedStatementApi
    fun prepareStatement(sql: String, columns: Array<String>) : PreparedStatementApi

    val catalog: String

    fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T
}
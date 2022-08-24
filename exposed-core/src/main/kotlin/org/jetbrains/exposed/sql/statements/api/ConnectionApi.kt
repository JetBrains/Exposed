package org.jetbrains.exposed.sql.statements.api

interface ExposedConnection<OriginalConnection : Any> {
    val isClosed: Boolean
    fun commit()
    fun rollback()
    fun close()
    var autoCommit: Boolean
    var readOnly: Boolean
    var transactionIsolation: Int
    val connection: OriginalConnection

    fun prepareStatement(sql: String, returnKeys: Boolean): PreparedStatementApi
    fun prepareStatement(sql: String, columns: Array<String>): PreparedStatementApi
    fun executeInBatch(sqls: List<String>)

    var catalog: String
    var schema: String

    fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T

    fun setSavepoint(name: String): ExposedSavepoint

    fun releaseSavepoint(savepoint: ExposedSavepoint)

    fun rollback(savepoint: ExposedSavepoint)
}

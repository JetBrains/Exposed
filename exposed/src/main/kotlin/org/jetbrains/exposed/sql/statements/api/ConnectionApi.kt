package org.jetbrains.exposed.sql.statements.api

interface ExposedConnection {
    val isClosed: Boolean
    fun commit()
    fun rollback()
    fun close()
    var autoCommit: Boolean

    fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T
}
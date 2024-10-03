package org.jetbrains.exposed.sql.statements.r2dbc

import io.r2dbc.spi.Row
import org.jetbrains.exposed.sql.statements.api.ResultApi

/** Class for wrapping an [io.r2dbc.spi.Row] generated by executing a statement that queries an R2DBC database. */
class R2dbcResult(
    /** The actual [Row] returned by the database after statement execution. */
    override val result: Row
) : ResultApi {
    override fun getObject(index: Int): Any? = result.get(index)

    override fun <T> getObject(index: Int, type: Class<T>): T? = result.get(index, type)

    override fun metadataColumnCount(): Int = result.metadata.columnMetadatas.size

    override fun metadataColumnName(index: Int): String = result.metadata.getColumnMetadata(index).name

    override fun metadataColumnIndex(label: String): Int {
        TODO("Not yet implemented")
    }

    override fun next(): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun releaseResult() {
        TODO("Not yet implemented")
    }
}

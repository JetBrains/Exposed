package org.jetbrains.exposed.sql.statements.r2dbc

import io.r2dbc.spi.Result
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import org.jetbrains.exposed.sql.statements.api.ResultApi
import org.reactivestreams.Publisher

/**
 * Class for wrapping an [io.r2dbc.spi.Row] generated by executing a statement that queries an R2DBC database.
 *
 * @property result The actual [Result] returned by the database after statement execution.
 */
class R2dbcResult(
    val result: Publisher<out Result>
) : ResultApi {
    private var currentRecord: R2dbcRecord? = null

    override fun toString(): String = "R2dbcResult(result = $result)"

    override fun getObject(index: Int): Any? = currentRecord?.row?.get(index)

    override fun <T> getObject(index: Int, type: Class<T>): T? = currentRecord?.row?.get(index, type)

    override fun getObject(name: String): Any? = currentRecord?.row?.get(name)

    override fun <T> getObject(name: String, type: Class<T>): T? = currentRecord?.row?.get(name, type)

    override fun next(): Boolean = currentRecord?.row != null

    override fun close() {
        // do nothing
    }

    override fun releaseResult() {
        // do nothing
    }

    data class R2dbcRecord(val row: Row, val metadata: RowMetadata)
}

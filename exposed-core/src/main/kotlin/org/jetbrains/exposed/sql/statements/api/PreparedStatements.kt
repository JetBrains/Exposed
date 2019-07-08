package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.IColumnType
import java.io.InputStream
import java.sql.ResultSet

interface PreparedStatementApi {

    var fetchSize: Int?

    fun fillParameters(args: Iterable<Pair<IColumnType, Any?>>): Int {
        args.forEachIndexed { index, (c, v) ->
            c.setParameter(this, index + 1, c.valueToDB(v))
        }

        return args.count() + 1
    }

    fun addBatch()

    fun executeQuery() : ResultSet

    fun executeUpdate() : Int

    val resultSet: ResultSet?

    operator fun set(index: Int, value: Any?)

    fun setInputStream(index: Int, inputStream: InputStream?)

    fun closeIfPossible()

    fun executeBatch() : List<Int>
}
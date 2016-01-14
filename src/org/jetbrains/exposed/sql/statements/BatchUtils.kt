package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

fun PreparedStatement.fillParameters(args: Iterable<Pair<ColumnType, Any?>>): Int {
    var index =  1

    for ((c, v) in args) {
        when (v) {
            null -> setObject(index, null)
            else -> setObject(index, c.valueToDB(v))
        }

        index++
    }

    return index
}
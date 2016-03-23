package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.ColumnType
import java.sql.PreparedStatement

fun PreparedStatement.fillParameters(args: Iterable<Pair<ColumnType, Any?>>): Int {

    args.forEachIndexed {index, pair ->
        val (c, v) = pair
        c.setParameter(this, index + 1, c.valueToDB(v))
    }

    return args.count() + 1
}
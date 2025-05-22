package org.example.examples

import org.jetbrains.exposed.v1.transactions.TransactionManager
import java.sql.ResultSet

fun <T : Any> String.execAndMap(transform: (ResultSet) -> T): List<T> {
    val result = mutableListOf<T>()
    TransactionManager.current().exec(this) { rs ->
        while (rs.next()) {
            result += transform(rs)
        }
    }
    return result
}

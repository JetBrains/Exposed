package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll as jdbcSelectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.SELECT_LIMIT_SQL
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.r2dbc.selectAll as r2dbcSelectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

fun selectManyScenario(rowCount: Int): Scenario {
    return Scenario(
        name = "SELECT ~$rowCount rows",
        rawJdbc = {
            Backends.jdbcConnection.prepareStatement(SELECT_LIMIT_SQL).use { ps ->
                ps.setInt(1, rowCount)
                ps.executeQuery().use { rs ->
                    var count = 0
                    while (rs.next()) {
                        rs.getInt(1)
                        rs.getString(2)
                        rs.getInt(3)
                        rs.getString(4)
                        count++
                    }
                    check(count == rowCount)
                }
            }
        },
        exposedJdbc = {
            transaction(Backends.exposedJdbcDb) {
                val rows = Customers.jdbcSelectAll().where { Customers.id lessEq rowCount }.toList()
                check(rows.size == rowCount)
            }
        },
        rawR2dbc = {
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirst()
            try {
                val result = conn.createStatement(SELECT_LIMIT_SQL.replace("?", "$1"))
                    .bind("$1", rowCount)
                    .execute()
                var count = 0
                result.collect { r ->
                    r.map { row, _ ->
                        row.get(0, java.lang.Integer::class.java)
                        row.get(1, java.lang.String::class.java)
                        row.get(2, java.lang.Integer::class.java)
                        row.get(3, java.lang.String::class.java)
                    }.collect { count++ }
                }
                check(count == rowCount)
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            suspendTransaction(Backends.exposedR2dbcDb) {
                val rows = Customers.r2dbcSelectAll().where { Customers.id lessEq rowCount }.toList()
                check(rows.size == rowCount)
            }
        },
    )
}

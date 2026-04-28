package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll as jdbcSelectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.SELECT_BY_PK_SQL
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.r2dbc.selectAll as r2dbcSelectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

fun selectByPkScenario(): Scenario {
    val pk = 500
    val expectedName = "name_$pk"

    return Scenario(
        name = "SELECT 1 row by PK",
        rawJdbc = {
            Backends.jdbcConnection.prepareStatement(SELECT_BY_PK_SQL).use { ps ->
                ps.setInt(1, pk)
                ps.executeQuery().use { rs ->
                    check(rs.next())
                    val name = rs.getString(2)
                    check(name == expectedName)
                }
            }
        },
        exposedJdbc = {
            transaction(Backends.exposedJdbcDb) {
                val rows = Customers.jdbcSelectAll().where { Customers.id eq pk }.toList()
                check(rows.size == 1)
                check(rows[0][Customers.name] == expectedName)
            }
        },
        rawR2dbc = {
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirst()
            try {
                val result = conn.createStatement(SELECT_BY_PK_SQL.replace("?", "$1"))
                    .bind("$1", pk)
                    .execute()
                var found = 0
                var name = ""
                result.collect { r ->
                    r.map { row, _ -> name = row.get(1, java.lang.String::class.java).toString() }.collect { found++ }
                }
                check(found == 1)
                check(name == expectedName)
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            suspendTransaction(Backends.exposedR2dbcDb) {
                val rows = Customers.r2dbcSelectAll().where { Customers.id eq pk }.toList()
                check(rows.size == 1)
                check(rows[0][Customers.name] == expectedName)
            }
        },
    )
}

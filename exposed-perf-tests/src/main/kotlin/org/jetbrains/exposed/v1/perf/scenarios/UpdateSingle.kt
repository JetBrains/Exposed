package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update as jdbcUpdate
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.perf.UPDATE_SQL
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update as r2dbcUpdate

fun updateSingleScenario(): Scenario {
    val pk = 750
    var ageCounter = 100

    fun nextAge(): Int = ageCounter++.also { if (ageCounter > 100_000) ageCounter = 100 }

    return Scenario(
        name = "UPDATE 1 row by PK",
        rawJdbc = {
            val newAge = nextAge()
            Backends.jdbcConnection.prepareStatement(UPDATE_SQL).use { ps ->
                ps.setInt(1, newAge)
                ps.setInt(2, pk)
                check(ps.executeUpdate() == 1)
            }
        },
        exposedJdbc = {
            val newAge = nextAge()
            transaction(Backends.exposedJdbcDb) {
                val updated = Customers.jdbcUpdate({ Customers.id eq pk }) { it[age] = newAge }
                check(updated == 1)
            }
        },
        rawR2dbc = {
            val newAge = nextAge()
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirst()
            try {
                val result = conn.createStatement("UPDATE customers SET age = $1 WHERE id = $2")
                    .bind("$1", newAge)
                    .bind("$2", pk)
                    .execute()
                var updated = 0L
                result.collect { r ->
                    r.rowsUpdated.collect { updated += it }
                }
                check(updated == 1L)
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            val newAge = nextAge()
            suspendTransaction(Backends.exposedR2dbcDb) {
                val updated = Customers.r2dbcUpdate({ Customers.id eq pk }) { it[age] = newAge }
                check(updated == 1)
            }
        },
    )
}

package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.jdbc.insert as jdbcInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.INSERT_SQL
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.r2dbc.insert as r2dbcInsert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.concurrent.atomic.AtomicInteger

fun insertSingleScenario(): Scenario {
    val nextId = AtomicInteger(1_000_000)

    return Scenario(
        name = "INSERT 1 row",
        rawJdbc = {
            val id = nextId.getAndIncrement()
            Backends.jdbcConnection.prepareStatement(INSERT_SQL).use { ps ->
                ps.setInt(1, id)
                ps.setString(2, "name_$id")
                ps.setInt(3, 30)
                ps.setString(4, "u$id@e.com")
                check(ps.executeUpdate() == 1)
            }
        },
        exposedJdbc = {
            val id = nextId.getAndIncrement()
            transaction(Backends.exposedJdbcDb) {
                Customers.jdbcInsert {
                    it[Customers.id] = id
                    it[name] = "name_$id"
                    it[age] = 30
                    it[email] = "u$id@e.com"
                }
            }
        },
        rawR2dbc = {
            val id = nextId.getAndIncrement()
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirst()
            try {
                val result = conn.createStatement("INSERT INTO customers (id, cust_name, age, email) VALUES ($1, $2, $3, $4)")
                    .bind("$1", id)
                    .bind("$2", "name_$id")
                    .bind("$3", 30)
                    .bind("$4", "u$id@e.com")
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
            val id = nextId.getAndIncrement()
            suspendTransaction(Backends.exposedR2dbcDb) {
                Customers.r2dbcInsert {
                    it[Customers.id] = id
                    it[name] = "name_$id"
                    it[age] = 30
                    it[email] = "u$id@e.com"
                }
            }
        },
    )
}

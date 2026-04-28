package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.jdbc.batchInsert as jdbcBatchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.INSERT_SQL
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.r2dbc.batchInsert as r2dbcBatchInsert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.concurrent.atomic.AtomicInteger

private const val BATCH_SIZE = 10

fun insertBatchScenario(): Scenario {
    val nextId = AtomicInteger(2_000_000)

    fun nextBatch(): IntRange {
        val start = nextId.getAndAdd(BATCH_SIZE)
        return start until (start + BATCH_SIZE)
    }

    return Scenario(
        name = "INSERT batch of $BATCH_SIZE rows",
        rawJdbc = {
            val ids = nextBatch()
            Backends.jdbcConnection.prepareStatement(INSERT_SQL).use { ps ->
                for (id in ids) {
                    ps.setInt(1, id)
                    ps.setString(2, "name_$id")
                    ps.setInt(3, 40)
                    ps.setString(4, "u$id@e.com")
                    ps.addBatch()
                }
                val counts = ps.executeBatch()
                check(counts.size == BATCH_SIZE)
            }
        },
        exposedJdbc = {
            val ids = nextBatch().toList()
            transaction(Backends.exposedJdbcDb) {
                Customers.jdbcBatchInsert(ids) { id ->
                    this[Customers.id] = id
                    this[Customers.name] = "name_$id"
                    this[Customers.age] = 40
                    this[Customers.email] = "u$id@e.com"
                }
            }
        },
        rawR2dbc = {
            val ids = nextBatch()
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirst()
            try {
                val stmt = conn.createStatement("INSERT INTO customers (id, cust_name, age, email) VALUES ($1, $2, $3, $4)")
                var first = true
                for (id in ids) {
                    if (!first) stmt.add()
                    stmt.bind("$1", id)
                        .bind("$2", "name_$id")
                        .bind("$3", 40)
                        .bind("$4", "u$id@e.com")
                    first = false
                }
                var updated = 0L
                stmt.execute().collect { r ->
                    r.rowsUpdated.collect { updated += it }
                }
                check(updated == BATCH_SIZE.toLong())
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            val ids = nextBatch().toList()
            suspendTransaction(Backends.exposedR2dbcDb) {
                Customers.r2dbcBatchInsert(ids) { id ->
                    this[Customers.id] = id
                    this[Customers.name] = "name_$id"
                    this[Customers.age] = 40
                    this[Customers.email] = "u$id@e.com"
                }
            }
        },
    )
}

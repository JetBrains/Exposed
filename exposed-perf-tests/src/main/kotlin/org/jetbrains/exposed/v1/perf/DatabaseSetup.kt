package org.jetbrains.exposed.v1.perf

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.sql.Connection
import java.sql.DriverManager

const val JDBC_URL = "jdbc:h2:mem:perfdb;DB_CLOSE_DELAY=-1"
const val R2DBC_URL = "r2dbc:h2:mem:///perfdb"
const val SEED_ROW_COUNT = 1000

object Backends {
    val jdbcConnection: Connection by lazy {
        DriverManager.getConnection(JDBC_URL)
    }
    val exposedJdbcDb: Database by lazy {
        Database.connect(JDBC_URL)
    }
    val r2dbcFactory: ConnectionFactory by lazy {
        ConnectionFactories.get(R2DBC_URL)
    }
    val exposedR2dbcDb: R2dbcDatabase by lazy {
        R2dbcDatabase.connect(R2DBC_URL)
    }
}

fun setUpSchemaAndSeedData() {
    val conn = Backends.jdbcConnection
    conn.createStatement().use { it.execute(DROP_TABLE_SQL) }
    conn.createStatement().use { it.execute(CREATE_TABLE_SQL.trimIndent()) }

    conn.prepareStatement(INSERT_SQL).use { ps ->
        for (i in 1..SEED_ROW_COUNT) {
            ps.setInt(1, i)
            ps.setString(2, "name_$i")
            ps.setInt(3, 20 + (i % 50))
            ps.setString(4, "user$i@example.com")
            ps.addBatch()
        }
        ps.executeBatch()
    }
}

suspend fun verifyR2dbcSeesSeedData(): Int {
    val conn = Backends.r2dbcFactory.create().awaitFirst()
    try {
        val result = conn.createStatement("SELECT COUNT(*) AS c FROM customers").execute()
        var count = 0
        result.collect { r ->
            r.map { row, _ -> (row.get(0) as Number).toInt() }.collect { c ->
                count = c
            }
        }
        return count
    } finally {
        conn.close().awaitFirstOrNull()
    }
}

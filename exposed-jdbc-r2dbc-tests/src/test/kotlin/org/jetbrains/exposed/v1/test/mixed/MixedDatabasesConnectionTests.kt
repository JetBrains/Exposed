package org.jetbrains.exposed.v1.test.mixed

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test

class MixedDatabasesConnectionTests : MixedDatabaseTestsBase() {

    // EXPOSED-894 Jdbc and R2dbc in the same project
    @Test
    fun testConnectionToJdbcAndR2dbcDatabases() {
        val jdbc = jdbcDialect.connect()
        val r2dbc = r2dbcDialect.connect()

        // Check that connection to JDBC and R2DBC databases works
        // and transaction with every of connection could be created without exceptions
        transaction(jdbc) { }
        runBlocking {
            suspendTransaction(r2dbc) { }
        }
    }

    // TODO test with nested transactions
}

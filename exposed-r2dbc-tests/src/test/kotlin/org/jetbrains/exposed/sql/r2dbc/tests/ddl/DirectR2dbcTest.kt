package org.jetbrains.exposed.sql.r2dbc.tests.ddl

import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.junit.Test

// TODO obabichev It's just example of usage R2DBC. It should be removed before merge PR
class DirectR2dbcTest {
    @Test
    fun testListDatabases7585875() = runBlocking {
        val connectionFactory = ConnectionFactories.get("r2dbc:postgresql://root:Exposed_password_1!@127.0.0.1:3004/postgres?lc_messages=en_US.UTF-8")
        val connection = connectionFactory.create().awaitFirst()

        try {
            val statement = connection.createStatement("SELECT datname FROM pg_database")
            val result = statement.execute().awaitFirst()
            val databases = mutableListOf<String>()

            result.map { row, _ ->
                databases.add(row.get("datname", String::class.java)!!)
            }.asFlow().collect()

            println("Found databases via direct R2DBC: $databases")

            // Verify that the list is not empty
            assert(databases.isNotEmpty()) { "No databases found" }

            // Verify presence of essential PostgreSQL system databases
            assert(databases.contains("root")) { "System database 'postgres' not found" }
            assert(databases.contains("postgres")) { "System database 'postgres' not found" }
            assert(databases.contains("template0")) { "System database 'template0' not found" }
            assert(databases.contains("template1")) { "System database 'template1' not found" }

            // Verify the total count is at least the number of system databases
            assert(databases.size >= 4) { "Expected at least 4 databases (system databases), but found ${databases.size}" }
        } finally {
            connection.close().asFlow().collect()
        }
    }
}

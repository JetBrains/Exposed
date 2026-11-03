package org.jetbrains.exposed.v1.r2dbc.sql.tests.h2

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.LogDbInTestName
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class IdentifierManagerDeadlockTests : LogDbInTestName() {
    object Tester : Table("identifier_manager_deadlock") {
        val id = integer("id")
    }

    /**
     * A fresh database's identifier manager is resolved when its first query is rendered.
     * Resolving it must not hold the lazy lock, or block the calling thread, while waiting for a pooled connection:
     * transactions that already hold the pool's connections may themselves be waiting for that lock, or for that thread.
     */
    @Test
    fun testFirstQueryDoesNotDeadlockWhenPoolIsExhausted() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val poolSize = 2
        val url = "r2dbc:h2:mem:///identifierManagerDeadlock;DB_CLOSE_DELAY=-1"

        val setupDb = R2dbcDatabase.connect(url)
        runBlocking { suspendTransaction(setupDb) { SchemaUtils.create(Tester) } }

        val pool = ConnectionPool(
            ConnectionPoolConfiguration.builder(ConnectionFactories.get(url))
                .initialSize(0)
                .maxSize(poolSize)
                .build()
        )
        // a new instance, so its identifier manager is not yet resolved
        val db = R2dbcDatabase.connect(pool, R2dbcDatabaseConfig.Builder().apply { explicitDialect = H2Dialect() })

        // stands in for a server's event loop threads
        val eventLoops = Executors.newFixedThreadPool(poolSize) { Thread(it, "event-loop").apply { isDaemon = true } }
        val scope = CoroutineScope(eventLoops.asCoroutineDispatcher())

        val holdersReady = List(poolSize) { CompletableDeferred<Unit>() }
        val release = CompletableDeferred<Unit>()

        // transactions that check out every pooled connection before their first DSL query
        val holders = holdersReady.map { ready ->
            scope.launch {
                suspendTransaction(db) {
                    exec("SELECT 1")
                    ready.complete(Unit)
                    release.await()
                    Tester.selectAll().toList()
                }
            }
        }

        try {
            val finished = runBlocking {
                withTimeoutOrNull(10.seconds) {
                    holdersReady.awaitAll()

                    // the first DSL query on the database, made while the pool is exhausted
                    val firstQuery = scope.launch {
                        suspendTransaction(db) { Tester.selectAll().toList() }
                    }
                    while (pool.metrics.get().pendingAcquireSize() == 0) {
                        delay(10.milliseconds)
                    }

                    release.complete(Unit)
                    (holders + firstQuery).joinAll()
                }
            }
            assertNotNull(finished, "Transactions deadlocked while resolving R2dbcDatabase.identifierManager")

            TransactionManager.closeAndUnregister(db)
            pool.dispose()
        } finally {
            eventLoops.shutdownNow()
            runBlocking { suspendTransaction(setupDb) { SchemaUtils.drop(Tester) } }
            TransactionManager.closeAndUnregister(setupDb)
        }
    }

    @Test
    fun testIdentifierManagerOfResolvedVendorNeedsNoConnection() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val connectionFactory = object : ConnectionFactory {
            var connectCount = 0

            override fun create(): Publisher<out Connection> {
                connectCount++
                error("No connection should be opened")
            }

            override fun getMetadata(): ConnectionFactoryMetadata = throw NotImplementedError()
        }
        val db = R2dbcDatabase.connect(
            connectionFactory,
            R2dbcDatabaseConfig.Builder().apply { explicitDialect = PostgreSQLDialect() }
        )

        try {
            assertEquals("\"", db.identifierManager.quoteString)
            assertEquals(0, connectionFactory.connectCount)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }
}

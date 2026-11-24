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
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.tests.LogDbInTestName
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DatabaseVersionDeadlockTests : LogDbInTestName() {
    init {
        // H2 keeps the time zone seen when it is first loaded, which other tests expect to be UTC
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    /**
     * The database version is read by code that cannot suspend, such as SQL rendering and row mapping.
     * That read must not block its thread while waiting for a pooled connection:
     * transactions that already hold the pool's connections may need that thread to finish.
     */
    @Test
    fun testVersionReadDoesNotDeadlockWhenPoolIsExhausted() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val poolSize = 2
        val url = "r2dbc:h2:mem:///versionDeadlock;DB_CLOSE_DELAY=-1"

        val pool = ConnectionPool(
            ConnectionPoolConfiguration.builder(ConnectionFactories.get(url))
                .initialSize(0)
                .maxSize(poolSize)
                .build()
        )
        val db = R2dbcDatabase.connect(pool, R2dbcDatabaseConfig.Builder().apply { explicitDialect = H2Dialect() })

        // stands in for a server's event loop threads
        val eventLoops = Executors.newFixedThreadPool(poolSize) { Thread(it, "event-loop").apply { isDaemon = true } }
        val scope = CoroutineScope(eventLoops.asCoroutineDispatcher())

        val holdersReady = List(poolSize) { CompletableDeferred<Unit>() }
        val readersStarted = List(poolSize) { CompletableDeferred<Unit>() }
        val release = CompletableDeferred<Unit>()

        // transactions that check out every pooled connection, then need a thread to finish
        val holders = holdersReady.map { ready ->
            scope.launch {
                suspendTransaction(db) {
                    exec("SELECT 1")
                    ready.complete(Unit)
                    release.await()
                    exec("SELECT 1")
                }
            }
        }

        try {
            val finished = runBlocking {
                withTimeoutOrNull(10.seconds) {
                    holdersReady.awaitAll()

                    // a version read on each thread, made while the pool is exhausted
                    val readers = readersStarted.map { started ->
                        scope.launch {
                            suspendTransaction(db) {
                                started.complete(Unit)
                                assertEquals(2, db.version.major)
                            }
                        }
                    }
                    readersStarted.awaitAll()
                    while (pool.metrics.get().pendingAcquireSize() == 0 && !readers.all { it.isCompleted }) {
                        delay(10.milliseconds)
                    }

                    release.complete(Unit)
                    (holders + readers).joinAll()
                }
            }
            assertNotNull(finished, "Transactions deadlocked while resolving R2dbcDatabase.version")

            TransactionManager.closeAndUnregister(db)
            pool.dispose()
        } finally {
            eventLoops.shutdownNow()
        }
    }

    @Test
    fun testVersionIsResolvedWithFirstConnection() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val connectionFactory = CountingConnectionFactory("r2dbc:h2:mem:///versionResolved;DB_CLOSE_DELAY=-1")
        val db = R2dbcDatabase.connect(connectionFactory, R2dbcDatabaseConfig.Builder().apply { explicitDialect = H2Dialect() })

        try {
            runBlocking {
                // a transaction that runs no statement never needs a connection
                suspendTransaction(db) { }
                assertEquals(0, connectionFactory.connectCount.get())

                suspendTransaction(db) { exec("SELECT 1") }
                assertEquals(1, connectionFactory.connectCount.get())

                // already resolved, so this needs no further connection, even outside a transaction
                assertEquals(2, db.version.major)
                assertTrue(db.fullVersion.startsWith("2."))
                assertEquals(1, connectionFactory.connectCount.get())
            }
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    @Test
    fun testVersionIsResolvedBeforeFirstStatementIsRendered() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val connectionFactory = CountingConnectionFactory("r2dbc:h2:mem:///versionRendered;DB_CLOSE_DELAY=-1")
        val db = R2dbcDatabase.connect(connectionFactory, R2dbcDatabaseConfig.Builder().apply { explicitDialect = H2Dialect() })

        var connectCountWhenRendered: Int? = null
        val versionReadingCondition = object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                connectCountWhenRendered = connectCountWhenRendered ?: connectionFactory.connectCount.get()
                queryBuilder.append("${db.version.major} = 2")
            }
        }

        try {
            runBlocking {
                suspendTransaction(db) { Table.Dual.select(intLiteral(1)).where(versionReadingCondition).toList() }
            }
            assertEquals(1, connectCountWhenRendered)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    private class CountingConnectionFactory(url: String) : ConnectionFactory {
        private val delegate = ConnectionFactories.get(url)
        val connectCount = AtomicInteger()

        override fun create(): Publisher<out Connection> {
            connectCount.incrementAndGet()
            return delegate.create()
        }

        override fun getMetadata(): ConnectionFactoryMetadata = delegate.metadata
    }
}

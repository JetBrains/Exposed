/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class R2dbcConnectionCleanupFailureTest {
    init {
        // Match the default used by R2dbcDatabaseTestsBase for directly opened H2 connections.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    companion object {
        @JvmStatic
        fun databases(): List<TestDB> {
            return TestDB.enabledDialects().filter { it == TestDB.H2_V2 || it == TestDB.POSTGRESQL }
        }
    }

    // Keep these instances intact when coroutine stacktrace recovery is enabled.
    private class TestFailure(val marker: String) : IllegalStateException(marker)

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testBeginFailureKeepsConnectionCloseFailure(testDB: TestDB) {
        runBlocking {
            val primary = TestFailure("begin failed")
            val closeFailure = TestFailure("connection close failed")
            var closes = 0
            withDatabase(testDB, { connection ->
                object : Connection by connection {
                    override fun beginTransaction(): Publisher<Void?> = Mono.error(primary)

                    override fun close(): Publisher<Void?> = Mono.from(connection.close()).then(
                        Mono.defer {
                            closes++
                            Mono.error(closeFailure)
                        }
                    )
                }
            }) { database ->
                val observed = assertFailsWith<TestFailure> { database.connector().getAutoCommit() }
                assertSame(primary, observed)
                assertEquals(listOf(closeFailure), observed.suppressed.toList())
                assertEquals(1, closes)
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testDirectConnectionClosePropagatesItsFailure(testDB: TestDB) {
        runBlocking {
            val closeFailure = TestFailure("connection close failed")
            var closes = 0
            withDatabase(testDB, { connection ->
                object : Connection by connection {
                    override fun close(): Publisher<Void?> = Mono.from(connection.close()).then(
                        Mono.defer {
                            closes++
                            Mono.error(closeFailure)
                        }
                    )
                }
            }) { database ->
                val connection = database.connector()
                connection.getAutoCommit()
                val observed = assertFailsWith<TestFailure> { connection.close() }
                assertSame(closeFailure, observed)
                assertEquals(1, closes)
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testMetadataFailureKeepsConnectionCloseFailure(testDB: TestDB) {
        runBlocking {
            val closeFailure = TestFailure("connection close failed")
            var closes = 0
            withDatabase(testDB, { connection ->
                object : Connection by connection {
                    override fun close(): Publisher<Void?> = Mono.from(connection.close()).then(
                        Mono.defer {
                            closes++
                            Mono.error(closeFailure)
                        }
                    )
                }
            }) { database ->
                // Metadata access currently requires a transaction while obtaining the catalog.
                val observed = assertFailsWith<IllegalStateException> { database.version }
                assertEquals("No transaction in context.", observed.message)
                assertEquals(listOf(closeFailure), observed.suppressed.toList())
                assertEquals(1, closes)
            }
        }
    }

    private suspend fun withDatabase(
        testDB: TestDB,
        decorate: (Connection) -> Connection,
        body: suspend (R2dbcDatabase) -> Unit
    ) {
        val previousDatabase = TransactionManager.defaultDatabase
        val delegate = ConnectionFactories.get(testDB.connection())
        val factory = object : ConnectionFactory {
            override fun getMetadata() = delegate.metadata

            override fun create(): Publisher<out Connection> = Mono.from(delegate.create()).map(decorate)
        }
        val database = R2dbcDatabase.connect(
            factory,
            R2dbcDatabaseConfig.Builder().apply {
                setUrl(testDB.connection())
                explicitDialect = if (testDB == TestDB.H2_V2) H2Dialect() else PostgreSQLDialect()
            }
        )
        try {
            body(database)
        } finally {
            TransactionManager.closeAndUnregister(database)
            TransactionManager.defaultDatabase = previousDatabase
        }
    }
}

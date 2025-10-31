package org.jetbrains.exposed.v1.r2dbc.sql.tests.h2

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.invoke
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getInt
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class MultiDatabaseTest {

    private val db1 by lazy {
        R2dbcDatabase.connect(
            "r2dbc:h2:mem:///db1;USER=root;DB_CLOSE_DELAY=-1;",
            databaseConfig = R2dbcDatabaseConfig {
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )
    }
    private val db2 by lazy {
        R2dbcDatabase.connect(
            "r2dbc:h2:mem:///db2;USER=root;DB_CLOSE_DELAY=-1;",
            databaseConfig = R2dbcDatabaseConfig {
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )
    }
    private var currentDB: R2dbcDatabase? = null

    @Before
    fun before() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        TransactionManager.currentDatabase?.let {
            currentDB = it
        }
    }

    @Test
    fun testTransactionWithDatabase() = runTest {
        suspendTransaction(db1) {
            assertFalse(DMLTestsData.Cities.exists())
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.exists())
            SchemaUtils.drop(DMLTestsData.Cities)
        }

        suspendTransaction(db2) {
            assertFalse(DMLTestsData.Cities.exists())
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.exists())
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    @Test
    fun testSimpleInsertsInDifferentDatabase() = runTest {
        suspendTransaction(db1) {
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.selectAll().empty())
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city1"
            }
        }

        suspendTransaction(db2) {
            assertFalse(DMLTestsData.Cities.exists())
            SchemaUtils.create(DMLTestsData.Cities)
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city2"
            }
        }

        suspendTransaction(db1) {
            assertEquals(1L, DMLTestsData.Cities.selectAll().count())
            assertEquals("city1", DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name])
            SchemaUtils.drop(DMLTestsData.Cities)
        }

        suspendTransaction(db2) {
            assertEquals(1L, DMLTestsData.Cities.selectAll().count())
            assertEquals("city2", DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name])
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabase() = runTest {
        suspendTransaction(db1) {
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.selectAll().empty())
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city1"
            }

            suspendTransaction(db2) {
                assertFalse(DMLTestsData.Cities.exists())
                SchemaUtils.create(DMLTestsData.Cities)
                DMLTestsData.Cities.insert {
                    it[DMLTestsData.Cities.name] = "city2"
                }
                DMLTestsData.Cities.insert {
                    it[DMLTestsData.Cities.name] = "city3"
                }
                assertEquals(2L, DMLTestsData.Cities.selectAll().count())
                assertEquals("city3", DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.name])
                SchemaUtils.drop(DMLTestsData.Cities)
            }

            assertEquals(1L, DMLTestsData.Cities.selectAll().count())
            assertEquals("city1", DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name])
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabaseDepth2() = runTest {
        suspendTransaction(db1) {
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.selectAll().empty())
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city1"
            }

            suspendTransaction(db2) {
                assertFalse(DMLTestsData.Cities.exists())
                SchemaUtils.create(DMLTestsData.Cities)
                DMLTestsData.Cities.insert {
                    it[DMLTestsData.Cities.name] = "city2"
                }
                DMLTestsData.Cities.insert {
                    it[DMLTestsData.Cities.name] = "city3"
                }
                assertEquals(2L, DMLTestsData.Cities.selectAll().count())
                assertEquals("city3", DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.name])

                suspendTransaction(db1) {
                    assertEquals(1L, DMLTestsData.Cities.selectAll().count())
                    DMLTestsData.Cities.insert {
                        it[DMLTestsData.Cities.name] = "city4"
                    }
                    DMLTestsData.Cities.insert {
                        it[DMLTestsData.Cities.name] = "city5"
                    }
                    assertEquals(3L, DMLTestsData.Cities.selectAll().count())
                }

                assertEquals(2L, DMLTestsData.Cities.selectAll().count())
                assertEquals("city3", DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.name])
                SchemaUtils.drop(DMLTestsData.Cities)
            }

            assertEquals(3L, DMLTestsData.Cities.selectAll().count())
            assertEqualLists(listOf("city1", "city4", "city5"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    @Test
    fun testCoroutinesWithMultiDb() = runTest {
        withContext(Dispatchers.IO) {
            suspendTransaction(db1) {
                val trOuterId = this.id
                SchemaUtils.create(DMLTestsData.Cities)
                assertTrue(DMLTestsData.Cities.selectAll().empty())
                DMLTestsData.Cities.insert {
                    it[DMLTestsData.Cities.name] = "city1"
                }

                inTopLevelSuspendTransaction(db2) {
                    assertFalse(this.id == trOuterId)
                    assertFalse(DMLTestsData.Cities.exists())
                    SchemaUtils.create(DMLTestsData.Cities)
                    DMLTestsData.Cities.insert {
                        it[DMLTestsData.Cities.name] = "city2"
                    }
                    DMLTestsData.Cities.insert {
                        it[DMLTestsData.Cities.name] = "city3"
                    }
                    assertEquals(2L, DMLTestsData.Cities.selectAll().count())
                    assertEquals("city3", DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.name])

                    suspendTransaction(db1) {
                        assertTrue(this.id == trOuterId)
                        assertEquals(1L, DMLTestsData.Cities.selectAll().count())
                        DMLTestsData.Cities.insert {
                            it[DMLTestsData.Cities.name] = "city4"
                        }
                        DMLTestsData.Cities.insert {
                            it[DMLTestsData.Cities.name] = "city5"
                        }
                        assertEquals(3L, DMLTestsData.Cities.selectAll().count())
                    }

                    assertEquals(2L, DMLTestsData.Cities.selectAll().count())
                    assertEquals("city3", DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.name])
                    SchemaUtils.drop(DMLTestsData.Cities)
                }

                assertEquals(3L, DMLTestsData.Cities.selectAll().count())
                assertEqualLists(
                    listOf("city1", "city4", "city5"),
                    DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] }
                )
                SchemaUtils.drop(DMLTestsData.Cities)
            }
        }
    }

    @Test
    fun `when default database is not explicitly set - should return the latest connection`() {
        db1
        db2
        assertEquals(TransactionManager.currentDatabase, db2)
    }

    @Test
    fun `when default database is explicitly set - should return the set connection`() {
        db1
        db2
        TransactionManager.defaultDatabase = db1
        assertEquals(TransactionManager.defaultDatabase, db1)
        TransactionManager.defaultDatabase = null
    }

    @Test
    fun `when set default database is removed - should return the latest connection`() {
        db1
        db2
        TransactionManager.defaultDatabase = db1
        TransactionManager.closeAndUnregister(db1)
        assertEquals(TransactionManager.currentDatabase, db2)
        TransactionManager.defaultDatabase = null
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test // this test always fails for one reason or another
    fun `when the default database is changed, coroutines should respect that`() = runTest {
        assertEquals("db1", db1.name) // These two asserts fail sometimes for reasons that escape me
        assertEquals("db2", db2.name) // but if you run just these tests one at a time, they pass.
        val coroutineDispatcher1 = newSingleThreadContext("first")
        TransactionManager.defaultDatabase = db1
        withContext(coroutineDispatcher1) {
            suspendTransaction {
                assertEquals(
                    db1.name,
                    TransactionManager.current().db.name
                ) // when running all tests together, this one usually fails
                TransactionManager.current().exec("SELECT 1") { row ->
                    assertEquals(1, row.getInt(1))
                }
            }
        }
        TransactionManager.defaultDatabase = db2
        withContext(coroutineDispatcher1) {
            suspendTransaction {
                assertEquals(
                    db2.name,
                    TransactionManager.current().db.name
                ) // fails??
                TransactionManager.current().exec("SELECT 1") { row ->
                    assertEquals(1, row.getInt(1))
                }
            }
        }
        TransactionManager.defaultDatabase = null
    }

    @Test // If the first two assertions pass, the entire test passes
    fun `when the default database is changed, threads should respect that`() = runTest {
        assertEquals("db1", db1.name)
        assertEquals("db2", db2.name)
        val threadpool = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        TransactionManager.defaultDatabase = db1
        threadpool.invoke {
            suspendTransaction {
                assertEquals(db1.name, TransactionManager.current().db.name)
                TransactionManager.current().exec("SELECT 1") { row ->
                    assertEquals(1, row.getInt(1))
                }
            }
        }
        TransactionManager.defaultDatabase = db2
        threadpool.invoke {
            suspendTransaction {
                assertEquals(db2.name, TransactionManager.current().db.name)
                TransactionManager.current().exec("SELECT 1") { row ->
                    assertEquals(1, row.getInt(1))
                }
            }
        }
        TransactionManager.defaultDatabase = null
    }
}

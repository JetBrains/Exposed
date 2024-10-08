package org.jetbrains.exposed.sql.tests.h2

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.DatabaseApi
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.withSuspendTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class MultiDatabaseTest {

    private val db1 by lazy {
        Database.connect(
            "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "",
            databaseConfig = DatabaseConfig {
                defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
            }
        )
    }
    private val db2 by lazy {
        Database.connect(
            "jdbc:h2:mem:db2;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "",
            databaseConfig = DatabaseConfig {
                defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
            }
        )
    }
    private var currentDB: DatabaseApi? = null

    @Before
    fun before() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        if (TransactionManager.isInitialized()) {
            currentDB = TransactionManager.currentOrNull()?.db
        }
    }

    @After
    fun after() {
        TransactionManager.resetCurrent(currentDB?.transactionManager)
    }

    @Test
    fun testTransactionWithDatabase() {
        transaction(db1) {
            assertFalse(DMLTestsData.Cities.exists())
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.exists())
            SchemaUtils.drop(DMLTestsData.Cities)
        }

        transaction(db2) {
            assertFalse(DMLTestsData.Cities.exists())
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.exists())
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    @Test
    fun testSimpleInsertsInDifferentDatabase() {
        transaction(db1) {
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.selectAll().empty())
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city1"
            }
        }

        transaction(db2) {
            assertFalse(DMLTestsData.Cities.exists())
            SchemaUtils.create(DMLTestsData.Cities)
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city2"
            }
        }

        transaction(db1) {
            assertEquals(1L, DMLTestsData.Cities.selectAll().count())
            assertEquals("city1", DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name])
            SchemaUtils.drop(DMLTestsData.Cities)
        }

        transaction(db2) {
            assertEquals(1L, DMLTestsData.Cities.selectAll().count())
            assertEquals("city2", DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name])
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabase() {
        transaction(db1) {
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.selectAll().empty())
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city1"
            }

            transaction(db2) {
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
    fun testEmbeddedInsertsInDifferentDatabaseDepth2() {
        transaction(db1) {
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.selectAll().empty())
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city1"
            }

            transaction(db2) {
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

                transaction(db1) {
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
    fun testCoroutinesWithMultiDb() = runBlocking {
        newSuspendedTransaction(Dispatchers.IO, db1) {
            val tr1 = this
            SchemaUtils.create(DMLTestsData.Cities)
            assertTrue(DMLTestsData.Cities.selectAll().empty())
            DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "city1"
            }

            newSuspendedTransaction(Dispatchers.IO, db2) {
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

                tr1.withSuspendTransaction {
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
    fun `when default database is not explicitly set - should return the latest connection`() {
        db1
        db2
        assertEquals(TransactionManager.defaultDatabase, db2)
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
        assertEquals(TransactionManager.defaultDatabase, db2)
        TransactionManager.defaultDatabase = null
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test // this test always fails for one reason or another
    fun `when the default database is changed, coroutines should respect that`(): Unit = runBlocking {
        assertEquals("jdbc:h2:mem:db1", db1.name) // These two asserts fail sometimes for reasons that escape me
        assertEquals("jdbc:h2:mem:db2", db2.name) // but if you run just these tests one at a time, they pass.
        val coroutineDispatcher1 = newSingleThreadContext("first")
        TransactionManager.defaultDatabase = db1
        newSuspendedTransaction(coroutineDispatcher1) {
            assertEquals(db1.name, TransactionManager.current().db.name) // when running all tests together, this one usually fails
            TransactionManager.current().exec("SELECT 1") { rs ->
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
        }
        TransactionManager.defaultDatabase = db2
        newSuspendedTransaction(coroutineDispatcher1) {
            assertEquals(db2.name, TransactionManager.current().db.name) // fails??
            TransactionManager.current().exec("SELECT 1") { rs ->
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
        }
        TransactionManager.defaultDatabase = null
    }

    @Test // If the first two assertions pass, the entire test passes
    fun `when the default database is changed, threads should respect that`() {
        assertEquals("jdbc:h2:mem:db1", db1.name)
        assertEquals("jdbc:h2:mem:db2", db2.name)
        val threadpool = Executors.newSingleThreadExecutor()
        TransactionManager.defaultDatabase = db1
        threadpool.submit {
            transaction {
                assertEquals(db1.name, TransactionManager.current().db.name)
                TransactionManager.current().exec("SELECT 1") { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
            .get()
        TransactionManager.defaultDatabase = db2
        threadpool.submit {
            transaction {
                assertEquals(db2.name, TransactionManager.current().db.name)
                TransactionManager.current().exec("SELECT 1") { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }.get()
        TransactionManager.defaultDatabase = null
    }
}

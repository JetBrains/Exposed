package org.jetbrains.exposed.sql.tests.h2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.dml.Cities
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class MultiDatabaseTest {

    private val db1 by lazy {
        Database.connect("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "", databaseConfig = DatabaseConfig {
            defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
        })
    }
    private val db2 by lazy {
        Database.connect("jdbc:h2:mem:db2;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "", databaseConfig = DatabaseConfig {
            defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
        })
    }
    private var currentDB: Database? = null

    @Before
    fun before() {
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
            assertFalse(Cities.exists())
            SchemaUtils.create(Cities)
            assertTrue(Cities.exists())
            SchemaUtils.drop(Cities)
        }

        transaction(db2) {
            assertFalse(Cities.exists())
            SchemaUtils.create(Cities)
            assertTrue(Cities.exists())
            SchemaUtils.drop(Cities)
        }
    }

    @Test
    fun testSimpleInsertsInDifferentDatabase() {
        transaction(db1) {
            SchemaUtils.create(Cities)
            assertTrue(Cities.selectAll().empty())
            Cities.insert { it[Cities.name] = "city1" }
        }

        transaction(db2) {
            assertFalse(Cities.exists())
            SchemaUtils.create(Cities)
            Cities.insert {
                it[Cities.name] = "city2"
            }
        }

        transaction(db1) {
            assertEquals(1L, Cities.selectAll().count())
            assertEquals("city1", Cities.selectAll().single()[Cities.name])
            SchemaUtils.drop(Cities)
        }

        transaction(db2) {
            assertEquals(1L, Cities.selectAll().count())
            assertEquals("city2", Cities.selectAll().single()[Cities.name])
            SchemaUtils.drop(Cities)
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabase() {
        transaction(db1) {
            SchemaUtils.create(Cities)
            assertTrue(Cities.selectAll().empty())
            Cities.insert { it[name] = "city1" }

            transaction(db2) {
                assertFalse(Cities.exists())
                SchemaUtils.create(Cities)
                Cities.insert { it[name] = "city2" }
                Cities.insert { it[name] = "city3" }
                assertEquals(2L, Cities.selectAll().count())
                assertEquals("city3", Cities.selectAll().last()[Cities.name])
                SchemaUtils.drop(Cities)
            }

            assertEquals(1L, Cities.selectAll().count())
            assertEquals("city1", Cities.selectAll().single()[Cities.name])
            SchemaUtils.drop(Cities)
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabaseDepth2() {
        transaction(db1) {
            SchemaUtils.create(Cities)
            assertTrue(Cities.selectAll().empty())
            Cities.insert { it[name] = "city1" }

            transaction(db2) {
                assertFalse(Cities.exists())
                SchemaUtils.create(Cities)
                Cities.insert { it[name] = "city2" }
                Cities.insert { it[name] = "city3" }
                assertEquals(2L, Cities.selectAll().count())
                assertEquals("city3", Cities.selectAll().last()[Cities.name])

                transaction(db1) {
                    assertEquals(1L, Cities.selectAll().count())
                    Cities.insert { it[name] = "city4" }
                    Cities.insert { it[name] = "city5" }
                    assertEquals(3L, Cities.selectAll().count())
                }

                assertEquals(2L, Cities.selectAll().count())
                assertEquals("city3", Cities.selectAll().last()[Cities.name])
                SchemaUtils.drop(Cities)
            }

            assertEquals(3L, Cities.selectAll().count())
            assertEqualLists(listOf("city1", "city4", "city5"), Cities.selectAll().map { it[Cities.name] })
            SchemaUtils.drop(Cities)
        }
    }

    @Test
    fun testCoroutinesWithMultiDb() = runBlocking {
        newSuspendedTransaction(Dispatchers.IO, db1) {
            val tr1 = this
            SchemaUtils.create(Cities)
            assertTrue(Cities.selectAll().empty())
            Cities.insert { it[name] = "city1" }

            newSuspendedTransaction(Dispatchers.IO, db2) {
                assertFalse(Cities.exists())
                SchemaUtils.create(Cities)
                Cities.insert { it[name] = "city2" }
                Cities.insert { it[name] = "city3" }
                assertEquals(2L, Cities.selectAll().count())
                assertEquals("city3", Cities.selectAll().last()[Cities.name])

                tr1.suspendedTransaction {
                    assertEquals(1L, Cities.selectAll().count())
                    Cities.insert { it[name] = "city4" }
                    Cities.insert { it[name] = "city5" }
                    assertEquals(3L, Cities.selectAll().count())
                }

                assertEquals(2L, Cities.selectAll().count())
                assertEquals("city3", Cities.selectAll().last()[Cities.name])
                SchemaUtils.drop(Cities)
            }

            assertEquals(3L, Cities.selectAll().count())
            assertEqualLists(listOf("city1", "city4", "city5"), Cities.selectAll().map { it[Cities.name] })
            SchemaUtils.drop(Cities)
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

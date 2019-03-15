package org.jetbrains.exposed.sql.tests.h2

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.shared.DMLTestsData
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiDatabaseTest {

    private val db1 by lazy { Database.connect("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")}
    private val db2 by lazy { Database.connect("jdbc:h2:mem:db2;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "")}
    private var currentDB : Database? = null

    @Before
    fun before() {
        if (TransactionManager.isInitialized()) {
            currentDB = TransactionManager.currentOrNull()?.db
        }
    }

    @After
    fun after() {
        TransactionManager.resetCurrent(currentDB?.let { TransactionManager.managerFor(it) })
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
            assertEquals(1, DMLTestsData.Cities.selectAll().count())
            assertEquals("city1", DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name])
            SchemaUtils.drop(DMLTestsData.Cities)
        }

        transaction(db2) {
            assertEquals(1, DMLTestsData.Cities.selectAll().count())
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
                assertEquals(2, DMLTestsData.Cities.selectAll().count())
                assertEquals("city3", DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.name])
                SchemaUtils.drop(DMLTestsData.Cities)
            }

            assertEquals(1, DMLTestsData.Cities.selectAll().count())
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
                assertEquals(2, DMLTestsData.Cities.selectAll().count())
                assertEquals("city3", DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.name])

                transaction(db1) {
                    assertEquals(1, DMLTestsData.Cities.selectAll().count())
                    DMLTestsData.Cities.insert {
                        it[DMLTestsData.Cities.name] = "city4"
                    }
                    DMLTestsData.Cities.insert {
                        it[DMLTestsData.Cities.name] = "city5"
                    }
                    assertEquals(3, DMLTestsData.Cities.selectAll().count())
                }

                assertEquals(2, DMLTestsData.Cities.selectAll().count())
                assertEquals("city3", DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.name])
                SchemaUtils.drop(DMLTestsData.Cities)
            }

            assertEquals(3, DMLTestsData.Cities.selectAll().count())
            assertEqualLists(listOf("city1", "city4", "city5"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name]})
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }
}
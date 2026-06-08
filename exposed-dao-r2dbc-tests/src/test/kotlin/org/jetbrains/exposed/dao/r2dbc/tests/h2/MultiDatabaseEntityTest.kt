package org.jetbrains.exposed.dao.r2dbc.tests.h2

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.flow.all
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.r2dbc.tests.shared.EntityTestsData
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import kotlin.properties.Delegates
import kotlin.test.Test

class MultiDatabaseEntityTest : R2dbcDatabaseTestsBase() {
    private val db1 by lazy {
        R2dbcDatabase.connect(
            "r2dbc:h2:mem:///db1;DB_CLOSE_DELAY=-1;", user = "root", password = "",
            databaseConfig = R2dbcDatabaseConfig {
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )
    }
    private val db2 by lazy {
        R2dbcDatabase.connect(
            "r2dbc:h2:mem:///db2;DB_CLOSE_DELAY=-1;", user = "root", password = "",
            databaseConfig = R2dbcDatabaseConfig {
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )
    }
    private var currentDB: R2dbcDatabase? = null

    @BeforeEach
    fun before() = runBlocking {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        TransactionManager.currentOrNull()?.let {
            currentDB = it.db
        }
        suspendTransaction(db1) {
            SchemaUtils.create(EntityTestsData.XTable, EntityTestsData.YTable)
        }
        suspendTransaction(db2) {
            SchemaUtils.create(EntityTestsData.XTable, EntityTestsData.YTable)
        }
    }

    @AfterEach
    fun after() = runBlocking {
        if (TestDB.H2_V2 in TestDB.enabledDialects()) {
            suspendTransaction(db1) {
                SchemaUtils.drop(EntityTestsData.XTable, EntityTestsData.YTable)
            }
            suspendTransaction(db2) {
                SchemaUtils.drop(EntityTestsData.XTable, EntityTestsData.YTable)
            }
        }
    }

    @Test
    fun testSimpleCreateEntitiesInDifferentDatabase() = runTest {
        suspendTransaction(db1) {
            EntityTestsData.XEntity.new {
                this.b1 = true
            }.flush()
        }

        suspendTransaction(db2) {
            EntityTestsData.XEntity.new {
                this.b1 = false
            }.flush()

            EntityTestsData.XEntity.new {
                this.b1 = false
            }.flush()
        }

        suspendTransaction(db1) {
            assertEquals(1L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().single().b1)
        }

        suspendTransaction(db2) {
            assertEquals(2L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().all { !it.b1 })
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabase() = runTest {
        suspendTransaction(db1) {
            EntityTestsData.XEntity.new {
                this.b1 = true
            }.flush()

            assertEquals(1L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().single().b1)

            suspendTransaction(db2) {
                assertEquals(0L, EntityTestsData.XEntity.all().count())
                EntityTestsData.XEntity.new {
                    this.b1 = false
                }.flush()

                EntityTestsData.XEntity.new {
                    this.b1 = false
                }.flush()
                assertEquals(2L, EntityTestsData.XEntity.all().count())
                assertEquals(true, EntityTestsData.XEntity.all().all { !it.b1 })
            }

            assertEquals(1L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().single().b1)
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabaseDepth2() = runTest {
        suspendTransaction(db1) {
            EntityTestsData.XEntity.new {
                this.b1 = true
            }.flush()

            assertEquals(1L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().single().b1)

            suspendTransaction(db2) {
                assertEquals(0L, EntityTestsData.XEntity.all().count())
                EntityTestsData.XEntity.new {
                    this.b1 = false
                }.flush()

                EntityTestsData.XEntity.new {
                    this.b1 = false
                }.flush()
                assertEquals(2L, EntityTestsData.XEntity.all().count())
                assertEquals(true, EntityTestsData.XEntity.all().all { !it.b1 })

                suspendTransaction(db1) {
                    EntityTestsData.XEntity.new {
                        this.b1 = true
                    }.flush()

                    EntityTestsData.XEntity.new {
                        this.b1 = false
                    }.flush()
                    assertEquals(3L, EntityTestsData.XEntity.all().count())
                }
                assertEquals(2L, EntityTestsData.XEntity.all().count())
            }

            assertEquals(3L, EntityTestsData.XEntity.all().count())
            assertEqualLists(listOf(true, true, false), EntityTestsData.XEntity.all().map { it.b1 }.toList())
        }
    }

    @Test
    fun crossReferencesAllowedForEntitiesFromSameDatabase() = runTest {
        var db1b1 by Delegates.notNull<EntityTestsData.BEntity>()
        var db2b1 by Delegates.notNull<EntityTestsData.BEntity>()
        var db1y1 by Delegates.notNull<EntityTestsData.YEntity>()
        var db2y1 by Delegates.notNull<EntityTestsData.YEntity>()
        suspendTransaction(db1) {
            db1b1 = EntityTestsData.BEntity.new(1) { }.flush()

            suspendTransaction(db2) {
                assertEquals(0L, EntityTestsData.BEntity.count())
                db2b1 = EntityTestsData.BEntity.new(2) { }.flush()
                db2y1 = EntityTestsData.YEntity.new("2") { }.flush()
                db2b1.y.set(db2y1)
            }
            assertEquals(1L, EntityTestsData.BEntity.count())
            assertNotNull(EntityTestsData.BEntity[1])

            db1y1 = EntityTestsData.YEntity.new("1") { }.flush()
            db1b1.y.set(db1y1)

            commit()

            suspendTransaction(db2) {
                assertNull(EntityTestsData.BEntity.testCache(EntityID(2, EntityTestsData.BEntity.table)))
                val b2Reread = EntityTestsData.BEntity.all().single()
                assertEquals(db2b1.id, b2Reread.id)
                assertEquals(db2y1.id, b2Reread.y()?.id)
                b2Reread.y.set(null)
            }
        }
        inTopLevelSuspendTransaction(db1, IsolationLevel.READ_COMMITTED) {
            maxAttempts = 1
            assertNull(EntityTestsData.BEntity.testCache(db1b1.id))
            val b1Reread = EntityTestsData.BEntity[db1b1.id]
            assertEquals(db1b1.id, b1Reread.id)
            assertEquals(db1y1.id, EntityTestsData.YEntity[db1y1.id].id)
            assertEquals(db1y1.id, b1Reread.y()?.id)
        }
    }

    @Test
    fun crossReferencesProhibitedForEntitiesFromDifferentDB() = runTest {
        Assertions.assertThrows(IllegalStateException::class.java) {
            runBlocking {
                suspendTransaction(db1) {
                    val db1b1 = EntityTestsData.BEntity.new(1) { }.flush()

                    suspendTransaction(db2) {
                        assertEquals(0L, EntityTestsData.BEntity.count())
                        db1b1.y.set(EntityTestsData.YEntity.new("2") { }.flush())
                    }
                }
            }
        }
    }
}

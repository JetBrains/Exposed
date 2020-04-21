package org.jetbrains.exposed.sql.tests.h2

import org.jetbrains.exposed.dao.DaoTransactionManager
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.inTopLevelTransaction
import org.jetbrains.exposed.dao.transaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData
import org.jetbrains.exposed.sql.transactionManager
import org.jetbrains.exposed.sql.transactions.DEFAULT_ISOLATION_LEVEL
import org.jetbrains.exposed.sql.transactions.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.transactions.ITransactionManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MultiDatabaseEntityTest {

    private val db1 by lazy { Database.connect("jdbc:h2:/Users/jeffw/git/Exposed/build/db1;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE;", "org.h2.Driver", "root", "", manager = { DaoTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) })}
    private val db2 by lazy { Database.connect("jdbc:h2:/Users/jeffw/git/Exposed/build/db2;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE;", "org.h2.Driver", "root", "", manager = { DaoTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) })}
    private var currentDB : Database? = null

    @Before
    fun before() {
        if (ITransactionManager.isInitialized()) {
            currentDB = ITransactionManager.currentOrNull()?.db
        }
        transaction(db1) {
            SchemaUtils.create(EntityTestsData.XTable, EntityTestsData.YTable)
        }
        transaction(db2) {
            SchemaUtils.create(EntityTestsData.XTable, EntityTestsData.YTable)
        }
    }

    @After
    fun after() {
        ITransactionManager.resetCurrent(currentDB.transactionManager)
        transaction(db1) {
            SchemaUtils.drop(EntityTestsData.XTable, EntityTestsData.YTable)
        }
        transaction(db2) {
            SchemaUtils.drop(EntityTestsData.XTable, EntityTestsData.YTable)
        }
    }


    @Test
    fun testSimpleCreateEntitiesInDifferentDatabase() {
        transaction(db1) {
            EntityTestsData.XEntity.new {
                this.b1 = true
            }
        }

        transaction(db2) {
            EntityTestsData.XEntity.new {
                this.b1 = false
            }

            EntityTestsData.XEntity.new {
                this.b1 = false
            }
        }

        transaction(db1) {
            assertEquals(1L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().single().b1)
        }

        transaction(db2) {
            assertEquals(2L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().all { !it.b1 })
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabase() {
        transaction(db1) {
            EntityTestsData.XEntity.new {
                this.b1 = true
            }

            assertEquals(1L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().single().b1)

            transaction(db2) {
                assertEquals(0L, EntityTestsData.XEntity.all().count())
                EntityTestsData.XEntity.new {
                    this.b1 = false
                }

                EntityTestsData.XEntity.new {
                    this.b1 = false
                }
                assertEquals(2L, EntityTestsData.XEntity.all().count())
                assertEquals(true, EntityTestsData.XEntity.all().all { !it.b1 })
            }

            assertEquals(1L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().single().b1)
        }
    }

    @Test
    fun testEmbeddedInsertsInDifferentDatabaseDepth2() {
        transaction(db1) {
            EntityTestsData.XEntity.new {
                this.b1 = true
            }

            assertEquals(1L, EntityTestsData.XEntity.all().count())
            assertEquals(true, EntityTestsData.XEntity.all().single().b1)

            transaction(db2) {
                assertEquals(0L, EntityTestsData.XEntity.all().count())
                EntityTestsData.XEntity.new {
                    this.b1 = false
                }

                EntityTestsData.XEntity.new {
                    this.b1 = false
                }
                assertEquals(2L, EntityTestsData.XEntity.all().count())
                assertEquals(true, EntityTestsData.XEntity.all().all { !it.b1 })

                transaction(db1) {
                    EntityTestsData.XEntity.new {
                        this.b1 = true
                    }

                    EntityTestsData.XEntity.new {
                        this.b1 = false
                    }
                    assertEquals(3L, EntityTestsData.XEntity.all().count())
                }
                assertEquals(2L, EntityTestsData.XEntity.all().count())
            }

            assertEquals(3L, EntityTestsData.XEntity.all().count())
            assertEqualLists(listOf(true, true, false), EntityTestsData.XEntity.all().map { it.b1 })
        }
    }

    @Test
    fun crossReferencesAllowedForEntitiesFromSameDatabase() {
        var db1b1 by Delegates.notNull<EntityTestsData.BEntity>()
        var db2b1 by Delegates.notNull<EntityTestsData.BEntity>()
        var db1y1 by Delegates.notNull<EntityTestsData.YEntity>()
        var db2y1 by Delegates.notNull<EntityTestsData.YEntity>()
        transaction(db1) {
            db1b1 = EntityTestsData.BEntity.new(1) {  }
            
            transaction(db2) {
                assertEquals(0L, EntityTestsData.BEntity.count())
                db2b1 = EntityTestsData.BEntity.new(2) {  }
                db2y1 = EntityTestsData.YEntity.new("2") { }
                db2b1.y = db2y1
            }
            assertEquals(1L, EntityTestsData.BEntity.count())
            assertNotNull(EntityTestsData.BEntity[1])

            db1y1 = EntityTestsData.YEntity.new("1") { }
            db1b1.y = db1y1

            commit()

            transaction(db2) {
                assertNull(EntityTestsData.BEntity.testCache(EntityID(2, EntityTestsData.BEntity.table)))
                val b2Reread = EntityTestsData.BEntity.all().single()
                assertEquals(db2b1.id, b2Reread.id)
                assertEquals(db2y1.id, b2Reread.y?.id)
                b2Reread.y = null

            }
        }
        inTopLevelTransaction(Connection.TRANSACTION_READ_COMMITTED, 1, db1) {
            assertNull(EntityTestsData.BEntity.testCache(db1b1.id))
            val b1Reread = EntityTestsData.BEntity[db1b1.id]
            assertEquals(db1b1.id, b1Reread.id)
            assertEquals(db1y1.id, EntityTestsData.YEntity[db1y1.id].id)
            assertEquals(db1y1.id, b1Reread.y?.id)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun crossReferencesProhibitedForEntitiesFromDifferentDB() {
        transaction(db1) {
            val db1b1 = EntityTestsData.BEntity.new(1) {  }

            transaction(db2) {
                assertEquals(0L, EntityTestsData.BEntity.count())
                db1b1.y = EntityTestsData.YEntity.new("2") { }
            }
        }
    }
}
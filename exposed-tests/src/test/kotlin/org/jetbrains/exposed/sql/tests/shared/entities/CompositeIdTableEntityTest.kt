package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentTestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompositeIdTableEntityTest : DatabaseTestsBase() {
    object Publishers : CompositeIdTable("publishers") {
        val pubId = uuid("pub_id").autoGenerate()
        val isbnCode = integer("isbn_code").autoIncrement()
        val name = varchar("publisher_name", 32)

        override val id = compositeEntityId(pubId, isbnCode)
        override val primaryKey = PrimaryKey(pubId, isbnCode)
    }

    class Publisher(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Publisher>(Publishers)

        var name by Publishers.name
    }

    // SQLite excluded as only allows auto-increment on single column PKs
    // SQL Server doesn't allow inserting explicit value for identity columns
    @Test
    fun testCreateAndDrop() {
        withDb(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER)) {
            try {
                SchemaUtils.create(Publishers)
                assertTrue(Publishers.exists())
                assertTrue(SchemaUtils.statementsRequiredToActualizeScheme(Publishers).isEmpty())
            } finally {
                SchemaUtils.drop(Publishers)
            }
        }
    }

    @Test
    fun testInsertAndSelectUsingDSL() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            Publishers.insert {
                it[name] = "Publisher A"
            }

            val result = Publishers.selectAll().single()
            assertEquals("Publisher A", result[Publishers.name])
        }
    }

    @Test
    fun testInsertAndSelectUsingDAO() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            Publisher.new {
                name = "Publisher A"
            }

            val result = Publisher.all().single()
            assertEquals("Publisher A", result.name)
            assertEquals(1, result.id.value[Publishers.isbnCode])
        }
    }

    @Test
    fun testInsertAndSelectUsingMixedDSLAndDAO() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            Publishers.insert {
                it[name] = "Publisher A"
            }
            Publisher.new {
                name = "Publisher B"
            }

            assertEquals(2, Publisher.all().count())
            assertEquals(2, Publishers.selectAll().count())
        }
    }

    @Test
    fun testInsertAndGetCompositeIds() {
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER), Publishers) {
            val id1 = Publishers.insertAndGetId {
                it[pubId] = UUID.randomUUID()
                it[isbnCode] = 725
                it[name] = "Publisher A"
            }
            assertEquals(725, id1.value[Publishers.isbnCode])

            val id2 = Publishers.insertAndGetId {
                it[name] = "Publisher B"
            }
            val expectedNextVal = if (currentTestDB in TestDB.mySqlRelatedDB) 726 else 1
            assertEquals(expectedNextVal, id2.value[Publishers.isbnCode])
        }
    }

    @Test
    fun testFindById() {
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER), Publishers) {
            val id1 = Publishers.insertAndGetId {
                it[pubId] = UUID.randomUUID()
                it[isbnCode] = 725
                it[name] = "Publisher A"
            }

            val p1 = Publisher.findById(id1)
            assertNotNull(p1)
            assertEquals(725, p1.id.value[Publishers.isbnCode])

            val id2 = Publisher.new {
                name = "Publisher B"
            }.id

            val p2 = Publisher.findById(id2)
            assertNotNull(p2)
            assertEquals("Publisher B", p2.name)
        }
    }

    @Test
    fun testFind() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val id1 = Publisher.new {
                name = "Publisher A"
            }.id

            assertEquals(id1, Publisher.find { Publishers.name like "% A" }.single().id)

            val result = Publisher.find { Publishers.id eq id1 }.single()
            assertEquals("Publisher A", result.name)
        }
    }

    @Test
    fun testUpdate() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }

            p1.name = "Publisher B"

            assertEquals("Publisher B", Publisher.all().single().name)
        }
    }
}

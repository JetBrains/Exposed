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
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// SQLite excluded from all tests as it only allows auto-increment on single column PKs.
// SQL Server is sometimes excluded because it doesn't allow inserting explicit values for identity columns.
class CompositeIdTableEntityTest : DatabaseTestsBase() {
    object Publishers : CompositeIdTable("publishers") {
        val pubId = uuid("pub_id").autoGenerate().compositeEntityId()
        val isbnCode = integer("isbn_code").autoIncrement().compositeEntityId()
        val name = varchar("publisher_name", 32)

        override val primaryKey = PrimaryKey(pubId, isbnCode)
    }

    class Publisher(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Publisher>(Publishers)

        var name by Publishers.name
    }

    @Test
    fun testCreateAndDropCompositeIdTable() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
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
    fun testInsertAndSelectUsingDAO() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }.id

            val result1 = Publisher.all().single()
            assertEquals("Publisher A", result1.name)
            assertEquals(p1.value[Publishers.isbnCode], result1.id.value[Publishers.isbnCode])
            assertEquals(p1.value[Publishers.pubId], result1.id.value[Publishers.pubId])

            Publisher.new { name = "Publisher B" }
            Publisher.new { name = "Publisher C" }

            val resul2 = Publisher.all().toList()
            assertEquals(3, resul2.size)
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

            // test all id column components are accessible from single ResultRow access
            val idResult = result[Publishers.id]
            assertIs<EntityID<CompositeID>>(idResult)
            assertEquals(result[Publishers.pubId], idResult.value[Publishers.pubId])
            assertEquals(result[Publishers.isbnCode], idResult.value[Publishers.isbnCode])

            // test that using composite id column in DSL query builder works
            val dslQuery = Publishers.select(Publishers.id).where { Publishers.id eq idResult }.prepareSQL(this)
            val selectClause = dslQuery.substringAfter("SELECT ").substringBefore("FROM")
            // id column should deconstruct to 2 columns from PK
            assertEquals(2, selectClause.split(", ", ignoreCase = true).size)
            val whereClause = dslQuery.substringAfter("WHERE ")
            // 2 column in composite PK to check, joined by single AND operator
            assertEquals(2, whereClause.split("AND", ignoreCase = true).size)

            // test equality comparison fails if composite columns do not match
            expectException<IllegalStateException> {
                val fake = EntityID(CompositeID(mapOf(Publishers.isbnCode to 7)), Publishers)
                Publishers.selectAll().where { Publishers.id eq fake }
            }
        }
    }

    @Test
    fun testInsertAndGetCompositeIds() {
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER), Publishers) {
            // no need to wrap inserted DSL value in EntityID
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
    fun testFindByCompositeId() {
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER), Publishers) {
            val id1: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[pubId] = UUID.randomUUID()
                it[isbnCode] = 725
                it[name] = "Publisher A"
            }

            val p1 = Publisher.findById(id1)
            assertNotNull(p1)
            assertEquals(725, p1.id.value[Publishers.isbnCode])

            val id2: EntityID<CompositeID> = Publisher.new {
                name = "Publisher B"
            }.id

            val p2 = Publisher.findById(id2)
            assertNotNull(p2)
            assertEquals("Publisher B", p2.name)
            assertEquals(id2.value[Publishers.pubId], p2.id.value[Publishers.pubId])

            // test findById() using CompositeID value
            val compositeId1: CompositeID = id1.value
            val p3 = Publisher.findById(compositeId1)
            assertNotNull(p3)
            assertEquals(p1, p3)
        }
    }

    @Test
    fun testFindWithDSLBuilder() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }

            assertEquals(p1.id, Publisher.find { Publishers.name like "% A" }.single().id)

            val p2 = Publisher.find { Publishers.id eq p1.id }.single()
            assertEquals(p1, p2)
        }
    }

    @Test
    fun testUpdateCompositeEntity() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }

            p1.name = "Publisher B"

            assertEquals("Publisher B", Publisher.all().single().name)
        }
    }

    @Test
    fun testDeleteCompositeEntity() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }
            val p2 = Publisher.new {
                name = "Publisher B"
            }

            assertEquals(2, Publisher.all().count())

            p1.delete()

            val result = Publisher.all().single()
            assertEquals("Publisher B", result.name)
            assertEquals(p2.id, result.id)
        }
    }
}

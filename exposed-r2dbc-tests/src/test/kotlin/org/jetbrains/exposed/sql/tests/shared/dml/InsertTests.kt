package org.jetbrains.exposed.sql.tests.shared.dml

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.exists
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.substring
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.sql.trim
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InsertTests : R2dbcDatabaseTestsBase() {
    // Sanity check to ensure previous DDL tests still pass
    @Test
    fun tableExists() = runTest {
        val testTable = object : Table() {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTable) {
            assertEquals(true, testTable.exists())
        }
    }

    @Test
    fun testInsertAndGetId() = runTest {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }

        withTables(idTable) {
            idTable.insertAndGetId {
                it[idTable.name] = "1"
            }

            assertEquals(1L, idTable.selectAll().count())

            idTable.insertAndGetId {
                it[idTable.name] = "2"
            }

            assertEquals(2L, idTable.selectAll().count())

            assertFailAndRollback("Unique constraint") {
                idTable.insertAndGetId {
                    it[idTable.name] = "2"
                }
            }
        }
    }

    @Test
    fun `test insert and get id when column has different name and get value by id column`() = runTest {
        val testTableWithId = object : IdTable<Int>("testTableWithId") {
            val code = integer("code")
            override val id = code.entityId()
        }

        withTables(testTableWithId) {
            val id1 = testTableWithId.insertAndGetId {
                it[code] = 1
            }
            assertNotNull(id1)
            assertEquals(1, id1.value)

            val id2 = testTableWithId.insert {
                it[code] = 2
            } get testTableWithId.id
            assertNotNull(id2)
            assertEquals(2, id2.value)
        }
    }

    @Test
    fun `test id and column have different names and get value by original column`() = runTest {
        val exampleTable = object : IdTable<String>("test_id_and_column_table") {
            val exampleColumn = varchar("example_column", 200)
            override val id = exampleColumn.entityId()
        }

        withTables(exampleTable) {
            val value = "value"
            exampleTable.insert {
                it[exampleColumn] = value
            }

            val resultValues: List<String> = exampleTable.selectAll().map { it[exampleTable.exampleColumn] }.toList()

            assertEquals(value, resultValues.first())
        }
    }

    object LongIdTable : Table() {
        val id = long("id").autoIncrement()
        val name = text("name")

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testGeneratedKey() = runTest {
        withTables(LongIdTable) {
            val id = LongIdTable.insert {
                it[LongIdTable.name] = "Foo"
            } get LongIdTable.id

            assertEquals(1, LongIdTable.selectAll().count())
            assertEquals(LongIdTable.selectAll().last()[LongIdTable.id], id)
        }
    }

    @Test
    fun testInsertWithPredefinedId() = runTest {
        val stringTable = object : IdTable<String>("stringTable") {
            override val id = varchar("id", 15).entityId()
            val name = varchar("name", 10)
        }
        withTables(stringTable) {
            val entityID = EntityID("id1", stringTable)
            val id1 = stringTable.insertAndGetId {
                it[id] = entityID
                it[name] = "foo"
            }

            stringTable.insertAndGetId {
                it[id] = EntityID("testId", stringTable)
                it[name] = "bar"
            }

            assertEquals(id1, entityID)
            val row1 = stringTable.selectAll().where { stringTable.id eq entityID }.singleOrNull()
            assertEquals(row1?.get(stringTable.id), entityID)

            val row2 = stringTable.selectAll().where { stringTable.id like "id%" }.singleOrNull()
            assertEquals(row2?.get(stringTable.id), entityID)
        }
    }

    @Test
    fun testInsertWithExpression() = runTest {
        val tbl = object : IntIdTable("testInsert") {
            val nullableInt = integer("nullableIntCol").nullable()
            val string = varchar("stringCol", 20)
        }

        fun expression(value: String) = stringLiteral(value).trim().substring(2, 4)

        suspend fun verify(value: String) {
            val row = tbl.selectAll().where { tbl.string eq value }.single()
            assertEquals(row[tbl.string], value)
        }

        withTables(tbl) {
            tbl.insert {
                it[string] = expression(" _exp1_ ")
            }

            verify("exp1")

            tbl.insert {
                it[string] = expression(" _exp2_ ")
                it[nullableInt] = 5
            }

            verify("exp2")

            tbl.insert {
                it[string] = expression(" _exp3_ ")
                it[nullableInt] = null
            }

            verify("exp3")
        }
    }
}

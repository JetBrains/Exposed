package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test

class CreateIndexTests : DatabaseTestsBase() {

    @Test
    fun createStandardIndex() {
        val TestTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        withTables(excludeSettings = listOf(TestDB.H2_MYSQL), tables = arrayOf(TestTable)) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            SchemaUtils.drop(TestTable)
        }
    }

    @Test
    fun createHashIndex() {
        val TestTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byNameHash = index("test_table_by_name", isUnique = false, name, indexType = "HASH")
        }

        withTables(excludeSettings = listOf(TestDB.H2_MYSQL, TestDB.SQLSERVER, TestDB.ORACLE), tables = arrayOf(TestTable)) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
        }
    }

    @Test
    fun createNonClusteredSQLServerIndex() {
        val TestTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byNameHash = index("test_table_by_name", isUnique = false, name, indexType = "NONCLUSTERED")
        }

        withDb(TestDB.SQLSERVER) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            SchemaUtils.drop(TestTable)
        }
    }

    @Test
    fun `test possibility to create indexes when table exists in different schemas`() {
        val TestTable = object : Table("test_table") {
            val id = integer("id").uniqueIndex()
            val name = varchar("name", length = 42).index("test_index")
            init {
                index(false, id, name)
            }
        }
        val schema1 = Schema("Schema1")
        val schema2 = Schema("Schema2")
        withSchemas(listOf(TestDB.SQLITE, TestDB.SQLSERVER), schema1, schema2) {
            SchemaUtils.setSchema(schema1)
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertEquals(true, TestTable.exists())
            SchemaUtils.setSchema(schema2)
            assertEquals(false, TestTable.exists())
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertEquals(true, TestTable.exists())
        }
    }


    @Test
    fun `test partial index`() {
        val partialIndexTable = object : IntIdTable("PartialIndexTableTest") {
            val name = varchar("name", 50)
            val value = integer("value")
            val anotherName = integer("anotherName")
            val anotherValue = integer("anotherValue")
            val flag = bool("flag")
            init {
                index("flag_index", columns = arrayOf(flag, name)) {
                    flag eq true
                }
                index(columns = arrayOf(value, name)) {
                    (name eq "aaa") and (value greaterEq 6)
                }
                uniqueIndex(columns = arrayOf(anotherValue))
            }
        }

        withDb(TestDB.POSTGRESQL) {
            SchemaUtils.createMissingTablesAndColumns(partialIndexTable)
            assertTrue(partialIndexTable.exists())

            // check that indexes are created and contain the proper filtering conditions
            exec(
                """SELECT indexname AS INDEX_NAME,
                   substring(indexdef, strpos(indexdef, ' WHERE ') + 7) AS FILTER_CONDITION
                   FROM pg_indexes
                   WHERE tablename='partialindextabletest' AND indexname != 'partialindextabletest_pkey'
                """.trimIndent()
            ) {
                var totalIndexCount = 0
                while (it.next()) {
                    totalIndexCount += 1
                    val filter = it.getString("FILTER_CONDITION")

                    when (it.getString("INDEX_NAME")) {
                        "partialindextabletest_value_name" -> assertEquals(filter, "(((name)::text = 'aaa'::text) AND (value >= 6))")
                        "flag_index" -> assertEquals(filter, "(flag = true)")
                        "partialindextabletest_anothervalue_unique" -> assertTrue(filter.startsWith(" UNIQUE INDEX "))
                    }
                }
                kotlin.test.assertEquals(totalIndexCount, 3, "Indexes expected to be created")
            }

            fun List<Column<*>>.names(): Set<String> { return map { identity(it) }.toSet() }
            fun getIndexes(): List<Index> {
                db.dialect.resetCaches()
                return currentDialect.existingIndices(partialIndexTable)[partialIndexTable].orEmpty()
            }

            val dropIndex = Index(columns = listOf(partialIndexTable.value, partialIndexTable.name), unique = false).dropStatement().first()
            kotlin.test.assertTrue(dropIndex.startsWith("DROP INDEX "), "Unique partial index must be created and dropped as index")
            val dropUniqConstraint = Index(columns = listOf(partialIndexTable.anotherValue), unique = true).dropStatement().first()
            kotlin.test.assertTrue(dropUniqConstraint.startsWith("ALTER TABLE "), "Unique index must be created and dropped as constraint")

            execInBatch(listOf(dropUniqConstraint, dropIndex))

            assertEquals(getIndexes().size, 1)
            SchemaUtils.drop(partialIndexTable)
        }
    }
}

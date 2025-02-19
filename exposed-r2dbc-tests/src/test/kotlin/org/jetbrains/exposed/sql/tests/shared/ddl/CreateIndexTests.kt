package org.jetbrains.exposed.sql.tests.shared.ddl

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.exists
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectMetadataTest
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test
import kotlin.test.expect

class CreateIndexTests : R2dbcDatabaseTestsBase() {
    @Test
    fun createStandardIndex() = runTest {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        withTables(excludeSettings = listOf(TestDB.H2_V2_MYSQL), tables = arrayOf(testTable)) {
            SchemaUtils.createMissingTablesAndColumns(testTable)
            assertTrue(testTable.exists())
            SchemaUtils.drop(testTable)
        }
    }

    @Test
    fun createHashIndex() = runTest {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byNameHash = index("test_table_by_name", isUnique = false, name, indexType = "HASH")
        }

        withTables(
            excludeSettings = listOf(TestDB.H2_V2_MYSQL, TestDB.SQLSERVER, TestDB.ORACLE),
            tables = arrayOf(testTable)
        ) {
            SchemaUtils.createMissingTablesAndColumns(testTable)
            assertTrue(testTable.exists())
        }
    }

    @Test
    fun createNonClusteredSQLServerIndex() = runTest {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byNameHash = index("test_table_by_name", isUnique = false, name, indexType = "NONCLUSTERED")
        }

        withDb(TestDB.SQLSERVER) {
            SchemaUtils.createMissingTablesAndColumns(testTable)
            assertTrue(testTable.exists())
            SchemaUtils.drop(testTable)
        }
    }

    @Test
    fun testCreateIndexWithTableInDifferentSchemas() = runTest {
        val testTable = object : Table("test_table") {
            val id = integer("id").uniqueIndex()
            val name = varchar("name", length = 42).index("test_index")

            init {
                index(false, id, name)
            }
        }
        val schema1 = Schema("Schema1")
        val schema2 = Schema("Schema2")
        withSchemas(listOf(TestDB.SQLSERVER), schema1, schema2) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.setSchema(schema1)
            SchemaUtils.createMissingTablesAndColumns(testTable)
            assertEquals(true, testTable.exists())
            SchemaUtils.setSchema(schema2)
            assertEquals(false, testTable.exists())
            SchemaUtils.createMissingTablesAndColumns(testTable)
            assertEquals(true, testTable.exists())
        }
    }

    @Test
    fun testCreateAndDropPartialIndex() = runTest {
        val tester = object : Table("tester") {
            val name = varchar("name", 32).uniqueIndex()
            val age = integer("age")
            val team = varchar("team", 32)

            init {
                uniqueIndex("team_only_index", team) { team eq "A" }
                index("name_age_index", isUnique = false, name, age) { age greaterEq 20 }
            }
        }

        withDb(listOf(TestDB.POSTGRESQL)) {
            SchemaUtils.createMissingTablesAndColumns(tester)
            assertTrue(tester.exists())

            val createdStatements = tester.indices.map { SchemaUtils.createIndex(it).first() }
            assertEquals(3, createdStatements.size)
            if (currentDialectTest is SQLiteDialect) {
                assertTrue(createdStatements.all { it.startsWith("CREATE ") })
            } else {
                assertEquals(2, createdStatements.count { it.startsWith("CREATE ") })
                assertEquals(1, createdStatements.count { it.startsWith("ALTER TABLE ") })
            }

            assertEquals(2, tester.indices.count { it.filterCondition != null })

            var indices = getIndices(tester)
            assertEquals(3, indices.size)

            val uniqueWithPartial = Index(
                listOf(tester.team),
                true,
                "team_only_index",
                null,
                Op.TRUE
            ).dropStatement().first()
            val dropStatements = indices.map { it.dropStatement().first() }
            expect(Unit) { execInBatch(dropStatements + uniqueWithPartial) }

            indices = getIndices(tester)
            assertEquals(0, indices.size)

            // test for non-unique partial index with type
            val type: String? = when (currentDialectTest) {
                is PostgreSQLDialect -> "BTREE"
                is SQLServerDialect -> "NONCLUSTERED"
                else -> null
            }
            val typedPartialIndex = Index(
                listOf(tester.name),
                false,
                "name_only_index",
                type,
                tester.name neq "Default"
            )
            val createdIndex = SchemaUtils.createIndex(typedPartialIndex).single()
            assertTrue(createdIndex.startsWith("CREATE "))
            assertTrue(" WHERE " in createdIndex)
            assertTrue(typedPartialIndex.dropStatement().first().startsWith("DROP INDEX "))

            SchemaUtils.drop(tester)
        }
    }

    @Test
    fun testCreateAndDropFunctionalIndex() = runTest {
        val tester = object : IntIdTable("tester") {
            val amount = integer("amount")
            val price = integer("price")
            val item = varchar("item", 32).nullable()

            init {
                index(customIndexName = "tester_plus_index", isUnique = false, functions = listOf(amount.plus(price)))
                index(isUnique = false, functions = listOf(item.lowerCase()))
                uniqueIndex(columns = arrayOf(price), functions = listOf(Coalesce(item, stringLiteral("*"))))
            }
        }

        val functionsNotSupported = TestDB.ALL_MARIADB + TestDB.ALL_H2 + TestDB.SQLSERVER + TestDB.MYSQL_V5
        withTables(excludeSettings = functionsNotSupported, tester) {
            SchemaUtils.createMissingTablesAndColumns()
            assertTrue(tester.exists())

            var indices = getIndices(tester)
            assertEquals(3, indices.size)

            val dropStatements = indices.map { it.dropStatement().first() }
            expect(Unit) { execInBatch(dropStatements) }

            indices = getIndices(tester)
            assertEquals(0, indices.size)
        }
    }

    private suspend fun R2dbcTransaction.getIndices(table: Table): List<Index> {
        db.dialectMetadata.resetCaches()
        return currentDialectMetadataTest.existingIndices(table)[table].orEmpty()
    }
}

package org.jetbrains.exposed.v1.tests.shared.ddl

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.expect

class CreateIndexTests : DatabaseTestsBase() {

    @Test
    fun createStandardIndex() {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        withTables(excludeSettings = listOf(TestDB.H2_V2_MYSQL), tables = arrayOf(testTable)) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(testTable)
            assertTrue(testTable.exists())
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(testTable)
        }
    }

    @Test
    fun createHashIndex() {
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
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(testTable)
            assertTrue(testTable.exists())
        }
    }

    @Test
    fun createNonClusteredSQLServerIndex() {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byNameHash = index("test_table_by_name", isUnique = false, name, indexType = "NONCLUSTERED")
        }

        withDb(TestDB.SQLSERVER) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(testTable)
            assertTrue(testTable.exists())
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(testTable)
        }
    }

    @Test
    fun testCreateIndexWithTableInDifferentSchemas() {
        val testTable = object : Table("test_table") {
            val id = integer("id").uniqueIndex()
            val name = varchar("name", length = 42).index("test_index")

            init {
                index(false, id, name)
            }
        }
        val schema1 = Schema("Schema1")
        val schema2 = Schema("Schema2")
        withSchemas(listOf(TestDB.SQLITE, TestDB.SQLSERVER), schema1, schema2) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.setSchema(schema1)
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(testTable)
            assertEquals(true, testTable.exists())
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.setSchema(schema2)
            assertEquals(false, testTable.exists())
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(testTable)
            assertEquals(true, testTable.exists())
        }
    }

    @Test
    fun testCreateAndDropPartialIndexWithPostgres() {
        val partialIndexTable = object : IntIdTable("PartialIndexTableTest") {
            val name = varchar("name", 50)
            val value = integer("value")
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

        withDb(TestDB.ALL_POSTGRES) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(partialIndexTable)
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
                        "partialindextabletest_value_name" -> assertEquals(
                            filter,
                            "(((name)::text = 'aaa'::text) AND (value >= 6))"
                        )
                        "flag_index" -> assertEquals(filter, "(flag = true)")
                        "partialindextabletest_anothervalue_unique" -> assertTrue(filter.startsWith(" UNIQUE INDEX "))
                    }
                }
                kotlin.test.assertEquals(totalIndexCount, 3, "Indexes expected to be created")
            }

            val dropIndex = Index(
                columns = listOf(partialIndexTable.value, partialIndexTable.name),
                unique = false
            ).dropStatement().first()
            kotlin.test.assertTrue(
                dropIndex.startsWith("DROP INDEX "),
                "Unique partial index must be created and dropped as index"
            )
            val dropUniqueConstraint = Index(
                columns = listOf(partialIndexTable.anotherValue),
                unique = true
            ).dropStatement().first()
            kotlin.test.assertTrue(
                dropUniqueConstraint.startsWith("ALTER TABLE "),
                "Unique index must be created and dropped as constraint"
            )

            execInBatch(listOf(dropUniqueConstraint, dropIndex))

            assertEquals(getIndices(partialIndexTable).size, 1)
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(partialIndexTable)
        }
    }

    @Test
    fun testCreateAndDropPartialIndex() {
        val tester = object : Table("tester") {
            val name = varchar("name", 32).uniqueIndex()
            val age = integer("age")
            val team = varchar("team", 32)

            init {
                uniqueIndex("team_only_index", team) { team eq "A" }
                index("name_age_index", isUnique = false, name, age) { age greaterEq 20 }
            }
        }

        withDb(TestDB.ALL_POSTGRES + TestDB.SQLITE + TestDB.SQLSERVER) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(tester)
            assertTrue(tester.exists())

            val createdStatements = tester.indices.map { org.jetbrains.exposed.v1.jdbc.SchemaUtils.createIndex(it).first() }
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
            val createdIndex = org.jetbrains.exposed.v1.jdbc.SchemaUtils.createIndex(typedPartialIndex).single()
            assertTrue(createdIndex.startsWith("CREATE "))
            assertTrue(" WHERE " in createdIndex)
            assertTrue(typedPartialIndex.dropStatement().first().startsWith("DROP INDEX "))

            org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tester)
        }
    }

    @Test
    fun testPartialIndexNotCreated() {
        val tester = object : Table("tester") {
            val age = integer("age")

            init {
                index("age_index", false, age) { age greaterEq 10 }
            }
        }

        withTables(tester) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns()
            assertTrue(tester.exists())

            val expectedIndexCount = when (currentDialectTest) {
                is PostgreSQLDialect, is SQLServerDialect, is SQLiteDialect -> 1
                else -> 0
            }
            val actualIndexCount = currentDialectMetadataTest.existingIndices(tester)[tester].orEmpty().size
            assertEquals(expectedIndexCount, actualIndexCount)
        }
    }

    @Test
    fun testCreateAndDropFunctionalIndex() {
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

        val functionsNotSupported = TestDB.ALL_H2_V2 + TestDB.MARIADB + TestDB.SQLSERVER + TestDB.MYSQL_V5
        withTables(excludeSettings = functionsNotSupported, tester) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns()
            assertTrue(tester.exists())

            var indices = getIndices(tester)
            assertEquals(3, indices.size)

            val dropStatements = indices.map { it.dropStatement().first() }
            expect(Unit) { execInBatch(dropStatements) }

            indices = getIndices(tester)
            assertEquals(0, indices.size)
        }
    }

    @Test
    fun testCreateAndDropIndexWithLongName() {
        // Long index name
        val indexName = "index-" + (0..100).joinToString(separator = "-") { "$it" }

        val tester = object : Table("tester") {
            val value = integer("value").index(indexName)
        }

        withDb {
            val createStatement = tester.indices.single().createStatement().single()

            val dropStatement = tester.indices.single().dropStatement().single()

            // Both statements must have either full index name or shortened index name
            assertEquals(createStatement.contains(indexName), dropStatement.contains(indexName))
        }
    }

    private fun JdbcTransaction.getIndices(table: Table): List<Index> {
        db.dialectMetadata.resetCaches()
        return currentDialectMetadataTest.existingIndices(table)[table].orEmpty()
    }
}

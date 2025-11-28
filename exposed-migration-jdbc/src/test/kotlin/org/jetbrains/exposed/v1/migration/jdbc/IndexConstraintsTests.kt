package org.jetbrains.exposed.v1.migration.jdbc

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.jupiter.api.Test

class IndexConstraintsTests : DatabaseTestsBase() {
    @Test
    fun testDropExtraIndexOnSameColumn() {
        val testTableWithTwoIndices = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
            val byName2 = index("test_table_by_name_2", false, name)
        }

        val testTableWithOneIndex = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        // Oracle does not allow more than one index on a column
        withTables(excludeSettings = listOf(TestDB.ORACLE), testTableWithTwoIndices) {
            assertTrue(testTableWithTwoIndices.exists())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTableWithOneIndex, withLogs = false)
            assertEquals(1, statements.size)
        }
    }

    @Test
    fun testDropUnmappedIndex() {
        val testTableWithIndex = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        val testTableWithoutIndex = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTableWithIndex) {
            assertTrue(testTableWithIndex.exists())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTableWithoutIndex, withLogs = false)
            assertEquals(1, statements.size)
        }
    }

    @Test
    fun testDifferentCheckConstraintForSameUnderlyingColumnType() {
        val oldTable = object : Table("tester") {
            val tester_col = byte("tester_col")
        }
        val newTable = object : Table("tester") {
            val tester_col = ubyte("tester_col")
        }

        // For H2 PostgreSQL, both `byte` and `ubyte` have the same column type of SMALLINT
        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2_PSQL, oldTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
            assertEquals(2, statements.size)
            assertEquals(
                newTable.checkConstraints().single().createStatement().single(),
                statements[0]
            )
            assertEquals(
                oldTable.checkConstraints().single().dropStatement().single(),
                statements[1]
            )
            statements.forEach(::exec)
            newTable.insert {
                it[tester_col] = UByte.MAX_VALUE
            }
        }
    }

    @Test
    fun testAddMissingCheckConstraint() {
        val oldTable = object : Table("tester") {
            val tester_col = text("tester_col")
        }
        val newTable = object : Table("tester") {
            val tester_col = byte("tester_col")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2_PSQL, oldTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
            assertEquals(2, statements.size)
            assertEquals(
                newTable.checkConstraints().single().createStatement().single(),
                statements[1]
            )
            statements.forEach(::exec)
            newTable.insert {
                it[tester_col] = Byte.MAX_VALUE
            }
        }
    }

    @Test
    fun testDropUnmappedCheckConstraint() {
        val oldTable = object : Table("tester") {
            val tester_col = byte("tester_col")
        }
        val newTable = object : Table("tester") {
            val tester_col = text("tester_col")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2_PSQL, oldTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
            assertEquals(2, statements.size)
            assertEquals(
                oldTable.checkConstraints().single().dropStatement().single(),
                statements[1]
            )
            statements.forEach(::exec)
            newTable.insert {
                it[tester_col] = "Testing text"
            }
        }
    }

    @Test
    fun testDropUnmappedIndices() {
        val dbTable = object : Table("testDropUnmappedIndices") {
            val indexOnlyInDb = integer("indexOnlyInDb").index("indexOnlyInDbIdx")
            val columnWithIndexOnlyInDb = integer("columnWithIndexOnlyInDb").index("columnWithIndexOnlyInDbIdx")
            val indexInCodeAndDB = integer("indexInCodeAndDB").index("indexInCodeAndDBIdx")
        }

        val tester = object : Table("testDropUnmappedIndices") {
            val indexOnlyInDb = integer("indexOnlyInDb")
            val indexInCodeAndDB = integer("indexInCodeAndDB").index("indexInCodeAndDBIdx")
            val indexOnlyInCode = integer("indexOnlyInCode").index("indexOnlyInCodeIdx")
        }

        withTables(dbTable) {
            val statements = MigrationUtils.dropUnmappedIndices(tester)
            assertEquals(2, statements.size)
            assertEquals(1, statements.map { it.lowercase() }.filter { it.contains(" indexOnlyInDbIdx".lowercase()) }.size)
            assertEquals(1, statements.map { it.lowercase() }.filter { it.contains(" columnWithIndexOnlyInDbIdx".lowercase()) }.size)
        }
    }
}

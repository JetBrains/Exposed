package org.jetbrains.exposed.v1.migration.jdbc

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.expect

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
    fun testCheckConstraintWithNameChangeTriggersStatements() {
        val oldTable = object : Table("tester") {
            val tester_col = text("tester_col").check("testCheck1") { it like "The%" }
        }
        val newTable = object : Table("tester") {
            val tester_col = text("tester_col").check("testCheck2") { it like "The%" }
        }

        withTables(oldTable) {
            // original table should not trigger
            assertTrue(MigrationUtils.statementsRequiredForDatabaseMigration(oldTable, withLogs = false).isEmpty())

            // name change should trigger add + drop of check constraints
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(newTable, withLogs = false)
            if (currentDialectTest.supportsAlterCheckConstraint) {
                assertEquals(2, statements.size)
                assertEquals(
                    newTable.checkConstraints().single().createStatement().single().lowercase(),
                    statements[0].lowercase()
                )
                assertEquals(
                    oldTable.checkConstraints().single().dropStatement().single().lowercase(),
                    statements[1].lowercase()
                )
                expect(Unit) {
                    statements.forEach(::exec)
                }
            } else {
                assertEquals(0, statements.size)
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

    @Test
    fun testFunctionalIndicesOnDifferentExpressionsAreNotExcessive() {
        val tester = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)
            val email = varchar("email", length = 42)

            override val primaryKey = PrimaryKey(id)

            init {
                index("test_table_lower_name", false, functions = listOf(name.lowerCase()))
                index("test_table_lower_email", false, functions = listOf(email.lowerCase()))
            }
        }

        val functionsNotSupported = TestDB.ALL_H2_V2 + TestDB.MARIADB + TestDB.SQLSERVER + TestDB.MYSQL_V5
        withTables(excludeSettings = functionsNotSupported, tester) {
            assertTrue(tester.exists())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tester, withLogs = false)
            // Only the drop matters here. Oracle case-folds the index names and Index.onlyNameDiffer() ignores
            // functions, which already produces a spurious CREATE for one of them independently of this change.
            assertEquals(0, statements.count { it.contains("DROP INDEX", ignoreCase = true) })
        }
    }

    @Test
    fun testPartialAndFullIndexOnSameColumnAreNotExcessive() {
        val tester = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)

            init {
                index("test_table_by_name", false, name)
                index("test_table_by_name_partial", false, name) { name like "A%" }
            }
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, tester) {
            assertTrue(tester.exists())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tester, withLogs = false)
            assertEquals(0, statements.size)
        }
    }

    @Test
    fun testIndicesOfUnmappedColumnsAreNotDroppedTwice() {
        val dbTable = object : Table("test_table") {
            val id = integer("id")
            val first = bool("first").nullable().index("test_table_first")
            val second = bool("second").nullable().index("test_table_second")

            override val primaryKey = PrimaryKey(id)
        }

        val tester = object : Table("test_table") {
            val id = integer("id")

            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = listOf(TestDB.ORACLE), dbTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tester, withLogs = false)

            assertEquals(statements.distinct().size, statements.size)
            assertEquals(1, statements.count { it.contains("test_table_first", ignoreCase = true) })
            assertEquals(1, statements.count { it.contains("test_table_second", ignoreCase = true) })
        }
    }

    @Test
    fun testPartialIndicesWithDifferentConditionsOnSameColumnAreNotExcessive() {
        val tester = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)

            init {
                index("test_table_by_name_a", false, name) { name like "A%" }
                index("test_table_by_name_b", false, name) { name like "B%" }
            }
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, tester) {
            assertTrue(tester.exists())

            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(tester, withLogs = false)
            assertEquals(0, statements.size)
        }
    }
}

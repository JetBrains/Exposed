package org.jetbrains.exposed.v1.tests.shared.dml

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Reproduces issue #683: Postgres column names are case sensitive (uppercase / lowercase)
 *
 * The problem: When a PostgreSQL table is created with quoted (case-sensitive) identifiers,
 * Exposed needs to consistently use quoted identifiers to access it. Without quotes,
 * PostgreSQL folds identifiers to lowercase, causing "column does not exist" errors.
 *
 * Example:
 * - Database table created with: CREATE TABLE "MyTable" ("UpperCaseColumn" INT)
 * - Exposed table defined as: Table("MyTable") with column("UpperCaseColumn")
 * - Generated SQL: SELECT mytable.uppercasecolumn... (lowercase, unquoted)
 * - PostgreSQL error: column "uppercasecolumn" does not exist
 *
 * Workaround: Manually quote identifiers in Exposed: Table("\"MyTable\"") with integer("\"UpperCaseColumn\"")
 * This generates: SELECT "MyTable"."UpperCaseColumn"... which works correctly.
 *
 * Note: This workaround has issues:
 * - Breaks MySQL (which uses backticks, not double quotes)
 * - Can break auto-generated constraint names
 *
 * Requested feature: A flag to auto-quote identifiers to preserve case sensitivity across databases.
 */
class ColumnCaseSensitivityTests : DatabaseTestsBase() {
    object TableUnquoted : Table("MyTable") {
        val id = integer("id")
        val upperCaseColumn = integer("UpperCaseColumn")

        override val primaryKey = PrimaryKey(id)
    }

    private fun quoted(name: String): String {
        val quote = when (currentDialect) {
            is MysqlDialect -> "`"
            else -> "\""
        }

        return "$quote$name$quote"
    }

    fun createTableUnquotedSql() = """CREATE TABLE ${quoted("MyTable")} (${quoted("id")} INT PRIMARY KEY, ${quoted("UpperCaseColumn")} INT NOT NULL)"""

    fun dropUnquotedTableSql() = """DROP TABLE IF EXISTS ${quoted("MyTable")}"""

    @Test
    fun testUnquotedIdentifiersFail() {
        withDb(db = listOf(TestDB.POSTGRESQL, TestDB.H2_V2)) {
            val testDb = db

            try {
                exec(createTableUnquotedSql())

                val exception = assertFailsWith<ExposedSQLException> {
                    TableUnquoted.insert {
                        it[id] = 1
                        it[upperCaseColumn] = 42
                    }
                }

                assert(
                    exception.message?.contains("does not exist", ignoreCase = true) == true ||
                        exception.message?.contains("not found", ignoreCase = true) == true
                ) {
                    "Expected 'does not exist' or 'not found' error, got: ${exception.message}"
                }
            } finally {
                transaction(testDb) {
                    try {
                        exec(dropUnquotedTableSql())
                    } catch (e: Exception) {
                        exposedLogger.info("Cleanup failed: ${e.message}")
                    }
                }
            }
        }
    }

    @Test
    fun testPreserveIdentifierCasingFlag() {
        withDb(configure = { preserveIdentifierCasing = true }) {
            exec(createTableUnquotedSql())

            try {
                TableUnquoted.insert {
                    it[id] = 1
                    it[upperCaseColumn] = 42
                }

                val result = TableUnquoted.selectAll().single()
                assertEquals(42, result[TableUnquoted.upperCaseColumn])
            } finally {
                exec(dropUnquotedTableSql())
            }
        }
    }
}

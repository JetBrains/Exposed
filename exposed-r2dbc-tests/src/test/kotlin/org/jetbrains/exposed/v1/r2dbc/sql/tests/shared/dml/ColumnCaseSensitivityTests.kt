package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
class ColumnCaseSensitivityTests : R2dbcDatabaseTestsBase() {
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
        Assumptions.assumeTrue(dialect in listOf(TestDB.POSTGRESQL, TestDB.H2_V2))

        withConnection(dialect) { database, testDb ->
            try {
                suspendTransaction(database) {
                    try {
                        exec(dropUnquotedTableSql())
                    } catch (e: Exception) {
                        exposedLogger.info("Pre-cleanup (expected if table doesn't exist): ${e.message}")
                    }
                }

                suspendTransaction(database) {
                    exec(createTableUnquotedSql())
                }

                try {
                    suspendTransaction(database) {
                        expectException<ExposedR2dbcException> {
                            TableUnquoted.insert {
                                it[id] = 1
                                it[upperCaseColumn] = 42
                            }
                        }
                        rollback()
                    }
                } catch (e: Exception) {
                    // Expected: PostgreSQL R2DBC throws rollback exception after the expected failure
                    exposedLogger.info("Transaction rollback after expected exception (normal for R2DBC): ${e.message}")
                }
            } finally {
                suspendTransaction(database) {
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
            try {
                exec(dropUnquotedTableSql())
            } catch (e: Exception) {
                exposedLogger.info("Pre-cleanup (expected if table doesn't exist): ${e.message}")
            }

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

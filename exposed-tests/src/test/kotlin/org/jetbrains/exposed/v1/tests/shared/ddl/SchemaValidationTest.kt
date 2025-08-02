package org.jetbrains.exposed.v1.tests.shared.ddl

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.migration.SchemaValidationException
import org.jetbrains.exposed.v1.migration.assertSchemaIsCorrect
import org.jetbrains.exposed.v1.migration.validateSchema
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertFalse
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SchemaValidationTest : DatabaseTestsBase() {

    // Simple test table
    object TestTable : Table("test_table") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 50)

        override val primaryKey = PrimaryKey(id)
    }

    // Table for mismatched schema test
    object MismatchedTable : Table("mismatched_table") {
        val id = integer("id").autoIncrement()

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testAssertSchemaIsCorrectWithValidSchema() {
        withTables(TestTable) {
            // Schema should be correct after creation
            assertSchemaIsCorrect(TestTable)
        }
    }

    @Test
    fun testAssertSchemaIsCorrectWithInvalidSchema() {
        withTables(TestTable) {
            // Create a situation where the schema is not aligned
            // by testing with a table that was not created
            assertFailsWith<SchemaValidationException> {
                assertSchemaIsCorrect(MismatchedTable)
            }
        }
    }

    @Test
    fun testAssertSchemaIsCorrectWithBatchMode() {
        withTables(TestTable) {
            // Test with batch mode
            assertSchemaIsCorrect(TestTable, inBatch = true)
        }
    }

    @Test
    fun testAssertSchemaIsCorrectMultipleTables() {
        withTables(TestTable, MismatchedTable) {
            // Schema should be correct for both tables
            assertSchemaIsCorrect(TestTable, MismatchedTable)
        }
    }

    @Test
    fun testValidateSchemaWithValidSchema() {
        withTables(TestTable) {
            val result = validateSchema(TestTable)
            assertTrue(result.isValid())
            assertTrue(result.getMigrationStatements().isEmpty())
        }
    }

    @Test
    fun testValidateSchemaWithInvalidSchema() {
        withTables(TestTable) {
            // Test with a table that was not created
            val result = validateSchema(MismatchedTable)
            assertFalse(result.isValid())
            assertFalse(result.getMigrationStatements().isEmpty())
        }
    }

    @Test
    fun testValidateSchemaWithBatchMode() {
        withTables(TestTable) {
            val result = validateSchema(TestTable, inBatch = true)
            assertTrue(result.isValid())
        }
    }

    @Test
    fun testSchemaValidationExceptionProperties() {
        withTables(TestTable) {
            try {
                assertSchemaIsCorrect(MismatchedTable)
            } catch (e: SchemaValidationException) {
                assertFalse(e.migrationStatements.isEmpty())
                assertTrue(e.message!!.contains("Schema validation failed"))
            }
        }
    }

    @Test
    fun testValidateSchemaResultTypes() {
        withTables(TestTable) {
            // Test with valid schema
            val validResult = validateSchema(TestTable)
            assertTrue(validResult.isValid())

            // Test with invalid schema
            val invalidResult = validateSchema(MismatchedTable)
            assertFalse(invalidResult.isValid())
        }
    }
}

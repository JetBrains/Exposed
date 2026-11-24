package org.jetbrains.exposed.v1.migration.jdbc

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColumnCommentMigrationTests : DatabaseTestsBase() {

    private val excludeUnsupportedComments = listOf(TestDB.H2_V2, TestDB.H2_V2_PSQL, TestDB.H2_V2_ORACLE, TestDB.H2_V2_SQLSERVER, TestDB.SQLITE, TestDB.SQLSERVER)

    @Test
    fun testAddCommentToExistingColumn() {
        // V1: Create table without comments
        val testTableV1 = object : Table("comment_migration_users") {
            val id = integer("id")
            val email = varchar("email", 255)
            override val primaryKey = PrimaryKey(id)
        }

        withDb(excludeSettings = excludeUnsupportedComments) {
            try {
                SchemaUtils.create(testTableV1)

                // Verify no comments initially
                val metadataBefore = connection.metadata { columns(testTableV1) }
                val columnsBefore = metadataBefore[testTableV1]!!
                assertNull(columnsBefore.metadataFor(testTableV1.id)?.comment, "id column should have no comment initially")
                assertNull(columnsBefore.metadataFor(testTableV1.email)?.comment, "email column should have no comment initially")

                // V2: Define table with comments added
                val testTableV2 = object : Table("comment_migration_users") {
                    val id = integer("id").comment("User identifier")
                    val email = varchar("email", 255).comment("User email address")
                    override val primaryKey = PrimaryKey(id)
                }

                // Apply migration
                MigrationUtils.statementsRequiredForDatabaseMigration(testTableV2).forEach { exec(it) }

                // Verify comments were added
                val metadataAfter = connection.metadata { columns(testTableV2) }
                val columnsAfter = metadataAfter[testTableV2]!!
                assertEquals(
                    "User identifier",
                    columnsAfter.metadataFor(testTableV2.id)?.comment,
                    "id column should have comment"
                )
                assertEquals(
                    "User email address",
                    columnsAfter.metadataFor(testTableV2.email)?.comment,
                    "email column should have comment"
                )
            } finally {
                SchemaUtils.drop(testTableV1)
            }
        }
    }

    @Test
    fun testUpdateExistingComment() {
        // V1: Create table with initial comments
        val testTableV1 = object : Table("comment_migration_products") {
            val id = integer("id").comment("Product ID")
            val name = varchar("name", 100).comment("Product name")
            override val primaryKey = PrimaryKey(id)
        }

        withDb(excludeSettings = excludeUnsupportedComments) {
            try {
                SchemaUtils.create(testTableV1)

                // Verify initial comments
                val metadataBefore = connection.metadata { columns(testTableV1) }
                val columnsBefore = metadataBefore[testTableV1]!!
                assertEquals("Product ID", columnsBefore.metadataFor(testTableV1.id)?.comment)
                assertEquals("Product name", columnsBefore.metadataFor(testTableV1.name)?.comment)

                // V2: Define table with updated comments
                val testTableV2 = object : Table("comment_migration_products") {
                    val id = integer("id").comment("Unique product identifier")
                    val name = varchar("name", 100).comment("Full product name")
                    override val primaryKey = PrimaryKey(id)
                }

                // Apply migration
                MigrationUtils.statementsRequiredForDatabaseMigration(testTableV2).forEach { exec(it) }

                // Verify comments were updated
                val metadataAfter = connection.metadata { columns(testTableV2) }
                val columnsAfter = metadataAfter[testTableV2]!!
                assertEquals("Unique product identifier", columnsAfter.metadataFor(testTableV2.id)?.comment)
                assertEquals("Full product name", columnsAfter.metadataFor(testTableV2.name)?.comment)
            } finally {
                SchemaUtils.drop(testTableV1)
            }
        }
    }

    @Test
    fun testRemoveComment() {
        // V1: Create table with comments
        val testTableV1 = object : Table("comment_migration_orders") {
            val id = integer("id").comment("Order identifier")
            val total = decimal("total", 10, 2).comment("Order total amount")
            override val primaryKey = PrimaryKey(id)
        }

        withDb(excludeSettings = excludeUnsupportedComments) {
            try {
                SchemaUtils.create(testTableV1)

                // Verify initial comments exist
                val metadataBefore = connection.metadata { columns(testTableV1) }
                val columnsBefore = metadataBefore[testTableV1]!!
                assertEquals("Order identifier", columnsBefore.metadataFor(testTableV1.id)?.comment)
                assertEquals("Order total amount", columnsBefore.metadataFor(testTableV1.total)?.comment)

                // V2: Define table without comments
                val testTableV2 = object : Table("comment_migration_orders") {
                    val id = integer("id")
                    val total = decimal("total", 10, 2)
                    override val primaryKey = PrimaryKey(id)
                }

                // Apply migration
                MigrationUtils.statementsRequiredForDatabaseMigration(testTableV2).forEach { exec(it) }

                // Verify comments were removed
                val metadataAfter = connection.metadata { columns(testTableV2) }
                val columnsAfter = metadataAfter[testTableV2]!!
                assertNull(columnsAfter.metadataFor(testTableV2.id)?.comment, "id column comment should be removed")
                assertNull(columnsAfter.metadataFor(testTableV2.total)?.comment, "total column comment should be removed")
            } finally {
                SchemaUtils.drop(testTableV1)
            }
        }
    }

    @Test
    fun testNoChangeWhenCommentMatches() {
        val testTable = object : Table("comment_migration_inventory") {
            val id = integer("id").comment("Item ID")
            val quantity = integer("quantity").comment("Available quantity")
            override val primaryKey = PrimaryKey(id)
        }

        withDb(excludeSettings = excludeUnsupportedComments) {
            try {
                SchemaUtils.create(testTable)

                // Verify comments exist
                val metadataBefore = connection.metadata { columns(testTable) }
                val columnsBefore = metadataBefore[testTable]!!
                assertEquals("Item ID", columnsBefore.metadataFor(testTable.id)?.comment)
                assertEquals("Available quantity", columnsBefore.metadataFor(testTable.quantity)?.comment)

                // Same table definition - should generate no migration statements
                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTable)
                assertEquals(0, statements.size, "Expected no comment migration statements, but got: $statements")
            } finally {
                SchemaUtils.drop(testTable)
            }
        }
    }

    @Test
    fun testPartialCommentUpdate() {
        // V1: Create table with comments on some columns
        val testTableV1 = object : Table("comment_migration_partial") {
            val id = integer("id").comment("Primary key")
            val name = varchar("name", 100)
            val status = varchar("status", 50).comment("Status code")
            override val primaryKey = PrimaryKey(id)
        }

        withDb(excludeSettings = excludeUnsupportedComments) {
            try {
                SchemaUtils.create(testTableV1)

                // Verify initial state
                val metadataBefore = connection.metadata { columns(testTableV1) }
                val columnsBefore = metadataBefore[testTableV1]!!
                assertEquals("Primary key", columnsBefore.metadataFor(testTableV1.id)?.comment)
                assertNull(columnsBefore.metadataFor(testTableV1.name)?.comment)
                assertEquals("Status code", columnsBefore.metadataFor(testTableV1.status)?.comment)

                // V2: Update one comment, add one, keep one unchanged
                val testTableV2 = object : Table("comment_migration_partial") {
                    val id = integer("id").comment("Record identifier") // Updated
                    val name = varchar("name", 100).comment("Full name") // Added
                    val status = varchar("status", 50).comment("Status code") // Unchanged
                    override val primaryKey = PrimaryKey(id)
                }

                // Apply migration
                MigrationUtils.statementsRequiredForDatabaseMigration(testTableV2).forEach { exec(it) }

                // Verify final state
                val metadataAfter = connection.metadata { columns(testTableV2) }
                val columnsAfter = metadataAfter[testTableV2]!!
                assertEquals("Record identifier", columnsAfter.metadataFor(testTableV2.id)?.comment)
                assertEquals("Full name", columnsAfter.metadataFor(testTableV2.name)?.comment)
                assertEquals("Status code", columnsAfter.metadataFor(testTableV2.status)?.comment)
            } finally {
                SchemaUtils.drop(testTableV1)
            }
        }
    }
}

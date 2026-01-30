package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class ColumnCommentTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testColumnCommentInDDL() {
        val testTable = object : Table("table_with_column_comments") {
            val id = integer("id").comment("Primary identifier")
            val email = varchar("email", 255).comment("User email address")
            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTable) {
            val ddl = testTable.ddl

            when (currentDialectTest) {
                is PostgreSQLDialect -> {
                    assertTrue(ddl.any { it == "COMMENT ON COLUMN table_with_column_comments.id IS 'Primary identifier'" })
                    assertTrue(ddl.any { it == "COMMENT ON COLUMN table_with_column_comments.email IS 'User email address'" })
                }
                is MysqlDialect -> {
                    assertTrue(ddl.any { it.contains("id INT") && it.contains("COMMENT 'Primary identifier'") })
                    assertTrue(ddl.any { it.contains("email VARCHAR(255)") && it.contains("COMMENT 'User email address'") })
                }
                is OracleDialect -> {
                    assertTrue(ddl.any { it == "COMMENT ON COLUMN TABLE_WITH_COLUMN_COMMENTS.ID IS 'Primary identifier'" })
                    assertTrue(ddl.any { it == "COMMENT ON COLUMN TABLE_WITH_COLUMN_COMMENTS.EMAIL IS 'User email address'" })
                }
                is H2Dialect -> {
                    val h2Dialect = currentDialectTest as H2Dialect
                    if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.MySQL ||
                        h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.MariaDB
                    ) {
                        // H2 in MySQL mode uses uppercase names and INT type
                        assertTrue(ddl.any { it.contains("ID INT", ignoreCase = true) && it.contains("COMMENT 'Primary identifier'") })
                        assertTrue(
                            ddl.any { it.contains("EMAIL VARCHAR(255)", ignoreCase = true) && it.contains("COMMENT 'User email address'") }
                        )
                    }
                }
                else -> {
                    // Check for actual COMMENT SQL syntax, not just the word "comment" in table/column names
                    assertTrue(ddl.none { it.contains("COMMENT ON", ignoreCase = true) || it.contains("COMMENT '") || it.contains("COMMENT='") })
                }
            }
        }
    }

    @Test
    fun testColumnCommentWithSingleQuoteEscaping() {
        val testTable = object : Table("table_with_special_column") {
            val id = integer("id").comment("User's primary ID")
            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTable) {
            val ddl = testTable.ddl

            when (currentDialectTest) {
                is PostgreSQLDialect -> {
                    assertTrue(ddl.any { it == "COMMENT ON COLUMN table_with_special_column.id IS 'User''s primary ID'" })
                }
                is MysqlDialect -> {
                    assertTrue(ddl.any { it.contains("id INT") && it.contains("COMMENT 'User''s primary ID'") })
                }
                is OracleDialect -> {
                    assertTrue(ddl.any { it == "COMMENT ON COLUMN TABLE_WITH_SPECIAL_COLUMN.ID IS 'User''s primary ID'" })
                }
                is H2Dialect -> {
                    val h2Dialect = currentDialectTest as H2Dialect
                    if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.MySQL ||
                        h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.MariaDB
                    ) {
                        // H2 in MySQL mode uses uppercase names and INT type
                        assertTrue(
                            ddl.any { it.contains("ID INT", ignoreCase = true) && it.contains("COMMENT 'User''s primary ID'") }
                        )
                    }
                }
                else -> {
                    // Check for actual COMMENT SQL syntax, not just the word "comment" in table/column names
                    assertTrue(ddl.none { it.contains("COMMENT ON", ignoreCase = true) || it.contains("COMMENT '") || it.contains("COMMENT='") })
                }
            }
        }
    }

    @Test
    fun testColumnCommentMethodChaining() {
        val testTable = object : Table("test_method_chaining") {
            val id = integer("id").autoIncrement().comment("Auto-incrementing ID")
            val active = bool("active").default(true).comment("Is user active")
            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTable) {
            val ddl = testTable.ddl
            assertTrue(ddl.isNotEmpty())
            // Comments are set and DDL generated successfully
        }
    }

    @Test
    fun testMultipleColumnComments() {
        val testTable = object : Table("test_multiple_columns") {
            val id = integer("id").comment("Primary key")
            val userName = varchar("user_name", 100).comment("Full name")
            val email = varchar("email", 255).comment("Email address")
            val age = integer("age").comment("User age")
            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTable) {
            val ddl = testTable.ddl

            when (currentDialectTest) {
                is PostgreSQLDialect -> {
                    assertTrue(ddl.any { it.contains("COMMENT ON COLUMN test_multiple_columns.id IS 'Primary key'") })
                    assertTrue(ddl.any { it.contains("COMMENT ON COLUMN test_multiple_columns.user_name IS 'Full name'") })
                    assertTrue(ddl.any { it.contains("COMMENT ON COLUMN test_multiple_columns.email IS 'Email address'") })
                    assertTrue(ddl.any { it.contains("COMMENT ON COLUMN test_multiple_columns.age IS 'User age'") })
                }
                is MysqlDialect -> {
                    val createTableStmt = ddl.find { it.startsWith("CREATE TABLE") }!!
                    assertTrue(createTableStmt.contains("COMMENT 'Primary key'"))
                    assertTrue(createTableStmt.contains("COMMENT 'Full name'"))
                    assertTrue(createTableStmt.contains("COMMENT 'Email address'"))
                    assertTrue(createTableStmt.contains("COMMENT 'User age'"))
                }
                is OracleDialect -> {
                    assertTrue(ddl.any { it.contains("COMMENT ON COLUMN TEST_MULTIPLE_COLUMNS.ID IS 'Primary key'") })
                    assertTrue(ddl.any { it.contains("COMMENT ON COLUMN TEST_MULTIPLE_COLUMNS.USER_NAME IS 'Full name'") })
                    assertTrue(
                        ddl.any { it.contains("COMMENT ON COLUMN TEST_MULTIPLE_COLUMNS.EMAIL IS 'Email address'") }
                    )
                    assertTrue(ddl.any { it.contains("COMMENT ON COLUMN TEST_MULTIPLE_COLUMNS.AGE IS 'User age'") })
                }
                is H2Dialect -> {
                    val h2Dialect = currentDialectTest as H2Dialect
                    if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.MySQL ||
                        h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.MariaDB
                    ) {
                        val createTableStmt = ddl.find { it.startsWith("CREATE TABLE") }!!
                        assertTrue(createTableStmt.contains("COMMENT 'Primary key'"))
                        assertTrue(createTableStmt.contains("COMMENT 'Full name'"))
                    }
                }
                else -> {
                    // Check for actual COMMENT SQL syntax, not just the word "comment" in table/column names
                    assertTrue(ddl.none { it.contains("COMMENT ON", ignoreCase = true) || it.contains("COMMENT '") || it.contains("COMMENT='") })
                }
            }
        }
    }

    // Migration Tests

    private val excludeUnsupportedComments = listOf(TestDB.H2_V2, TestDB.H2_V2_PSQL, TestDB.H2_V2_ORACLE, TestDB.H2_V2_SQLSERVER, TestDB.SQLSERVER)

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
                commit()

                // Verify no comments initially
                val metadataBefore = connection().metadata { columns(testTableV1) }
                val columnsBefore = metadataBefore[testTableV1]!!
                assertNull(columnsBefore.find { it.name == testTableV1.id.nameInDatabaseCase() }?.comment)
                assertNull(columnsBefore.find { it.name == testTableV1.email.nameInDatabaseCase() }?.comment)

                // V2: Define table with comments added
                val testTableV2 = object : Table("comment_migration_users") {
                    val id = integer("id").comment("User identifier")
                    val email = varchar("email", 255).comment("User email address")
                    override val primaryKey = PrimaryKey(id)
                }

                // Apply migration
                SchemaUtils.statementsRequiredToActualizeScheme(testTableV2).forEach { exec(it) }
                commit()

                // Verify comments were added
                val metadataAfter = connection().metadata { columns(testTableV2) }
                val columnsAfter = metadataAfter[testTableV2]!!
                assertEquals("User identifier", columnsAfter.find { it.name == testTableV2.id.nameInDatabaseCase() }?.comment)
                assertEquals("User email address", columnsAfter.find { it.name == testTableV2.email.nameInDatabaseCase() }?.comment)
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
                commit()

                // Verify initial comments
                val metadataBefore = connection().metadata { columns(testTableV1) }
                val columnsBefore = metadataBefore[testTableV1]!!
                assertEquals("Product ID", columnsBefore.find { it.name == testTableV1.id.nameInDatabaseCase() }?.comment)
                assertEquals("Product name", columnsBefore.find { it.name == testTableV1.name.nameInDatabaseCase() }?.comment)

                // V2: Define table with updated comments
                val testTableV2 = object : Table("comment_migration_products") {
                    val id = integer("id").comment("Unique product identifier")
                    val name = varchar("name", 100).comment("Full product name")
                    override val primaryKey = PrimaryKey(id)
                }

                // Apply migration
                SchemaUtils.statementsRequiredToActualizeScheme(testTableV2).forEach { exec(it) }
                commit()

                // Verify comments were updated
                val metadataAfter = connection().metadata { columns(testTableV2) }
                val columnsAfter = metadataAfter[testTableV2]!!
                assertEquals("Unique product identifier", columnsAfter.find { it.name == testTableV2.id.nameInDatabaseCase() }?.comment)
                assertEquals("Full product name", columnsAfter.find { it.name == testTableV2.name.nameInDatabaseCase() }?.comment)
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
                commit()

                // Verify initial comments exist
                val metadataBefore = connection().metadata { columns(testTableV1) }
                val columnsBefore = metadataBefore[testTableV1]!!
                assertEquals("Order identifier", columnsBefore.find { it.name == testTableV1.id.nameInDatabaseCase() }?.comment)
                assertEquals("Order total amount", columnsBefore.find { it.name == testTableV1.total.nameInDatabaseCase() }?.comment)

                // V2: Define table without comments
                val testTableV2 = object : Table("comment_migration_orders") {
                    val id = integer("id")
                    val total = decimal("total", 10, 2)
                    override val primaryKey = PrimaryKey(id)
                }

                // Apply migration
                SchemaUtils.statementsRequiredToActualizeScheme(testTableV2).forEach { exec(it) }
                commit()

                // Verify comments were removed
                val metadataAfter = connection().metadata { columns(testTableV2) }
                val columnsAfter = metadataAfter[testTableV2]!!
                assertNull(columnsAfter.find { it.name == testTableV2.id.nameInDatabaseCase() }?.comment)
                assertNull(columnsAfter.find { it.name == testTableV2.total.nameInDatabaseCase() }?.comment)
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
                commit()

                // Verify comments exist
                val metadataBefore = connection().metadata { columns(testTable) }
                val columnsBefore = metadataBefore[testTable]!!
                assertEquals("Item ID", columnsBefore.find { it.name == testTable.id.nameInDatabaseCase() }?.comment)
                assertEquals("Available quantity", columnsBefore.find { it.name == testTable.quantity.nameInDatabaseCase() }?.comment)

                // Same table definition - should generate no migration statements
                val statements = SchemaUtils.statementsRequiredToActualizeScheme(testTable)
                assertEquals(0, statements.size)
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
                commit()

                // Verify initial state
                val metadataBefore = connection().metadata { columns(testTableV1) }
                val columnsBefore = metadataBefore[testTableV1]!!
                assertEquals("Primary key", columnsBefore.find { it.name == testTableV1.id.nameInDatabaseCase() }?.comment)
                assertNull(columnsBefore.find { it.name == testTableV1.name.nameInDatabaseCase() }?.comment)
                assertEquals("Status code", columnsBefore.find { it.name == testTableV1.status.nameInDatabaseCase() }?.comment)

                // V2: Update one comment, add one, keep one unchanged
                val testTableV2 = object : Table("comment_migration_partial") {
                    val id = integer("id").comment("Record identifier") // Updated
                    val name = varchar("name", 100).comment("Full name") // Added
                    val status = varchar("status", 50).comment("Status code") // Unchanged
                    override val primaryKey = PrimaryKey(id)
                }

                // Apply migration
                SchemaUtils.statementsRequiredToActualizeScheme(testTableV2).forEach { exec(it) }
                commit()

                // Verify final state
                val metadataAfter = connection().metadata { columns(testTableV2) }
                val columnsAfter = metadataAfter[testTableV2]!!
                assertEquals("Record identifier", columnsAfter.find { it.name == testTableV2.id.nameInDatabaseCase() }?.comment)
                assertEquals("Full name", columnsAfter.find { it.name == testTableV2.name.nameInDatabaseCase() }?.comment)
                assertEquals("Status code", columnsAfter.find { it.name == testTableV2.status.nameInDatabaseCase() }?.comment)
            } finally {
                SchemaUtils.drop(testTableV1)
            }
        }
    }
}

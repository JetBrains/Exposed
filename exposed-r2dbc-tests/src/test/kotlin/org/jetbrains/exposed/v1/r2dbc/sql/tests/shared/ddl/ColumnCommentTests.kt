package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.junit.jupiter.api.Test

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
}

package org.jetbrains.exposed.v1.gradle.plugin.migrations

import org.jetbrains.exposed.v1.gradle.plugin.statementToFileDescription
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StatementToFileDescriptionGeneratorTest {

    @Test
    fun `test CREATE TABLE statements`() {
        // Simple CREATE TABLE
        Assertions.assertEquals(
            "CREATE_TABLE_USERS",
            "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255))".statementToFileDescription(true)
        )

        // Simple CREATE TABLE
        Assertions.assertEquals(
            "create_table_users",
            "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255))".statementToFileDescription(false)
        )

        // CREATE TABLE with IF NOT EXISTS
        Assertions.assertEquals(
            "CREATE_TABLE_PRODUCTS",
            "CREATE TABLE IF NOT EXISTS products (id INT PRIMARY KEY, name VARCHAR(255))".statementToFileDescription(
                true
            )
        )

        // CREATE TABLE with schema prefix
        Assertions.assertEquals(
            "CREATE_TABLE_ORDERS",
            "CREATE TABLE public.orders (id INT PRIMARY KEY, customer_id INT)".statementToFileDescription(true)
        )

        // CREATE TABLE with quoted identifiers
        Assertions.assertEquals(
            "CREATE_TABLE_ITEMS",
            "CREATE TABLE \"items\" (id INT PRIMARY KEY, order_id INT)".statementToFileDescription(true)
        )
    }

    @Test
    fun `test ALTER TABLE statements`() {
        // ALTER TABLE with ADD COLUMN
        Assertions.assertEquals(
            "ALTER_TABLE_USERS_ADD_COLUMN_EMAIL",
            "ALTER TABLE users ADD COLUMN email VARCHAR(255)".statementToFileDescription(true)
        )

        // ALTER TABLE with just ADD (some db allow COLUMN keyword to be omitted)
        Assertions.assertEquals(
            "alter_table_users",
            "ALTER TABLE users ADD email VARCHAR(255)".statementToFileDescription(false)
        )

        // ALTER TABLE with ADD CONSTRAINT
        Assertions.assertEquals(
            "ALTER_TABLE_USERS_ADD_CONSTRAINT_USERS_EMAIL_UNIQUE",
            "ALTER TABLE users ADD CONSTRAINT users_email_unique UNIQUE (email)".statementToFileDescription(true)
        )

        // ALTER TABLE with DROP CONSTRAINT
        Assertions.assertEquals(
            "alter_table_users_drop_constraint_users_email_unique",
            "ALTER TABLE users DROP CONSTRAINT users_email_unique UNIQUE (email)".statementToFileDescription(false)
        )

        // ALTER TABLE with DROP COLUMN
        Assertions.assertEquals(
            "alter_table_users_drop_column_email",
            "ALTER TABLE users DROP COLUMN IF EXISTS email".statementToFileDescription(false)
        )

        // ALTER TABLE with just DROP (some db allow COLUMN keyword to be omitted)
        Assertions.assertEquals(
            "ALTER_TABLE_USERS",
            "ALTER TABLE users DROP email".statementToFileDescription(true)
        )

        // ALTER TABLE with schema prefix
        Assertions.assertEquals(
            "ALTER_TABLE_PRODUCTS_ADD_COLUMN_PRICE",
            "ALTER TABLE public.products ADD COLUMN price DECIMAL(10,2)".statementToFileDescription(true)
        )

        // ALTER TABLE with quoted identifiers
        Assertions.assertEquals(
            "ALTER_TABLE_ITEMS_ADD_COLUMN_QUANTITY",
            "ALTER TABLE \"items\" ADD COLUMN \"quantity\" INT".statementToFileDescription(true)
        )
    }

    @Test
    fun `test CREATE SEQUENCE statements`() {
        // Simple CREATE SEQUENCE
        Assertions.assertEquals(
            "CREATE_SEQUENCE_USERS_ID_SEQ", "CREATE SEQUENCE users_id_seq".statementToFileDescription(true)
        )

        // Simple CREATE SEQUENCE
        Assertions.assertEquals(
            "create_sequence_users_id_seq", "CREATE SEQUENCE users_id_seq".statementToFileDescription(false)
        )

        // CREATE SEQUENCE with IF NOT EXISTS
        Assertions.assertEquals(
            "CREATE_SEQUENCE_PRODUCTS_ID_SEQ",
            "CREATE SEQUENCE IF NOT EXISTS products_id_seq".statementToFileDescription(true)
        )

        // CREATE SEQUENCE with schema prefix
        Assertions.assertEquals(
            "CREATE_SEQUENCE_ORDERS_ID_SEQ", "CREATE SEQUENCE public.orders_id_seq".statementToFileDescription(true)
        )

        // CREATE SEQUENCE with quoted identifiers
        Assertions.assertEquals(
            "CREATE_SEQUENCE_ITEMS_ID_SEQ", "CREATE SEQUENCE \"items_id_seq\"".statementToFileDescription(true)
        )

        // CREATE SEQUENCE with options
        Assertions.assertEquals(
            "CREATE_SEQUENCE_USERS_ID_SEQ",
            "CREATE SEQUENCE users_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807".statementToFileDescription(
                true
            )
        )
    }

    @Test
    fun `test CREATE INDEX statements`() {
        // Simple CREATE INDEX
        Assertions.assertEquals(
            "CREATE_INDEX_IDX_USERS_EMAIL_ON_USERS",
            "CREATE INDEX idx_users_email ON users (email)".statementToFileDescription(true)
        )

        // CREATE INDEX with IF NOT EXISTS
        Assertions.assertEquals(
            "create_index_idx_users_email_on_users",
            "CREATE INDEX IF NOT EXISTS idx_users_email ON users (email)".statementToFileDescription(false)
        )

        // CREATE INDEX with schema prefix
        Assertions.assertEquals(
            "CREATE_INDEX_IDX_PRODUCTS_NAME_ON_PRODUCTS",
            "CREATE INDEX idx_products_name ON public.products (name)".statementToFileDescription(true)
        )

        // CREATE INDEX with quoted identifiers
        Assertions.assertEquals(
            "CREATE_INDEX_IDX_ITEMS_ORDER_ID_ON_ITEMS",
            "CREATE INDEX \"idx_items_order_id\" ON \"items\" (order_id)".statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "CREATE_INDEX_IDX_USERS_EMAIL_ON_USERS",
            "CREATE UNIQUE INDEX idx_users_email ON users (email)".statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "CREATE_INDEX_IDX_PRODUCTS_NAME_ON_PRODUCTS",
            "CREATE CLUSTERED INDEX idx_products_name ON public.products (name)".statementToFileDescription(true)
        )
    }

    @Test
    fun `test DROP TABLE statements`() {
        // Simple DROP TABLE
        Assertions.assertEquals(
            "DROP_TABLE_USERS", "DROP TABLE users".statementToFileDescription(true)
        )

        // DROP TABLE with IF EXISTS
        Assertions.assertEquals(
            "drop_table_products", "DROP TABLE IF EXISTS products".statementToFileDescription(false)
        )

        // DROP TABLE with schema prefix
        Assertions.assertEquals(
            "DROP_TABLE_ORDERS", "DROP TABLE public.orders".statementToFileDescription(true)
        )

        // DROP TABLE with quoted identifiers
        Assertions.assertEquals(
            "DROP_TABLE_ITEMS", "DROP TABLE \"items\"".statementToFileDescription(true)
        )
    }

    @Test
    fun `test DROP INDEX statements`() {
        // Simple DROP INDEX
        Assertions.assertEquals(
            "DROP_INDEX_IDX_USERS_EMAIL", "DROP INDEX idx_users_email".statementToFileDescription(true)
        )

        // DROP INDEX with IF EXISTS
        Assertions.assertEquals(
            "drop_index_idx_users_email_on_users",
            "DROP INDEX IF EXISTS idx_users_email ON users".statementToFileDescription(false)
        )

        // DROP INDEX with quoted identifiers
        Assertions.assertEquals(
            "DROP_INDEX_IDX_ITEMS_ORDER_ID", "DROP INDEX \"idx_items_order_id\"".statementToFileDescription(true)
        )
    }

    @Test
    fun `test complex SQL statements`() {
        // Complex CREATE TABLE with multiple columns and constraints
        Assertions.assertEquals(
            "CREATE_TABLE_USERS",
            """
            CREATE TABLE users (
                user_id BIGSERIAL PRIMARY KEY,
                name VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL,
                role VARCHAR(50) NOT NULL,
                salt bytea NOT NULL,
                password_hash bytea NOT NULL,
                expires_at TIMESTAMP NOT NULL
            )
            """.trimIndent().statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "CREATE_TABLE_CHAT_MEMORIES",
            """
            CREATE TABLE IF NOT EXISTS chat_memories (
              memory_id BIGSERIAL PRIMARY KEY,
              memory_key VARCHAR(255) NOT NULL,
              messages TEXT NOT NULL
            )"
            """.statementToFileDescription(true)
        )

        // CREATE SEQUENCE followed by CREATE TABLE
        Assertions.assertEquals(
            "CREATE_TABLE_USERS",
            """
            CREATE SEQUENCE users_seq
                START WITH 100
                INCREMENT BY 1;
            CREATE TABLE users (
                user_id INTEGER DEFAULT nextval('users_seq') PRIMARY KEY,
                first_name VARCHAR(50) NOT NULL,
            );
            """.trimIndent().statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "ALTER_TABLE_CHAT_MEMORIES_ADD_CONSTRAINT_CHAT_MEMORIES_MEMORY_KEY_UNIQUE",
            "ALTER TABLE chat_memories ADD CONSTRAINT chat_memories_memory_key_unique UNIQUE (memory_key)"
                .statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "CREATE_SEQUENCE_CHAT_MEMORIES_MEMORY_ID_SEQ",
            "CREATE SEQUENCE IF NOT EXISTS chat_memories_memory_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807".statementToFileDescription(
                true
            )
        )

        // ALTER TABLE with ADD COLUMN + ADD CONSTRAINT
        Assertions.assertEquals(
            "ALTER_TABLE_USERS_ADD_COLUMN_EMAIL",
            """
                ALTER TABLE users ADD COLUMN email VARCHAR(255);
                ALTER TABLE users ADD CONSTRAINT users_email_unique UNIQUE (email)
            """.trimIndent().statementToFileDescription(true)
        )
    }

    @Test
    fun `test edge cases and special characters`() {
        Assertions.assertEquals(
            "CREATE_TABLE_USERS",
            "  CREATE   TABLE   users   (id INT PRIMARY KEY, name VARCHAR(255))  ".statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "CREATE_TABLE_USER_PROFILES",
            "CREATE TABLE \"user_profiles\" (id INT PRIMARY KEY, user_id INT)".statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "CREATE_TABLE_USERPROFILES",
            "CREATE TABLE UserProfiles (id INT PRIMARY KEY, user_id INT)".statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "CREATE_TABLE_USERS",
            """
            CREATE TABLE users (
                user_id BIGSERIAL PRIMARY KEY,
                \"name\" VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL,
                \"role\" VARCHAR(50) NOT NULL,
                salt bytea NOT NULL,
                password_hash bytea NOT NULL,
                expires_at TIMESTAMP NOT NULL
            )
            """.trimIndent().statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "ALTER_TABLE_USERS_ADD_CONSTRAINT_USERS_NAME_UNIQUE",
            "ALTER TABLE users ADD CONSTRAINT users_name_unique UNIQUE (\"name\")".statementToFileDescription(true)
        )
    }

    @Test
    fun `test fallback for unknown statements`() {
        val customSql = "CUSTOM SQL STATEMENT"
        val result = customSql.statementToFileDescription(true)
        Assertions.assertEquals("CUSTOM_STATEMENT_${customSql.hashCode().toString().replace("-", "").take(8)}", result)

        Assertions.assertEquals(
            "INSERT_USERS",
            "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')".statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "UPDATE_USERS", "UPDATE users SET email = 'new@example.com' WHERE id = 1".statementToFileDescription(true)
        )

        Assertions.assertEquals(
            "DELETE_USERS", "DELETE FROM users WHERE id = 1".statementToFileDescription(true)
        )
    }
}

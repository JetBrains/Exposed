package org.jetbrains.exposed.migration.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StatementToFileNameGeneratorTest {

    @Test
    fun `test CREATE TABLE statements`() {
        // Simple CREATE TABLE
        assertEquals(
            "CREATE_TABLE_USERS", "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255))".statementToFileName()
        )

        // CREATE TABLE with IF NOT EXISTS
        assertEquals(
            "CREATE_TABLE_PRODUCTS", "CREATE TABLE IF NOT EXISTS products (id INT PRIMARY KEY, name VARCHAR(255))".statementToFileName()
        )

        // CREATE TABLE with schema prefix
        assertEquals(
            "CREATE_TABLE_ORDERS", "CREATE TABLE public.orders (id INT PRIMARY KEY, customer_id INT)".statementToFileName()
        )

        // CREATE TABLE with quoted identifiers
        assertEquals(
            "CREATE_TABLE_ITEMS", "CREATE TABLE \"items\" (id INT PRIMARY KEY, order_id INT)".statementToFileName()
        )
    }

    @Test
    fun `test ALTER TABLE statements`() {
        // Simple ALTER TABLE
        assertEquals(
            "ALTER_TABLE_USERS", "ALTER TABLE users ADD COLUMN email VARCHAR(255)".statementToFileName()
        )

        // ALTER TABLE with ADD CONSTRAINT
        assertEquals(
            "ALTER_TABLE_USERS_ADD_CONSTRAINT_USERS_EMAIL_UNIQUE", "ALTER TABLE users ADD CONSTRAINT users_email_unique UNIQUE (email)".statementToFileName()
        )

        // ALTER TABLE with schema prefix
        assertEquals(
            "ALTER_TABLE_PRODUCTS", "ALTER TABLE public.products ADD COLUMN price DECIMAL(10,2)".statementToFileName()
        )

        // ALTER TABLE with quoted identifiers
        assertEquals(
            "ALTER_TABLE_ITEMS", "ALTER TABLE \"items\" ADD COLUMN quantity INT".statementToFileName()
        )
    }

    @Test
    fun `test CREATE SEQUENCE statements`() {
        // Simple CREATE SEQUENCE
        assertEquals(
            "CREATE_SEQUENCE_USERS_ID_SEQ", "CREATE SEQUENCE users_id_seq".statementToFileName()
        )

        // CREATE SEQUENCE with IF NOT EXISTS
        assertEquals(
            "CREATE_SEQUENCE_PRODUCTS_ID_SEQ", "CREATE SEQUENCE IF NOT EXISTS products_id_seq".statementToFileName()
        )

        // CREATE SEQUENCE with schema prefix
        assertEquals(
            "CREATE_SEQUENCE_ORDERS_ID_SEQ", "CREATE SEQUENCE public.orders_id_seq".statementToFileName()
        )

        // CREATE SEQUENCE with quoted identifiers
        assertEquals(
            "CREATE_SEQUENCE_ITEMS_ID_SEQ", "CREATE SEQUENCE \"items_id_seq\"".statementToFileName()
        )

        // CREATE SEQUENCE with options
        assertEquals(
            "CREATE_SEQUENCE_USERS_ID_SEQ", "CREATE SEQUENCE users_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807".statementToFileName()
        )
    }

    @Test
    fun `test DROP TABLE statements`() {
        // Simple DROP TABLE
        assertEquals(
            "DROP_TABLE_USERS", "DROP TABLE users".statementToFileName()
        )

        // DROP TABLE with IF EXISTS
        assertEquals(
            "DROP_TABLE_PRODUCTS", "DROP TABLE IF EXISTS products".statementToFileName()
        )

        // DROP TABLE with schema prefix
        assertEquals(
            "DROP_TABLE_ORDERS", "DROP TABLE public.orders".statementToFileName()
        )

        // DROP TABLE with quoted identifiers
        assertEquals(
            "DROP_TABLE_ITEMS", "DROP TABLE \"items\"".statementToFileName()
        )
    }

    @Test
    fun `test CREATE INDEX statements`() {
        // Simple CREATE INDEX
        assertEquals(
            "CREATE_INDEX_IDX_USERS_EMAIL_ON_USERS", "CREATE INDEX idx_users_email ON users (email)".statementToFileName()
        )

        // CREATE INDEX with schema prefix
        assertEquals(
            "CREATE_INDEX_IDX_PRODUCTS_NAME_ON_PRODUCTS", "CREATE INDEX idx_products_name ON public.products (name)".statementToFileName()
        )

        // CREATE INDEX with quoted identifiers
        assertEquals(
            "CREATE_INDEX_IDX_ITEMS_ORDER_ID_ON_ITEMS", "CREATE INDEX \"idx_items_order_id\" ON \"items\" (order_id)".statementToFileName()
        )
    }

    @Test
    fun `test complex SQL statements`() {
        // Complex CREATE TABLE with multiple columns and constraints
        assertEquals(
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
            """.trimIndent().statementToFileName()
        )

        // Complex ALTER TABLE with multiple constraints
        assertEquals(
            "ALTER_TABLE_USERS_ADD_CONSTRAINT_USERS_EMAIL_UNIQUE",
            """
            ALTER TABLE users
            ADD CONSTRAINT users_email_unique UNIQUE (email)
            """.trimIndent().statementToFileName()
        )
    }

    @Test
    fun `test edge cases and special characters`() {
        assertEquals(
            "CREATE_TABLE_USERS",
            "  CREATE   TABLE   users   (id INT PRIMARY KEY, name VARCHAR(255))  ".statementToFileName()
        )

        assertEquals(
            "CREATE_TABLE_USER_PROFILES",
            "CREATE TABLE \"user_profiles\" (id INT PRIMARY KEY, user_id INT)".statementToFileName()
        )

        assertEquals(
            "CREATE_TABLE_USERPROFILES",
            "CREATE TABLE UserProfiles (id INT PRIMARY KEY, user_id INT)".statementToFileName()
        )
    }

    @Test
    fun `test fallback for unknown statements`() {
        val customSql = "CUSTOM SQL STATEMENT"
        val result = customSql.statementToFileName()
        assertEquals("CUSTOM_STATEMENT_${customSql.hashCode().toString().replace("-", "").take(8)}", result)

        assertEquals(
            "INSERT_USERS", "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')".statementToFileName()
        )

        assertEquals(
            "UPDATE_USERS", "UPDATE users SET email = 'new@example.com' WHERE id = 1".statementToFileName()
        )

        assertEquals(
            "DELETE_USERS", "DELETE FROM users WHERE id = 1".statementToFileName()
        )
    }

    @Test
    fun `test examples from comments`() {
        assertEquals(
            "CREATE_TABLE_CHAT_MEMORIES",
            """
            CREATE TABLE IF NOT EXISTS chat_memories (
              memory_id BIGSERIAL PRIMARY KEY,
              memory_key VARCHAR(255) NOT NULL,
              messages TEXT NOT NULL
            )"
            """.statementToFileName()
        )

        assertEquals(
            "ALTER_TABLE_CHAT_MEMORIES_ADD_CONSTRAINT_CHAT_MEMORIES_MEMORY_KEY_UNIQUE",
            "ALTER TABLE chat_memories ADD CONSTRAINT chat_memories_memory_key_unique UNIQUE (memory_key)"
                .statementToFileName()
        )

        assertEquals(
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
            """.trimIndent().statementToFileName()
        )

        assertEquals(
            "ALTER_TABLE_USERS_ADD_CONSTRAINT_USERS_NAME_UNIQUE",
            "ALTER TABLE users ADD CONSTRAINT users_name_unique UNIQUE (\"name\")".statementToFileName()
        )

        assertEquals(
            "ALTER_TABLE_USERS_ADD_CONSTRAINT_USERS_EMAIL_UNIQUE",
            "ALTER TABLE users ADD CONSTRAINT users_email_unique UNIQUE (email)".statementToFileName()
        )

        assertEquals(
            "CREATE_SEQUENCE_CHAT_MEMORIES_MEMORY_ID_SEQ",
            "CREATE SEQUENCE IF NOT EXISTS chat_memories_memory_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807".statementToFileName()
        )

        assertEquals(
            "CREATE_SEQUENCE_USERS_USER_ID_SEQ", "CREATE SEQUENCE users_user_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807".statementToFileName()
        )
    }
}

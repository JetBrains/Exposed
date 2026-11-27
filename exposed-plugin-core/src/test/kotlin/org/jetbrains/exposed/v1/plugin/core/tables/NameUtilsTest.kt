package org.jetbrains.exposed.v1.plugin.core.tables

import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.MySqlSyntax
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameUtilsTest {
    @Test
    fun testObjectNamesFromTableNames() {
        assertEquals("Users", "USERS".toObjectName())
        assertEquals("Users", "users".toObjectName())
        assertEquals("UserAccounts", "USER_ACCOUNTS".toObjectName())
        assertEquals("UserAccounts", "user_accounts".toObjectName())
        assertEquals("UserAccounts", "userAccounts".toObjectName())
        assertEquals("UserAccounts", "UserAccounts".toObjectName())
        assertEquals("UserAccounts", "user-accounts".toObjectName())
        assertEquals("UserAccounts", "user accounts".toObjectName())
        assertEquals("_2faCodes", "2FA_CODES".toObjectName())
        assertEquals("_", "".toObjectName())
    }

    @Test
    fun testPropertyNamesFromColumnNames() {
        assertEquals("id", "ID".toPropertyName())
        assertEquals("id", "id".toPropertyName())
        assertEquals("id2", "ID2".toPropertyName())
        assertEquals("firstName", "FIRST_NAME".toPropertyName())
        assertEquals("firstName", "first_name".toPropertyName())
        assertEquals("firstName", "firstName".toPropertyName())
        assertEquals("firstName", "FirstName".toPropertyName())
        assertEquals("firstName", "first-name".toPropertyName())
        assertEquals("_1stPlace", "1ST_PLACE".toPropertyName())
    }

    @Test
    fun testGeneratedConstraintNamesForMySQL() {
        // a primary key is always named PRIMARY, and an unnamed foreign key <table>_ibfk_<n>
        assertTrue(MySqlSyntax.isGeneratedConstraintName("PRIMARY", "orders", listOf("id")))
        assertTrue(MySqlSyntax.isGeneratedConstraintName("orders_ibfk_1", "orders", listOf("user_id")))
        assertTrue(MySqlSyntax.isGeneratedConstraintName("orders_ibfk_12", "orders", listOf("user_id")))

        // an unnamed UNIQUE constraint is named after its first column, with a number when that name is taken
        assertTrue(MySqlSyntax.isGeneratedConstraintName("code", "orders", listOf("code")))
        assertTrue(MySqlSyntax.isGeneratedConstraintName("a", "orders", listOf("a", "b")))
        assertTrue(MySqlSyntax.isGeneratedConstraintName("code_2", "orders", listOf("code")))

        assertFalse(MySqlSyntax.isGeneratedConstraintName("fk_orders_user", "orders", listOf("user_id")))
        assertFalse(MySqlSyntax.isGeneratedConstraintName("uq_code", "orders", listOf("code")))
        assertFalse(MySqlSyntax.isGeneratedConstraintName("users_ibfk_1", "orders", listOf("user_id")))
        assertFalse(MySqlSyntax.isGeneratedConstraintName("code_unique", "orders", listOf("code")))
    }
}

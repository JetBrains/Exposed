package org.jetbrains.exposed.v1.plugin.core.tables

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
}

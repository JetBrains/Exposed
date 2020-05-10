package org.jetbrains.exposed.sql.tests.mysql

import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.tests.RepeatableTestRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse

class MysqlTests : DatabaseTestsBase() {

    @get:Rule
    val repeatRule = RepeatableTestRule()
    
    @Test
    fun testEmbeddedConnection() {
        withDb(TestDB.MYSQL) {
            assertFalse(TransactionManager.current().exec("SELECT VERSION();") { it.next(); it.getString(1) }.isNullOrEmpty())
        }
    }
}
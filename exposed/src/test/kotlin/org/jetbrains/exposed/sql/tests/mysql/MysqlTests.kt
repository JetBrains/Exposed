package org.jetbrains.exposed.sql.tests.mysql

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.h2.H2Tests
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import org.jetbrains.exposed.test.utils.RepeatableTest
import org.jetbrains.exposed.test.utils.RepeatableTestRule
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
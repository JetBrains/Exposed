package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThreadLocalManagerTest : DatabaseTestsBase() {
    @Test
    fun testReconnection() {
        var secondThreadTm: TransactionManager? = null
        var isMysql = false
        withDb(TestDB.MYSQL) {
            isMysql = true
            SchemaUtils.create(DMLTestsData.Cities)
            val firstThreadTm = TransactionManager.currentThreadManager.get()
            thread {
                withDb(TestDB.MYSQL) {
                    DMLTestsData.Cities.selectAll().toList()
                    secondThreadTm = TransactionManager.currentThreadManager.get()
                    assertNotEquals(firstThreadTm, secondThreadTm)
                }
            }.join()
            assertEquals(firstThreadTm, TransactionManager.currentThreadManager.get())
        }
        if (isMysql) {
            assertEquals(secondThreadTm, TransactionManager.currentThreadManager.get())
        }
    }

}
package org.jetbrains.exposed.v1.r2dbc.sql.tests.h2

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.LogDbInTestName
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.Assume
import org.junit.Test

class ConnectionPoolTests : LogDbInTestName() {
    private val maximumPoolSize = 10

    private val h2PoolDB1 by lazy {
        R2dbcDatabase.connect("r2dbc:pool:h2:mem:///poolDB1?maxSize=$maximumPoolSize")
    }

    @Test
    fun testSuspendTransactionsExceedingPoolSize() = runTest {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        suspendTransaction(h2PoolDB1) {
            SchemaUtils.create(TestTable)
        }

        val exceedsPoolSize = (maximumPoolSize * 2 + 1).coerceAtMost(50)
        repeat(exceedsPoolSize) { i ->
            suspendTransaction {
                delay(100)
//                TestEntity.new { testValue = "test$it" }
                TestTable.insert { it[TestTable.testValue] = "test$i" }
            }
        }

        suspendTransaction(h2PoolDB1) {
//            assertEquals(exceedsPoolSize, TestEntity.all().toList().count())
            assertEquals(exceedsPoolSize, TestTable.selectAll().toList().count())

            SchemaUtils.drop(TestTable)
        }
    }

    object TestTable : IntIdTable("POOL_TESTER") {
        val testValue = varchar("test_value", 32)
    }

//    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
//        companion object : IntEntityClass<TestEntity>(org.jetbrains.exposed.v1.sql.tests.h2.ConnectionPoolTests.TestTable)
//
//        var testValue by org.jetbrains.exposed.v1.sql.tests.h2.ConnectionPoolTests.TestTable.testValue
//    }
}

package org.jetbrains.exposed.v1.dao.r2dbc.tests.h2

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.tests.LogDbInTestName
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class ConnectionPoolTests : LogDbInTestName() {
    private val maximumPoolSize = 10

    private val h2PoolDB1 by lazy {
        R2dbcDatabase.connect("r2dbc:pool:h2:mem:///daoPoolDB1?maxSize=$maximumPoolSize")
    }

    @Test
    fun testSuspendTransactionsExceedingPoolSize() = runTest {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        suspendTransaction(h2PoolDB1) {
            SchemaUtils.create(TestTable)
        }

        val exceedsPoolSize = (maximumPoolSize * 2 + 1).coerceAtMost(50)
        repeat(exceedsPoolSize) { i ->
            launch {
                suspendTransaction(h2PoolDB1) {
                    delay(100.milliseconds)
                    TestEntity.newSuspend { testValue = "test$i" }
                }
            }
            // otherwise runTest skips delays
            testScheduler.advanceUntilIdle()
        }

        suspendTransaction(h2PoolDB1) {
            assertEquals(exceedsPoolSize, TestEntity.all().toList().count())

            SchemaUtils.drop(TestTable)
        }
    }

    object TestTable : IntIdTable("DAO_POOL_TESTER") {
        val testValue = varchar("test_value", 32)
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<TestEntity>(TestTable)

        var testValue by TestTable.testValue
    }
}

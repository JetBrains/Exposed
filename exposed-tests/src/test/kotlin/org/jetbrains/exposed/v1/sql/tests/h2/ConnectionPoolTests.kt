package org.jetbrains.exposed.v1.sql.tests.h2

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.sql.tests.LogDbInTestName
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.junit.Assume
import org.junit.Test

class ConnectionPoolTests : LogDbInTestName() {
    private val hikariDataSource1 by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:hikariDB1"
                maximumPoolSize = 10
            }
        )
    }

    private val hikariDB1 by lazy {
        Database.connect(hikariDataSource1)
    }

    @Test
    fun testSuspendTransactionsExceedingPoolSize() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        transaction(db = hikariDB1) {
            SchemaUtils.create(TestTable)
        }

        val exceedsPoolSize = (hikariDataSource1.maximumPoolSize * 2 + 1).coerceAtMost(50)
        runBlocking {
            repeat(exceedsPoolSize) {
                launch {
                    newSuspendedTransaction {
                        delay(100)
                        TestEntity.new { testValue = "test$it" }
                    }
                }
            }
        }

        transaction(db = hikariDB1) {
            assertEquals(exceedsPoolSize, TestEntity.all().toList().count())

            SchemaUtils.drop(TestTable)
        }
    }

    object TestTable : IntIdTable("HIKARI_TESTER") {
        val testValue = varchar("test_value", 32)
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<TestEntity>(TestTable)

        var testValue by TestTable.testValue
    }
}

package org.jetbrains.exposed.sql.tests.mysql

import com.mysql.cj.conf.PropertyKey
import com.mysql.cj.jdbc.ConnectionImpl
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.RepeatableTestRule
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.demo.sql.Cities
import org.jetbrains.exposed.sql.tests.demo.sql.Cities.autoIncrement
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class MysqlTests : DatabaseTestsBase() {

    @get:Rule
    val repeatRule = RepeatableTestRule()

    @Test
    fun testEmbeddedConnection() {
        withDb(TestDB.MYSQL) {
            assertFalse(
                TransactionManager.current().exec("SELECT VERSION();") {
                    it.next()
                    it.getString(1)
                }.isNullOrEmpty()
            )
        }
    }

    @Test
    fun testBatchInsertWithRewriteBatchedStatementsOn() {
        val mysqlOnly = TestDB.enabledDialects() - TestDB.MYSQL
        withTables(excludeSettings = mysqlOnly, DMLTestsData.Cities) {
            val mysqlConnection = this.connection.connection as ConnectionImpl
            mysqlConnection.propertySet.getBooleanProperty(PropertyKey.rewriteBatchedStatements).value = true
            val cityNames = listOf("FooCity", "BarCity")
            val generatedValues = DMLTestsData.Cities.batchInsert(cityNames) { city ->
                this[DMLTestsData.Cities.name] = city
            }

            assertEquals(cityNames.size, generatedValues.size)
            generatedValues.forEach {
                assertNotNull(it.getOrNull(DMLTestsData.Cities.id))
            }
        }
    }

    object Cities : Table() {
        val id: Column<Int> = integer("id").autoIncrement()
        val name: Column<String?> = varchar("name", 50).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun mysqlStringOrdering() {
        withDb(TestDB.MYSQL) {
            SchemaUtils.create(Cities)
            Cities.insert {
                it[name] = "a"
            }
            Cities.insert {
                it[name] = "B"
            }
            Cities.insert {
                it[name] = "b"
            }
            Cities.insert {
                it[name] = null
            }
            Cities.insert {
                it[name] = "c"
            }
            fun assertOrdered(expected: List<Int>, order: SortOrder) {
                val ordered = Cities.select(Cities.id).orderBy(Cities.name, order).map { it[Cities.id] }
                assertEquals(expected, ordered)
            }
            assertOrdered(listOf(5, 2, 3, 1, 4), SortOrder.DESC_NULLS_LAST) // c, B, b, a, null
            assertOrdered(listOf(1, 2, 3, 5, 4), SortOrder.ASC_NULLS_LAST) // a, B, b, c, null
            assertOrdered(listOf(4, 5, 2, 3, 1), SortOrder.DESC_NULLS_FIRST) // null, c, B, b, a
            assertOrdered(listOf(4, 1, 2, 3, 5), SortOrder.ASC_NULLS_FIRST) // null, a, B, b, c
        }
    }
}

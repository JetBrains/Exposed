package org.jetbrains.exposed.sql.tests.mysql

import com.mysql.cj.conf.PropertyKey
import com.mysql.cj.jdbc.ConnectionImpl
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.sql.transactions.JdbcTransaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MysqlTests : DatabaseTestsBase() {
    @Test
    fun testEmbeddedConnection() {
        withDb(TestDB.MYSQL_V5) {
            assertFalse(
                (TransactionManager.current() as JdbcTransaction).exec("SELECT VERSION();") {
                    it.next()
                    it.getString(1)
                }.isNullOrEmpty()
            )
        }
    }

    @Test
    fun testBatchInsertWithRewriteBatchedStatementsOn() {
        val mysqlOnly = TestDB.enabledDialects() - TestDB.MYSQL_V8
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

    private class IndexHintQuery(
        val source: Query,
        val indexHint: String
    ) : Query(source.set, source.where) {

        init {
            source.copyTo(this)
        }

        override fun prepareSQL(builder: QueryBuilder): String {
            val originalSql = super.prepareSQL(builder)
            val fromTableSql = " FROM ${transaction.identity(set.source as Table)} "
            return originalSql.replace(fromTableSql, "$fromTableSql$indexHint ")
        }

        override fun copy(): IndexHintQuery = IndexHintQuery(source.copy(), indexHint).also { copy ->
            copyTo(copy)
        }
    }

    private fun Query.indexHint(hint: String) = IndexHintQuery(this, hint)

    @Test
    fun testCustomSelectQueryWithHint() {
        val tester = object : IntIdTable("tester") {
            val item = varchar("item", 32).uniqueIndex()
            val amount = integer("amount")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_MYSQL_MARIADB, tester) {
            val originalText = "Original SQL"
            val originalQuery = tester.selectAll().withDistinct().where { tester.id eq 2 }.limit(1).comment(originalText)

            val hint1 = "FORCE INDEX (PRIMARY)"
            val hintQuery1 = originalQuery.indexHint(hint1)
            val hintQuery1Sql = hintQuery1.prepareSQL(this)
            assertTrue { listOf(originalText, hint1, " WHERE ", " DISTINCT ", " LIMIT ").all { it in hintQuery1Sql } }
            hintQuery1.toList()

            val itemIndex = tester.indices.first { it.columns == listOf(tester.item) }.indexName
            val hint2 = "USE INDEX ($itemIndex)"
            val hintQuery2 = tester
                .selectAll()
                .indexHint(hint2)
                .where { (tester.id eq 1) and (tester.item eq "Item A") }
                .groupBy(tester.id)
                .having { tester.id.count() eq 1 }
                .orderBy(tester.amount)
            val hintQuery2Sql = hintQuery2.prepareSQL(this)
            assertTrue { listOf(hint2, " WHERE ", " GROUP BY ", " HAVING ", " ORDER BY ").all { it in hintQuery2Sql } }
            hintQuery2.toList()
        }
    }
}

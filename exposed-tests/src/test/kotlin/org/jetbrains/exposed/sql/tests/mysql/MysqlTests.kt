package org.jetbrains.exposed.sql.tests.mysql

import com.mysql.cj.conf.PropertyKey
import com.mysql.cj.jdbc.ConnectionImpl
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.expect

class MysqlTests : DatabaseTestsBase() {
    @Test
    fun testEmbeddedConnection() {
        withDb(TestDB.MYSQL_V5) {
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

    @Test
    fun testSelectWithOptimizerHintComment() {
        val tester = object : Table("tester") {
            val seconds = integer("seconds")
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_MYSQL_MARIADB, tester) { testDb ->
            tester.insert { it[seconds] = 1 }

            // SLEEP(N) pauses execution for N seconds & returns 0 if no interruption
            val sleepNSeconds: CustomFunction<Int?> = tester.seconds.function("SLEEP")
            val queryWithoutHint = tester.select(sleepNSeconds)
            assertEquals(0, queryWithoutHint.single()[sleepNSeconds])

            tester.update { it[seconds] = 2 }

            // Hint places a limit of N milliseconds on how long a query should take before termination
            val queryWithHint = tester
                .select(sleepNSeconds)
                .comment("+ MAX_EXECUTION_TIME(1000) ", AbstractQuery.CommentPosition.AFTER_SELECT)
            if (testDb in TestDB.ALL_MYSQL) {
                // Query execution was interrupted, max statement execution time exceeded
                expectException<ExposedSQLException> {
                    queryWithHint.single()
                }
            } else {
                // MariaDB has much fewer optimizer hint options and, like any other db, will just ignore the comment
                expect(0) {
                    queryWithHint.single()[sleepNSeconds]
                }
            }
        }
    }
}

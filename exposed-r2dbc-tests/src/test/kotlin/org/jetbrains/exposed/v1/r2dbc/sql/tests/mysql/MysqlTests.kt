package org.jetbrains.exposed.v1.r2dbc.sql.tests.mysql

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.MySQL
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.MySQL.ForUpdate
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.MySQL.MODE
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MysqlTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testEmbeddedConnection() {
        withDb(TestDB.MYSQL_V5) {
            assertFalse(
                org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current().exec("SELECT VERSION();") {
                    it.getString(1)
                }?.firstOrNull()
                    .isNullOrEmpty()
            )
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
            // Query execution was interrupted, max statement execution time exceeded
            expectException<ExposedR2dbcException> {
                queryWithHint.single()
            }
        }
    }

    @Test
    fun testForUpdateOptionsSyntax() {
        val table = object : IntIdTable() {
            val name = varchar("name", 50)
        }

        val id = 1

        suspend fun Query.city() = map { it[table.name] }.single()

        suspend fun select(option: ForUpdateOption): String {
            return table.selectAll().where { table.id eq id }.forUpdate(option).city()
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.MYSQL_V8, table) {
            val name = "name"
            table.insert {
                it[table.id] = id
                it[table.name] = name
            }
            commit()

            val defaultForUpdateRes = table.selectAll().where { table.id eq id }.city()
            val forUpdateRes = select(option = ForUpdateOption.ForUpdate)
            val forUpdateOfTableRes = select(ForUpdate(ofTables = arrayOf(table)))
            val forShareRes = select(MySQL.ForShare)
            val forShareNoWaitOfTableRes = select(MySQL.ForShare(MODE.NO_WAIT, table))
            val notForUpdateRes = table.selectAll().where { table.id eq id }.notForUpdate().city()

            assertEquals(name, defaultForUpdateRes)
            assertEquals(name, forUpdateRes)
            assertEquals(name, forUpdateOfTableRes)
            assertEquals(name, forShareRes)
            assertEquals(name, forShareNoWaitOfTableRes)
            assertEquals(name, notForUpdateRes)
        }
    }
}

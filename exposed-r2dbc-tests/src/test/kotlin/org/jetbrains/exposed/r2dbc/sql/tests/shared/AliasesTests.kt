package org.jetbrains.exposed.r2dbc.sql.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.r2dbc.sql.tests.shared.dml.withCitiesAndUsers
import org.jetbrains.exposed.sql.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AliasesTests : R2dbcDatabaseTestsBase() {
    @Test
    fun `test_github_issue_379_count_alias_ClassCastException`() {
        val stables = object : UUIDTable("Stables") {
            val name = varchar("name", 256).uniqueIndex()
        }

        val facilities = object : UUIDTable("Facilities") {
            val stableId = reference("stable_id", stables)
            val name = varchar("name", 256)
        }

        withTables(facilities, stables) {
            val stable1Id = stables.insertAndGetId {
                it[stables.name] = "Stables1"
            }
            stables.insertAndGetId {
                it[stables.name] = "Stables2"
            }
            facilities.insertAndGetId {
                it[facilities.stableId] = stable1Id
                it[facilities.name] = "Facility1"
            }
            val fcAlias = facilities.name.count().alias("fc")
            val fAlias = facilities.select(facilities.stableId, fcAlias).groupBy(facilities.stableId).alias("f")
            val sliceColumns = stables.columns + fAlias[fcAlias]
            val stats = stables.join(fAlias, JoinType.LEFT, stables.id, fAlias[facilities.stableId])
                .select(sliceColumns)
                .groupBy(*sliceColumns.toTypedArray()).toList().associate {
                    it[stables.name] to it[fAlias[fcAlias]]
                }
            assertEquals(2, stats.size)
            assertEquals(1, stats["Stables1"])
            assertNull(stats["Stables2"])
        }
    }

    @Test
    fun testJoinSubQuery01() {
        withCitiesAndUsers { _, users, _ ->
            val expAlias = users.name.max().alias("m")
            val usersAlias = users.select(users.cityId, expAlias).groupBy(users.cityId).alias("u2")
            val resultRows = Join(users).join(usersAlias, JoinType.INNER, usersAlias[expAlias], users.name).selectAll().toList()
            assertEquals(3, resultRows.size)
        }
    }

    @Test
    fun testJoinSubQuery02() {
        withCitiesAndUsers { _, users, _ ->
            val expAlias = users.name.max().alias("m")

            val query = Join(users).joinQuery(on = { it[expAlias].eq(users.name) }) {
                users.select(users.cityId, expAlias).groupBy(users.cityId)
            }
            val innerExp = query.lastQueryAlias!![expAlias]

            assertEquals("q0", query.lastQueryAlias?.alias)
            assertEquals(3L, query.selectAll().count())
            assertNotNull(query.select(users.columns + innerExp).first()[innerExp])
        }
    }

    @Test
    fun testJoinSubQuery03() {
        withCitiesAndUsers { cities, users, userData ->
            val firstJoinQuery = cities
                .leftJoin(users)
                .joinQuery(
                    on = { it[userData.user_id] eq users.id },
                    joinPart = { userData.selectAll() }
                )
            assertEquals("q0", firstJoinQuery.lastQueryAlias?.alias)

            val secondJoinQuery = firstJoinQuery.joinQuery(
                on = { it[userData.user_id] eq users.id },
                joinPart = { userData.selectAll() }
            )
            assertEquals("q1", secondJoinQuery.lastQueryAlias?.alias)
        }
    }

    @Test
    fun testClientDefaultIsSameInAlias() {
        val tester = object : IntIdTable("tester") {
            val text = text("text").clientDefault { "DEFAULT_TEXT" }
        }

        val aliasTester = tester.alias("alias_tester")

        val default = tester.columns.find { it.name == "text" }?.defaultValueFun
        val aliasDefault = aliasTester.columns.find { it.name == "text" }?.defaultValueFun

        assertEquals(default, aliasDefault)
    }

    @Test
    fun testDefaultExpressionIsSameInAlias() {
        val defaultExpression = stringLiteral("DEFAULT_TEXT")

        val tester = object : IntIdTable("tester") {
            val text = text("text").defaultExpression(defaultExpression)
        }

        val testerAlias = tester.alias("alias_tester")

        val default = tester.columns.find { it.name == "text" }?.defaultValueInDb()
        val aliasDefault = testerAlias.columns.find { it.name == "text" }?.defaultValueInDb()

        assertEquals(default, aliasDefault)
    }

    @Test
    fun testDatabaseGeneratedIsSameInAlias() {
        val tester = object : IntIdTable("tester") {
            val text = text("text").databaseGenerated()
        }

        val testerAlias = tester.alias("alias_tester")

        val default = tester.columns.find { it.name == "text" }?.isDatabaseGenerated()
        val aliasDefault = testerAlias.columns.find { it.name == "text" }?.isDatabaseGenerated()

        assertEquals(default, aliasDefault)
    }

    @Test
    fun testReferenceIsSameInAlias() {
        val stables = object : UUIDTable("Stables") {}

        val facilities = object : UUIDTable("Facilities") {
            val stableId = reference("stable_id", stables)
        }

        withTables(facilities, stables) {
            val facilitiesAlias = facilities.alias("FacilitiesAlias")
            val foreignKey = facilities.columns.find { it.name == "stable_id" }?.foreignKey
            val aliasForeignKey = facilitiesAlias.columns.find { it.name == "stable_id" }?.foreignKey
            assertEquals(foreignKey, aliasForeignKey)
        }
    }

    @Test
    fun testIsNullAndEqWithAliasedIdTable() {
        val tester = object : IntIdTable("tester") {
            val amount = integer("amount")
        }

        withTables(tester) {
            val t1 = tester.insertAndGetId { it[amount] = 99 }

            val counter = tester.alias("counter")

            val result1 = counter.selectAll().where {
                counter[tester.id].isNull() or (counter[tester.id] neq t1)
            }.toList()
            assertTrue { result1.isEmpty() }

            val result2 = counter.selectAll().where {
                counter[tester.id].isNotNull() and (counter[tester.id] eq t1)
            }.single()
            assertEquals(99, result2[counter[tester.amount]])

            val result3 = counter.selectAll().where {
                (counter[tester.id] eq t1.value) or (counter[tester.id] neq 123)
            }.single()
            assertEquals(99, result3[counter[tester.amount]])
        }
    }

    @Test
    fun testAliasFromInternalQuery() {
        val tester1 = object : LongIdTable("tester1") {
            val foo = varchar("foo", 255)
        }

        val tester2 = object : LongIdTable("tester2") {
            val ref = long("ref")
        }

        withTables(tester1, tester2) {
            val id = tester1.insertAndGetId { it[foo] = "foo" }
            tester2.insert { it[ref] = id.value }

            val idAlias = tester1.id.alias("idAlias")
            val fooAlias = tester1.foo.alias("fooAlias")

            val internalQuery = tester1
                .select(idAlias, fooAlias)
                .where { tester1.foo eq "foo" }
                .alias("internalQuery")

            val query = tester2
                .innerJoin(internalQuery, { ref }, { internalQuery[idAlias] })
                .selectAll()

            assertEquals("foo", query.first()[internalQuery[fooAlias]])
        }
    }

    @Test
    fun testExpressionWithColumnTypeAlias() {
        val subInvoices = object : Table("SubInvoices") {
            val productId = long("product_id")
            val mainAmount = decimal("main_amount", 4, 2)
            val isDraft = bool("is_draft")
        }

        withTables(subInvoices) { testDb ->
            subInvoices.insert {
                it[productId] = 1
                it[mainAmount] = 3.5.toBigDecimal()
                it[isDraft] = false
            }

            val inputSum = SqlExpressionBuilder.coalesce(
                subInvoices.mainAmount.sum(), decimalLiteral(BigDecimal.ZERO)
            ).alias("input_sum")

            val input = subInvoices.select(subInvoices.productId, inputSum)
                .where {
                    subInvoices.isDraft eq false
                }.groupBy(subInvoices.productId).alias("input")

            val sumTotal = Expression.build {
                coalesce(input[inputSum], decimalLiteral(BigDecimal.ZERO))
            }.alias("inventory")

            val booleanValue = when (testDb) {
                in TestDB.ALL_ORACLE_LIKE, in TestDB.ALL_SQLSERVER_LIKE -> "0"
                else -> "FALSE"
            }

            val expectedQuery = "SELECT COALESCE(input.input_sum, 0) inventory FROM " +
                """(SELECT ${subInvoices.nameInDatabaseCase()}.${subInvoices.productId.nameInDatabaseCase()}, """ +
                """COALESCE(SUM(${subInvoices.nameInDatabaseCase()}.${subInvoices.mainAmount.nameInDatabaseCase()}), 0) input_sum """ +
                """FROM ${subInvoices.nameInDatabaseCase()} """ +
                """WHERE ${subInvoices.nameInDatabaseCase()}.${subInvoices.isDraft.nameInDatabaseCase()} = $booleanValue """ +
                """GROUP BY ${subInvoices.nameInDatabaseCase()}.${subInvoices.productId.nameInDatabaseCase()}) input"""

            assertEquals(
                expectedQuery,
                input.select(sumTotal).prepareSQL(QueryBuilder(false))
            )
        }
    }
}

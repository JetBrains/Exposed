package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.dml.withCitiesAndUsers
import org.jetbrains.exposed.v1.tests.shared.entities.EntityTestsData
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AliasesTests : DatabaseTestsBase() {
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
                .groupBy(*sliceColumns.toTypedArray()).associate {
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
    fun testWrapRowWithAliasedTable() {
        withTables(EntityTestsData.XTable, EntityTestsData.YTable) {
            val entity1 = EntityTestsData.XEntity.new {
                this.b1 = false
            }

            flushCache()
            entityCache.clear()

            val alias = EntityTestsData.XTable.alias("xAlias")
            val entityFromAlias = alias.selectAll().map { EntityTestsData.XEntity.wrapRow(it, alias) }.singleOrNull()
            assertNotNull(entityFromAlias)
            assertEquals(entity1.id, entityFromAlias.id)
            assertEquals(false, entityFromAlias.b1)
        }
    }

    @Test
    fun testWrapRowWithAliasedQuery() {
        withTables(EntityTestsData.XTable, EntityTestsData.YTable) {
            val entity1 = EntityTestsData.XEntity.new {
                this.b1 = false
            }

            flushCache()
            entityCache.clear()

            val alias = EntityTestsData.XTable.selectAll().alias("xAlias")
            val entityFromAlias = alias.selectAll().map { EntityTestsData.XEntity.wrapRow(it, alias) }.singleOrNull()
            assertNotNull(entityFromAlias)
            assertEquals(entity1.id, entityFromAlias.id)
            assertEquals(false, entityFromAlias.b1)
        }
    }

    @Test
    fun `test aliased expression with aliased query`() {
        withTables(EntityTestsData.XTable, EntityTestsData.YTable) {
            val dataToInsert = listOf(true, true, false, true)
            EntityTestsData.XTable.batchInsert(dataToInsert) {
                this[EntityTestsData.XTable.b1] = it
            }
            val aliasedExpression = EntityTestsData.XTable.id.max().alias("maxId")
            val aliasedQuery = EntityTestsData.XTable
                .select(EntityTestsData.XTable.b1, aliasedExpression)
                .groupBy(EntityTestsData.XTable.b1)
                .alias("maxBoolean")

            val aliasedBool = aliasedQuery[EntityTestsData.XTable.b1]
            val expressionToCheck = aliasedQuery[aliasedExpression]
            assertEquals("maxBoolean.maxId", expressionToCheck.toString())

            val resultQuery = aliasedQuery
                .leftJoin(EntityTestsData.XTable, { this[aliasedExpression] }, { id })
                .select(aliasedBool, expressionToCheck)

            val result = resultQuery.map {
                it[aliasedBool] to it[expressionToCheck]!!.value
            }

            assertEqualCollections(listOf(true to 4, false to 3), result)
        }
    }

    @Test
    fun `test alias for same table with join`() {
        withTables(EntityTestsData.XTable, EntityTestsData.YTable) {
            val table1Count = EntityTestsData.XTable.id.max().alias("t1max")
            val table2Count = EntityTestsData.XTable.id.max().alias("t2max")
            val t1Alias = EntityTestsData.XTable.select(table1Count).groupBy(EntityTestsData.XTable.b1).alias("t1")
            val t2Alias = EntityTestsData.XTable.select(table2Count).groupBy(EntityTestsData.XTable.b1).alias("t2")
            t1Alias.join(t2Alias, JoinType.INNER) {
                t1Alias[table1Count] eq t2Alias[table2Count]
            }.select(t1Alias[table1Count]).toList()
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

            val inputSum = coalesce(
                subInvoices.mainAmount.sum(), decimalLiteral(BigDecimal.ZERO)
            ).alias("input_sum")

            val input = subInvoices.select(subInvoices.productId, inputSum)
                .where {
                    subInvoices.isDraft eq false
                }.groupBy(subInvoices.productId).alias("input")

            val sumTotal = coalesce(
                input[inputSum],
                decimalLiteral(BigDecimal.ZERO),
            ).alias("inventory")

            val booleanValue = when (testDb) {
                TestDB.SQLITE, in TestDB.ALL_ORACLE_LIKE, in TestDB.ALL_SQLSERVER_LIKE -> "0"
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

    @Test
    fun testExpressionWithColumnTypeAliasCastedToExpression() {
        val tester = object : IntIdTable("Test") {
            val weight = integer("weight")
        }

        val expression = tester.weight.sum()
        val alias: ExpressionWithColumnTypeAlias<Int?> = expression.alias("task_weight")
        val query: QueryAlias = tester.select(alias).alias("all_weight")

        fun Expression<*>.toExpressionString() = QueryBuilder(true)
            .also { this.toQueryBuilder(it) }
            .toString().lowercase()

        withTables(tester) {
            assertEquals("all_weight.task_weight", query[alias].toExpressionString())
            assertEquals("all_weight.task_weight", query[alias as ExpressionWithColumnType<Int?>].toExpressionString())
            assertEquals("all_weight.task_weight", query[alias as Expression<Int?>].toExpressionString())
        }
    }

    @Test
    fun testExpressionAliasCastedToExpression() {
        val tester = object : IntIdTable("Test") {
            val weight = integer("weight")
        }

        val expression: Expression<Int?> = tester.weight.sum()
        val alias: ExpressionAlias<Int?> = expression.alias("task_weight")
        val query: QueryAlias = tester.select(alias).alias("all_weight")

        fun Expression<*>.toExpressionString() = QueryBuilder(true)
            .also { this.toQueryBuilder(it) }
            .toString().lowercase()

        withTables(tester) {
            assertEquals("all_weight.task_weight", query[alias].toExpressionString())
            assertEquals("all_weight.task_weight", query[alias as Expression<Int?>].toExpressionString())
        }
    }

    @Test
    fun testColumnCastedToExpression() {
        val tester = object : IntIdTable("Test") {
            val weight = integer("weight")
        }

        val column: Column<Int> = tester.weight
        val query: QueryAlias = tester.select(column).alias("all_weight")

        fun Expression<*>.toExpressionString() = QueryBuilder(true)
            .also { this.toQueryBuilder(it) }
            .toString().lowercase()

        withTables(tester) {
            assertEquals("all_weight.weight", query[column].toExpressionString())
            assertEquals("all_weight.weight", query[column as ExpressionWithColumnType<Int>].toExpressionString())
            assertEquals("all_weight.weight", query[column as Expression<Int>].toExpressionString())
        }
    }
}

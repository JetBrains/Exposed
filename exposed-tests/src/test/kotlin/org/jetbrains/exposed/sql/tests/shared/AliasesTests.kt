package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.entityCache
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.dml.withCitiesAndUsers
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AliasesTests : DatabaseTestsBase() {
    @Test
    fun `test_github_issue_379_count_alias_ClassCastException`() {
        val Stables = object : UUIDTable("Stables") {
            val name = varchar("name", 256).uniqueIndex()
        }

        val Facilities = object : UUIDTable("Facilities") {
            val stableId = reference("stable_id", Stables)
            val name = varchar("name", 256)
        }

        withTables(Facilities, Stables) {
            val stable1Id = Stables.insertAndGetId {
                it[Stables.name] = "Stables1"
            }
            Stables.insertAndGetId {
                it[Stables.name] = "Stables2"
            }
            Facilities.insertAndGetId {
                it[Facilities.stableId] = stable1Id
                it[Facilities.name] = "Facility1"
            }
            val fcAlias = Facilities.name.count().alias("fc")
            val fAlias = Facilities.slice(Facilities.stableId, fcAlias).selectAll().groupBy(Facilities.stableId).alias("f")
            val sliceColumns = Stables.columns + fAlias[fcAlias]
            val stats = Stables.join(fAlias, JoinType.LEFT, Stables.id, fAlias[Facilities.stableId])
                    .slice(sliceColumns)
                    .selectAll()
                    .groupBy(*sliceColumns.toTypedArray()).map {
                        it[Stables.name] to it[fAlias[fcAlias]]
                    }.toMap()
            assertEquals(2, stats.size)
            assertEquals(1, stats["Stables1"])
            assertNull(stats["Stables2"])
        }
    }

    @Test
    fun testJoinSubQuery01() {
        withCitiesAndUsers { cities, users, userData ->
            val expAlias = users.name.max().alias("m")
            val usersAlias = users.slice(users.cityId, expAlias).selectAll().groupBy(users.cityId).alias("u2")
            val resultRows = Join(users).join(usersAlias, JoinType.INNER, usersAlias[expAlias], users.name).selectAll().toList()
            assertEquals(3, resultRows.size)
        }
    }

    @Test
    fun testJoinSubQuery02() {
        withCitiesAndUsers { cities, users, userData ->
            val expAlias = users.name.max().alias("m")

            val query = Join(users).joinQuery(on = { it[expAlias].eq(users.name) }) {
                users.slice(users.cityId, expAlias).selectAll().groupBy(users.cityId)
            }
            val innerExp = query.lastQueryAlias!![expAlias]

            assertEquals("q0", query.lastQueryAlias?.alias)
            assertEquals(3, query.selectAll().count())
            assertNotNull(query.slice(users.columns + innerExp).selectAll().first()[innerExp])
        }
    }

    @Test
    fun `test wrap row with Aliased table`() {
        withTables(EntityTestsData.XTable) {
            val entity1  = EntityTestsData.XEntity.new {
                this.b1 = false
            }

            flushCache()
            entityCache.data.clear()

            val alias = EntityTestsData.XTable.alias("xAlias")
            val entityFromAlias = alias.selectAll().map { EntityTestsData.XEntity.wrapRow(it, alias) }.singleOrNull()
            assertNotNull(entityFromAlias)
            assertEquals(entity1.id, entityFromAlias.id)
            assertEquals(false, entityFromAlias.b1)
        }
    }

    @Test
    fun `test wrap row with Aliased query`() {
        withTables(EntityTestsData.XTable) {
            val entity1  = EntityTestsData.XEntity.new {
                this.b1 = false
            }

            flushCache()
            entityCache.data.clear()

            val alias = EntityTestsData.XTable.selectAll().alias("xAlias")
            val entityFromAlias = alias.selectAll().map { EntityTestsData.XEntity.wrapRow(it, alias) }.singleOrNull()
            assertNotNull(entityFromAlias)
            assertEquals(entity1.id, entityFromAlias.id)
            assertEquals(false, entityFromAlias.b1)
        }
    }

    @Test
    fun `test aliased expression with aliased query`() {
        withTables(EntityTestsData.XTable) {
            val dataToInsert = listOf(true, true, false, true)
            EntityTestsData.XTable.batchInsert(dataToInsert) {
                this[EntityTestsData.XTable.b1] = it
            }
            val aliasedExpression = EntityTestsData.XTable.id.max().alias("maxId")
            val aliasedQuery = EntityTestsData.XTable.
                slice(EntityTestsData.XTable.b1, aliasedExpression).
                selectAll().
                groupBy(EntityTestsData.XTable.b1).
                alias("maxBoolean")

            val aliasedBool = aliasedQuery[EntityTestsData.XTable.b1]
            val expressionToCheck = aliasedQuery[aliasedExpression]
            assertEquals("maxBoolean.maxId", expressionToCheck.toString())

            val resultQuery = aliasedQuery.
                leftJoin(EntityTestsData.XTable, { this[aliasedExpression]}, { id }).
                slice(aliasedBool, expressionToCheck).
                selectAll()

            val result = resultQuery.map {
                it[aliasedBool] to it[expressionToCheck]!!.value
            }

            assertEqualCollections(listOf(true to 4, false to 3), result)
        }
    }
}
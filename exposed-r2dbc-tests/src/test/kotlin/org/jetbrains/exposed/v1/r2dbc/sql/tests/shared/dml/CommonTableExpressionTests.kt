package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.core.BiCompositeColumn
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.FieldSet
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.allFrom
import org.jetbrains.exposed.v1.core.anyFrom
import org.jetbrains.exposed.v1.core.asCte
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.intParam
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.recursiveCte
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.except
import org.jetbrains.exposed.v1.r2dbc.explain
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.intersect
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.union
import org.jetbrains.exposed.v1.r2dbc.unionAll
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CommonTableExpressionTests : R2dbcDatabaseTestsBase() {
    private object Items : Table("cte_items") {
        val id = integer("id")
        val value = integer("value")

        override val primaryKey = PrimaryKey(id)
    }

    private object CompositeItems : Table("cte_composite_items") {
        val first = Column<Int>(this, "first_value", IntegerColumnType())
        val second = Column<Int>(this, "second_value", IntegerColumnType())
        val pair = registerCompositeColumn(
            object : BiCompositeColumn<Int, Int, Pair<Int, Int>>(
                first,
                second,
                transformFromValue = { it },
                transformToValue = { firstValue, secondValue -> firstValue as Int to secondValue as Int },
            ) {}
        )
    }

    private class UnsnapshottableQuery(override val set: FieldSet) : AbstractQuery<UnsnapshottableQuery>(emptyList())

    private class FailingExpression : ExpressionWithColumnType<Int>() {
        override val columnType = IntegerColumnType()
        var shouldFail = true

        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            check(!shouldFail) { "Deliberate CTE rendering failure" }
            queryBuilder.append("1")
        }
    }

    @Test
    fun testOrdinaryCte() {
        withItems {
            val selected = Items.select(Items.id, Items.value).where { Items.value less 30 }
            val cte = selected.asCte("selected_items")
            val id = cte[Items.id]
            val value = cte[Items.value]

            val rows = cte.select(id, value).withCtes(cte).orderBy(id).map { it[id] to it[value] }.toList()

            assertEquals(listOf(1 to 10, 2 to 20), rows)
        }
    }

    @Test
    fun testDependentCtes() {
        withItems {
            val first = Items.select(Items.id, Items.value).asCte("all_items")
            val firstId = first[Items.id]
            val firstValue = first[Items.value]
            val second = first.select(firstId, firstValue).where { firstValue less 30 }.asCte("selected_items")
            val secondId = second[firstId]

            val rows = second.select(secondId).withCtes(first, second).orderBy(secondId).map { it[secondId] }.toList()

            assertEquals(listOf(1, 2), rows)
            assertFailsWith<IllegalStateException> { second[Items.id] }
        }
    }

    @Test
    fun testCteCanBeJoinedToTable() {
        withItems {
            val cte = Items.select(Items.id).where { Items.value greater 10 }.asCte("selected_items")
            val cteId = cte[Items.id]
            val join = Items.join(cte, JoinType.INNER, Items.id, cteId)

            val ids = join.select(Items.id).orderBy(Items.id).withCtes(cte).map { it[Items.id] }.toList()
            assertEquals(listOf(2, 3), ids)
        }
    }

    @Test
    fun testRecursiveCte() {
        withDb(excludeSettings = listOf(TestDB.MYSQL_V5)) {
            val number = intLiteral(1).alias("n")
            val numbers = recursiveCte("numbers", listOf(number)) { self ->
                val current = self[number]
                val seed = Table.Dual.select(number)
                val next = (current + intLiteral(1)).alias("n")
                val recursive = self.select(next).where { current less 3 }
                seed.unionAll(recursive)
            }
            val result = numbers[number]

            val values = numbers.select(result).withCtes(numbers).orderBy(result).map { it[result] }.toList()
            assertEquals(listOf(1, 2, 3), values)
        }
    }

    @Test
    fun testRecursiveCteWithDistinctUnion() {
        withDb(TestDB.ALL_H2_V2) {
            val number = intLiteral(1).alias("number")
            val numbers = recursiveCte("numbers", listOf(number)) { self ->
                val current = self[number]
                val seed = Table.Dual.select(number)
                val next = (current + intLiteral(1)).alias("number")
                val recursive = self.select(next).where { current less 3 }
                seed.union(recursive)
            }
            val result = numbers[number]

            val values = numbers.select(result).withCtes(numbers).orderBy(result).map { it[result] }.toList()
            assertEquals(listOf(1, 2, 3), values)
        }
    }

    @Test
    fun testRecursiveCteSyntax() {
        withDb(excludeSettings = listOf(TestDB.MYSQL_V5)) { testDb ->
            val number = intLiteral(1).alias("number")
            val numbers = recursiveCte("numbers", listOf(number)) { Table.Dual.select(number) }
            val sql = numbers.select(numbers[number]).withCtes(numbers).prepareSQL(this, prepared = false)
            val expectsRecursiveKeyword = testDb != TestDB.SQLSERVER && testDb != TestDB.ORACLE

            assertEquals(expectsRecursiveKeyword, sql.startsWith("WITH RECURSIVE "))
        }
    }

    @Test
    fun testCountWithCte() {
        withItems {
            val cte = Items.select(Items.value).asCte("item_values")
            val value = cte[Items.value]

            assertEquals(3, cte.selectAll().withCtes(cte).count())
            assertEquals(3, cte.selectAll().withDistinct().withCtes(cte).count())
            assertEquals(3, cte.select(value).groupBy(value).withCtes(cte).count())
            assertEquals(1, cte.select(value).withCtes(cte).limit(1).offset(1).count())
        }
    }

    @Test
    fun testComplexCountRestoresCteStateAfterFailure() {
        withDb(excludeSettings = listOf(TestDB.MYSQL_V5)) {
            val failingExpression = FailingExpression()
            val projected = failingExpression.alias("cte_value")
            val cte = Table.Dual.select(projected).asCte("failing_cte")
            val value = cte[projected]
            val query = cte.select(value).withDistinct().withCtes(cte)

            assertFailsWith<IllegalStateException> { query.count() }

            failingExpression.shouldFail = false
            assertEquals(1, query.count())
            assertTrue(query.prepareSQL(this, prepared = false).startsWith("WITH "))
        }
    }

    @Test
    fun testComplexCountUsesCustomQuerySubclass() {
        withItems {
            class CustomQuery(source: Query) : Query(source.set, source.where) {
                override fun prepareSQL(builder: QueryBuilder): String = error("Custom query SQL")
            }

            val query = CustomQuery(Items.selectAll()).withDistinct()

            assertEquals("Custom query SQL", assertFailsWith<IllegalStateException> { query.count() }.message)
        }
    }

    @Test
    fun testParametersAndComputedAlias() {
        withItems {
            val doubled = (Items.value * intLiteral(2)).alias("doubled")
            val cte = Items.select(Items.id, doubled).where { Items.value greater 10 }.asCte("computed_items")
            val computed = cte[doubled]

            val rows = cte.select(computed).where { computed less 60 }.withCtes(cte).map { it[computed] }.toList()

            assertEquals(listOf(40), rows)
        }
    }

    @Test
    fun testCteOrderAndParameterOrder() {
        withItems {
            val first = Items.select(Items.id).where { Items.value greater intParam(10) }.asCte("first_items")
            val second = Items.select(Items.id).where { Items.value greater intParam(20) }.asCte("second_items")
            val secondId = second[Items.id]
            val query = second.select(secondId).where { secondId less intParam(4) }.withCtes(first, second)

            val sql = query.prepareSQL(this, prepared = true)
            val normalizedSql = sql.uppercase()
            assertTrue(normalizedSql.indexOf("FIRST_ITEMS") < normalizedSql.indexOf("SECOND_ITEMS"))
            assertEquals(listOf(10, 20, 4), query.arguments().single().map { it.second })
            assertTrue(query.prepareSQL(this, prepared = false).contains("20"))
        }
    }

    @Test
    fun testCompositeOutputIsExpanded() {
        withTables(excludeSettings = listOf(TestDB.MYSQL_V5), CompositeItems) {
            CompositeItems.insert { it[pair] = 1 to 2 }
            val cte = CompositeItems.select(CompositeItems.pair).asCte("composite_items")
            val first = cte[CompositeItems.first]
            val second = cte[CompositeItems.second]

            val row = cte.select(first, second).withCtes(cte).map { it[first] to it[second] }.toList().single()
            assertEquals(1 to 2, row)
            val failure = assertFailsWith<IllegalStateException> { cte[CompositeItems.pair] }
            assertTrue(failure.message.orEmpty().contains("component columns"))
        }
    }

    @Test
    fun testReservedWordCteName() {
        withItems {
            val cte = Items.select(Items.id).asCte("select")
            val id = cte[Items.id]

            val values = cte.select(id).withCtes(cte).orderBy(id).map { it[id] }.toList()
            assertEquals(listOf(1, 2, 3), values)
        }
    }

    @Test
    fun testCteReferenceIsDistinctFromPhysicalTableWithSameName() {
        withDb(TestDB.H2_V2) {
            val cte = Items.select(Items.id).asCte(Items.tableName)
            val cteId = cte[Items.id]

            assertNotEquals(Items.id, cteId)
            assertNotEquals(cteId, Items.id)
        }
    }

    @Test
    fun testDefinitionIsSnapshotted() {
        withItems {
            val source = Items.select(Items.id)
            val cte = source.asCte("item_ids")
            source.adjustSelect { select(Items.value) }
            val id = cte[Items.id]

            assertEquals(listOf(1, 2, 3), cte.select(id).withCtes(cte).orderBy(id).map { it[id] }.toList())
        }
    }

    @Test
    fun testSetOperationsWithCte() {
        withItems {
            val cte = Items.select(Items.id).asCte("item_ids")
            val id = cte[Items.id]
            val first = cte.select(id).where { id less 2 }
            val second = cte.select(id).where { id greater 2 }
            val union = first.unionAll(second).withCtes(cte)

            assertEquals(listOf(1, 3), union.map { it[id] }.toList().sorted())
            assertEquals(2, union.count())
        }
    }

    @Test
    fun testWithDistinctRetainsCtesOnUnionAll() {
        withItems {
            val cte = Items.select(Items.id).asCte("item_ids")
            val id = cte[Items.id]
            val union = cte.select(id).where { id less 3 }
                .unionAll(cte.select(id).where { id greater 1 })
                .withCtes(cte)
                .withDistinct()

            assertEquals(listOf(1, 2, 3), union.map { it[id] }.toList().sorted())
        }
    }

    @Test
    fun testCteBearingSetOperationOperandIsRejected() {
        withItems {
            val cte = Items.select(Items.id).asCte("item_ids")
            val id = cte[Items.id]
            val first = cte.select(id).withCtes(cte)
            val union = first.unionAll(Items.select(Items.id))

            assertFailsWith<IllegalArgumentException> {
                union.prepareSQL(this, prepared = false)
            }
        }
    }

    @Test
    fun testSetOperationAsCteDefinition() {
        withItems {
            val itemId = Items.id.alias("item_id")
            val low = Items.select(itemId).where { Items.id less 2 }
            val high = Items.select(itemId).where { Items.id greater 2 }
            val cte = low.unionAll(high).asCte("item_ids")
            val id = cte[itemId]

            assertEquals(listOf(1, 3), cte.select(id).withCtes(cte).orderBy(id).map { it[id] }.toList())
        }
    }

    @Test
    fun testSetOperationDefinitionIsDeeplySnapshotted() {
        withItems {
            val itemId = Items.id.alias("item_id")
            val low = Items.select(itemId).where { Items.id less 2 }
            val high = Items.select(itemId).where { Items.id greater 2 }
            val cte = low.unionAll(high).asCte("item_ids")
            val id = cte[itemId]

            high.adjustWhere { Items.id less 0 }

            assertEquals(listOf(1, 3), cte.select(id).withCtes(cte).orderBy(id).map { it[id] }.toList())
        }
    }

    @Test
    fun testSetOperationSnapshotsPreserveOperation() {
        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2, Items) {
            Items.insert {
                it[id] = 1
                it[value] = 10
            }
            Items.insert {
                it[id] = 2
                it[value] = 20
            }
            val itemId = Items.id.alias("item_id")

            val intersected = Items.select(itemId).where { Items.id less 3 }
                .intersect(Items.select(itemId).where { Items.id eq 1 })
                .asCte("intersected")
            val intersectedValue = intersected[itemId]
            assertEquals(
                listOf(1),
                intersected.select(intersectedValue).withCtes(intersected).map { it[intersectedValue] }.toList()
            )

            val excepted = Items.select(itemId).where { Items.id less 3 }
                .except(Items.select(itemId).where { Items.id eq 2 })
                .asCte("excepted")
            val exceptedValue = excepted[itemId]
            assertEquals(
                listOf(1),
                excepted.select(exceptedValue).withCtes(excepted).map { it[exceptedValue] }.toList()
            )
        }
    }

    @Test
    fun testExplicitGeneratedStyleAliasIsAccepted() {
        withItems {
            val firstValue = (Items.value + intLiteral(1)).alias("exp0")
            val secondValue = (Items.value + intLiteral(2)).alias("exp0")
            val cte = Items.select(firstValue).unionAll(Items.select(secondValue)).asCte("explicit_alias")
            val value = cte[firstValue]

            assertEquals(6, cte.select(value).withCtes(cte).count())
        }
    }

    @Test
    fun testCopyAndEmptyRetainCtes() {
        withItems {
            val cte = Items.select(Items.id).asCte("item_ids")
            val query = cte.selectAll().withCtes(cte)

            assertEquals(3, query.copy().count())
            assertEquals(false, query.empty())
        }
    }

    @Test
    fun testFrontCommentPrecedesWithClause() {
        withItems {
            val cte = Items.select(Items.id).asCte("item_ids")
            val query = cte.selectAll().withCtes(cte).comment("cte-query")
            val sql = query.prepareSQL(this, prepared = false)

            assertTrue(sql.startsWith("/*cte-query*/ WITH "))
            assertEquals(1, Regex("/\\*cte-query\\*/").findAll(sql).count())
        }
    }

    @Test
    fun testInvalidSchemasAreRejected() {
        withItems {
            assertFailsWith<IllegalStateException> {
                Items.select(Items.value + intLiteral(1)).asCte("unnamed")
            }

            val generated = Items.select(Items.value + intLiteral(1))
                .unionAll(Items.select(Items.value + intLiteral(2)))
            assertFailsWith<IllegalArgumentException> { generated.asCte("generated") }

            val duplicate = Items.select(Items.id.alias("same"), Items.value.alias("same")).asCte("duplicate")
            assertFailsWith<IllegalArgumentException> {
                duplicate.selectAll().withCtes(duplicate).prepareSQL(this, prepared = false)
            }
        }
    }

    @Test
    fun testAmbiguousOriginalFieldLookupIsRejected() {
        withItems {
            val first = Items.id.alias("first_id")
            val second = Items.id.alias("second_id")
            val cte = Items.select(first, second).asCte("duplicate_ids")
            val firstId = cte[first]
            val secondId = cte[second]

            assertFailsWith<IllegalStateException> { cte[Items.id] }
            val rows = cte.select(firstId, secondId).withCtes(cte).map { it[firstId] to it[secondId] }.toList()
            assertTrue(rows.all { it.first == it.second })
        }
    }

    @Test
    fun testRecursiveArityMismatchIsRejected() {
        withDb {
            val first = intLiteral(1).alias("first")
            val second = intLiteral(2).alias("second")

            assertFailsWith<IllegalArgumentException> {
                recursiveCte("invalid_recursive", listOf(first, second)) { Table.Dual.select(first) }
            }
        }
    }

    @Test
    fun testRecursiveTypeMismatchIsRejected() {
        withDb {
            val declared = intLiteral(1).alias("value")
            val actual = stringLiteral("invalid").alias("value")

            assertFailsWith<IllegalArgumentException> {
                recursiveCte("invalid_recursive", listOf(declared)) { Table.Dual.select(actual) }
            }
        }
    }

    @Test
    fun testCustomQueryWithoutSnapshotSupportIsRejected() {
        withDb {
            assertFailsWith<IllegalStateException> {
                UnsnapshottableQuery(Table.Dual.select(intLiteral(1).alias("value")).set).asCte("custom")
            }
        }
    }

    @Test
    fun testQuerySubclassWithoutSubtypePreservingCopyIsRejected() {
        withDb {
            class CustomQuery(source: Query) : Query(source.set, source.where) {
                override fun prepareSQL(builder: QueryBuilder): String = error("Custom query SQL")
            }

            val exception = assertFailsWith<IllegalStateException> {
                CustomQuery(Table.Dual.select(intLiteral(1).alias("value"))).asCte("custom")
            }

            assertContains(exception.message.orEmpty(), "copy() must preserve its subtype")
        }
    }

    @Test
    fun testQuerySubclassWithSubtypePreservingCopyCanBeUsedAsCte() {
        withDb(excludeSettings = listOf(TestDB.MYSQL_V5)) {
            class CustomQuery(source: Query) : Query(source.set, source.where) {
                override fun copy(): CustomQuery = CustomQuery(this).also { copyTo(it) }

                override fun prepareSQL(builder: QueryBuilder): String {
                    builder.append("/* custom query */ ")
                    return super.prepareSQL(builder)
                }
            }

            val value = intLiteral(1).alias("value")
            val cte = CustomQuery(Table.Dual.select(value)).asCte("custom")

            val sql = cte.select(cte[value]).withCtes(cte).prepareSQL(this, prepared = false)

            assertContains(sql, "AS (/* custom query */ SELECT")
        }
    }

    @Test
    fun testReferencedCtesMustBeAttachedToOutermostQuery() {
        withDb(excludeSettings = listOf(TestDB.MYSQL_V5)) {
            val value = intLiteral(1).alias("value")
            val first = Table.Dual.select(value).asCte("first_cte")
            val firstValue = first[value]

            val directReference = assertFailsWith<IllegalArgumentException> {
                first.select(firstValue).prepareSQL(this, prepared = false)
            }
            assertContains(directReference.message.orEmpty(), "CTE 'first_cte' is referenced but not attached")

            val second = first.select(firstValue).asCte("second_cte")
            val secondValue = second[firstValue]
            val missingDependency = assertFailsWith<IllegalArgumentException> {
                second.select(secondValue).withCtes(second).prepareSQL(this, prepared = false)
            }
            assertContains(missingDependency.message.orEmpty(), "CTE 'first_cte' is referenced but not attached")
        }
    }

    @Test
    fun testDuplicateCteNamesAreRejected() {
        withItems {
            val first = Items.select(Items.id).asCte("duplicate")
            val second = Items.select(Items.value).asCte("duplicate")

            assertFailsWith<IllegalArgumentException> {
                first.selectAll().withCtes(first, second).prepareSQL(this, prepared = false)
            }
        }
    }

    @Test
    fun testMySql5IsRejectedBeforeExecution() {
        withDb(TestDB.MYSQL_V5) {
            val value = intLiteral(1).alias("value")
            val cte = Table.Dual.select(value).asCte("value_cte")

            assertFailsWith<UnsupportedByDialectException> {
                cte.select(cte[value]).withCtes(cte).prepareSQL(this, prepared = true)
            }
        }
    }

    @Test
    fun testCteCannotBeNestedInQueriesOrStatements() {
        withItems {
            val cte = Items.select(Items.id).asCte("item_ids")
            val id = cte[Items.id]
            val nestedQuery = cte.select(id).withCtes(cte)
            val nestedAlias = nestedQuery.alias("nested")
            val join = Items.join(nestedAlias, JoinType.INNER, Items.id, nestedAlias[id])

            assertFailsWith<IllegalArgumentException> { nestedAlias.selectAll().toList() }
            assertFailsWith<IllegalArgumentException> {
                Items.select(wrapAsExpression<Int>(nestedQuery)).prepareSQL(this, prepared = false)
            }
            assertFailsWith<IllegalArgumentException> {
                Items.selectAll().where { exists(nestedQuery) }.prepareSQL(this, prepared = false)
            }
            assertFailsWith<IllegalArgumentException> {
                Items.selectAll().where { Items.id inSubQuery nestedQuery }.prepareSQL(this, prepared = false)
            }
            assertFailsWith<IllegalArgumentException> {
                Items.selectAll().where { Items.id eq anyFrom<Int>(nestedQuery) }.prepareSQL(this, prepared = false)
            }
            assertFailsWith<IllegalArgumentException> {
                Items.selectAll().where { Items.id eq allFrom<Int>(nestedQuery) }.prepareSQL(this, prepared = false)
            }
            assertFailsWith<IllegalArgumentException> {
                buildStatement { Items.insert(nestedQuery, columns = listOf(Items.id)) }
                    .prepareSQL(this, prepared = false)
            }
            assertFailsWith<IllegalArgumentException> {
                buildStatement { Items.replace(nestedQuery, columns = listOf(Items.id)) }
                    .prepareSQL(this, prepared = false)
            }
            assertFailsWith<IllegalArgumentException> {
                buildStatement {
                    Items.mergeFrom(nestedAlias, on = { Items.id eq nestedAlias[id] }) {}
                }.prepareSQL(this, prepared = false)
            }
            assertFailsWith<IllegalArgumentException> {
                buildStatement { join.update { it[Items.value] = 0 } }.arguments().toList()
            }
            assertFailsWith<IllegalArgumentException> {
                buildStatement { join.delete(Items) }.arguments().toList()
            }
        }
    }

    @Test
    fun testExplainAndTargetTracking() {
        withItems { testDb ->
            val cte = Items.select(Items.id).asCte("SelectedItems")
            val query = cte.selectAll().withCtes(cte)

            assertEquals(listOf(Items), query.targets)
            if (testDb !in TestDB.ALL_SQLSERVER_LIKE + TestDB.ALL_ORACLE_LIKE) {
                assertTrue(explain { query }.toList().isNotEmpty())
            }
        }
    }

    private fun withItems(statement: suspend R2dbcTransaction.(TestDB) -> Unit) {
        withTables(excludeSettings = listOf(TestDB.MYSQL_V5), Items) { testDb ->
            Items.insert {
                it[id] = 1
                it[value] = 10
            }
            Items.insert {
                it[id] = 2
                it[value] = 20
            }
            Items.insert {
                it[id] = 3
                it[value] = 30
            }
            statement(testDb)
        }
    }
}

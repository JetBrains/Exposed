package org.jetbrains.exposed.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.r2dbc.sql.mergeFrom
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MergeSelectTest : MergeBaseTest() {

    private val sourceQuery = Source.selectAll().alias("sub")

    private fun SqlExpressionBuilder.defaultOnCondition() = Dest.key eq sourceQuery[Source.key]

    @Test
    fun testInsert() {
        withMergeTestTablesAndDefaultData { dest, source ->
            dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenNotMatchedInsert {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = sourceQuery[source.value] * 2
                    it[dest.optional] = stringLiteral("optional::") + sourceQuery[source.key]
                }
            }

            val destRow = dest.getByKey("only-in-source-1")
            assertEquals(2, destRow[dest.value])
            assertEquals("optional::only-in-source-1", destRow[dest.optional])
            assertEquals(TEST_DEFAULT_DATE_TIME, destRow[dest.at])
        }
    }

    @Test
    fun testInsertByAlias() {
        withMergeTestTablesAndDefaultData { dest, source ->
            val destAlias = dest.alias("dest_alias")

            destAlias.mergeFrom(
                sourceQuery,
                on = { sourceQuery[source.key] eq destAlias[dest.key] },
            ) {
                whenNotMatchedInsert {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = sourceQuery[source.value] * 2
                }
            }

            val destRow = dest.getByKey("only-in-source-1")
            assertEquals(2, destRow[dest.value])
        }
    }

    @Test
    fun testUpdate() {
        withMergeTestTablesAndDefaultData { dest, source ->
            dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenMatchedUpdate {
                    it[dest.value] = (sourceQuery[source.value] + dest.value) * 2
                    it[dest.optional] = sourceQuery[source.key] + stringLiteral("::") + dest.key
                }
            }
            val destRow = dest.getByKey("in-source-and-dest-1")
            assertEquals(22, destRow[dest.value])
            assertEquals("in-source-and-dest-1::in-source-and-dest-1", destRow[dest.optional])
            assertEquals(TEST_DEFAULT_DATE_TIME, destRow[dest.at])
        }
    }

    @Test
    fun testUpdateByAlias() {
        withMergeTestTablesAndDefaultData { dest, source ->
            val destAlias = dest.alias("dest_alias")

            destAlias.mergeFrom(
                sourceQuery,
                on = { sourceQuery[source.key] eq destAlias[dest.key] },
            ) {
                whenMatchedUpdate {
                    it[dest.value] = (sourceQuery[source.value] + destAlias[dest.value]) * 2
                    it[dest.optional] = sourceQuery[source.key] + stringLiteral("::") + destAlias[dest.key]
                }
            }

            val destRow = dest.getByKey("in-source-and-dest-1")
            assertEquals(22, destRow[dest.value])
            assertEquals("in-source-and-dest-1::in-source-and-dest-1", destRow[dest.optional])
            assertEquals(TEST_DEFAULT_DATE_TIME, destRow[dest.at])
        }
    }

    @Test
    fun testDelete() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.ALL_ORACLE_LIKE) { dest, _ ->
            dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenMatchedDelete()
            }

            assertNull(dest.getByKeyOrNull("in-source-and-dest-1"))
            assertNull(dest.getByKeyOrNull("in-source-and-dest-2"))
            assertNull(dest.getByKeyOrNull("in-source-and-dest-3"))
            assertNull(dest.getByKeyOrNull("in-source-and-dest-4"))
        }
    }

    @Test
    fun testConditionOnInsertAndUpdate() {
        withMergeTestTablesAndDefaultData { dest, source ->
            dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenNotMatchedInsert(and = (sourceQuery[source.value] greater 2)) {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = sourceQuery[source.value]
                }

                whenMatchedUpdate(and = (dest.value greater 20)) {
                    it[dest.value] = sourceQuery[source.value] + dest.value
                }
            }

            assertNull(dest.getByKeyOrNull("only-in-source-1"))
            assertNull(dest.getByKeyOrNull("only-in-source-2"))
            assertNotNull(dest.getByKeyOrNull("only-in-source-3"))
            assertNotNull(dest.getByKeyOrNull("only-in-source-4"))

            assertEquals(10, dest.getByKey("in-source-and-dest-1")[dest.value])
            assertEquals(20, dest.getByKey("in-source-and-dest-2")[dest.value])
            assertEquals(33, dest.getByKey("in-source-and-dest-3")[dest.value])
            assertEquals(44, dest.getByKey("in-source-and-dest-4")[dest.value])
        }
    }

    @Test
    fun testConditionOnDelete() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.ALL_ORACLE_LIKE) { dest, source ->
            dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenMatchedDelete(and = (sourceQuery[source.value] greater 2) and (dest.value greater 20))
            }

            assertNotNull(dest.getByKeyOrNull("in-source-and-dest-1"))
            assertNotNull(dest.getByKeyOrNull("in-source-and-dest-2"))
            assertNull(dest.getByKeyOrNull("in-source-and-dest-3"))
            assertNull(dest.getByKeyOrNull("in-source-and-dest-4"))
        }
    }

    @Test
    fun testMultipleClauses() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.ALL_ORACLE_LIKE + TestDB.ALL_SQLSERVER_LIKE) { dest, source ->
            dest.mergeFrom(sourceQuery, on = { defaultOnCondition() }) {
                whenNotMatchedInsert(and = (sourceQuery[source.value] eq 1)) {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = sourceQuery[source.value]
                    it[dest.optional] = "one"
                }
                whenNotMatchedInsert(and = (sourceQuery[source.value] eq 2)) {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = sourceQuery[source.value]
                    it[dest.optional] = "two"
                }
                whenNotMatchedInsert {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = sourceQuery[source.value]
                    it[dest.optional] = "three-and-more"
                }

                whenMatchedDelete(and = (sourceQuery[source.value] eq 1))
                whenMatchedUpdate(and = (sourceQuery[source.value] eq 1)) {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = (dest.value + sourceQuery[source.value]) * 10
                }
                whenMatchedUpdate(and = (sourceQuery[source.value] eq 2)) {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = (dest.value + sourceQuery[source.value]) * 100
                }
                whenMatchedDelete(and = (sourceQuery[source.value] eq 3))

                whenMatchedUpdate {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = 1000
                }
            }

            assertEquals("one", dest.getByKey("only-in-source-1")[dest.optional])
            assertEquals("two", dest.getByKey("only-in-source-2")[dest.optional])
            assertEquals("three-and-more", dest.getByKey("only-in-source-3")[dest.optional])
            assertEquals("three-and-more", dest.getByKey("only-in-source-4")[dest.optional])

            assertNull(dest.getByKeyOrNull("in-source-and-dest-1"))
            assertEquals(2200, dest.getByKey("in-source-and-dest-2")[dest.value])
            assertNull(dest.getByKeyOrNull("in-source-and-dest-3"))
            assertEquals(1000, dest.getByKey("in-source-and-dest-4")[dest.value])
        }
    }

    @Test
    fun testDoNothingInPostgres() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES) { dest, source ->
            dest.mergeFrom(sourceQuery, on = { defaultOnCondition() }) {
                whenNotMatchedDoNothing(and = sourceQuery[source.value] greater 1)
                whenNotMatchedInsert {
                    it[dest.key] = sourceQuery[source.key]
                    it[dest.value] = sourceQuery[source.value] + 100
                }
            }

            assertEquals(101, dest.selectAll().where { dest.key eq "only-in-source-1" }.first()[dest.value])
            assertNull(dest.selectAll().where { dest.key inList listOf("only-in-source-2", "only-in-source-3") }.firstOrNull())
        }
    }

    @Test
    fun testMergeFromWithConstCondition() {
        val filteredSourceQuery = Source.selectAll().where { Source.key eq "only-in-source-1" }.alias("sub")

        withMergeTestTablesAndDefaultData { dest, source ->
            dest.mergeFrom(
                filteredSourceQuery,
                on = { Dest.key eq filteredSourceQuery[Source.key] },
            ) {
                whenNotMatchedInsert {
                    it[dest.key] = filteredSourceQuery[source.key]
                    it[dest.value] = filteredSourceQuery[source.value]
                }
            }

            assertEquals(1, dest.getByKey("only-in-source-1")[dest.value])
            assertNull(dest.selectAll().where { Dest.key eq "only-in-source-2" }.firstOrNull())
        }
    }
}

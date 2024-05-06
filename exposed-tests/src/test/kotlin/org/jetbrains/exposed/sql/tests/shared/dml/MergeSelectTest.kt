package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
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
        withMergeTestTablesAndDefaultData {
            Dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenNotMatchedInsert {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = sourceQuery[Source.value] * 2
                    it[Dest.optional] = stringLiteral("optional::") + sourceQuery[Source.key]
                }
            }

            val dest = Dest.getByKey("only-in-source-1")
            assertEquals(2, dest[Dest.value])
            assertEquals("optional::only-in-source-1", dest[Dest.optional])
            assertEquals(TEST_DEFAULT_DATE_TIME, dest[Dest.at])
        }
    }

    @Test
    fun testInsertByAlias() {
        withMergeTestTablesAndDefaultData {
            val destAlias = Dest.alias("dest_alias")

            destAlias.mergeFrom(
                sourceQuery,
                on = { sourceQuery[Source.key] eq destAlias[Dest.key] },
            ) {
                whenNotMatchedInsert {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = sourceQuery[Source.value] * 2
                }
            }

            val dest = Dest.getByKey("only-in-source-1")
            assertEquals(2, dest[Dest.value])
        }
    }

    @Test
    fun testUpdate() {
        withMergeTestTablesAndDefaultData {
            Dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenMatchedUpdate {
                    it[Dest.value] = (sourceQuery[Source.value] + Dest.value) * 2
                    it[Dest.optional] = sourceQuery[Source.key] + stringLiteral("::") + Dest.key
                }
            }
            val dest = Dest.getByKey("in-source-and-dest-1")
            assertEquals(22, dest[Dest.value])
            assertEquals("in-source-and-dest-1::in-source-and-dest-1", dest[Dest.optional])
            assertEquals(TEST_DEFAULT_DATE_TIME, dest[Dest.at])
        }
    }

    @Test
    fun testUpdateByAlias() {
        withMergeTestTablesAndDefaultData {
            val destAlias = Dest.alias("dest_alias")

            destAlias.mergeFrom(
                sourceQuery,
                on = { sourceQuery[Source.key] eq destAlias[Dest.key] },
            ) {
                whenMatchedUpdate {
                    it[Dest.value] = (sourceQuery[Source.value] + destAlias[Dest.value]) * 2
                    it[Dest.optional] = sourceQuery[Source.key] + stringLiteral("::") + destAlias[Dest.key]
                }
            }

            val dest = Dest.getByKey("in-source-and-dest-1")
            assertEquals(22, dest[Dest.value])
            assertEquals("in-source-and-dest-1::in-source-and-dest-1", dest[Dest.optional])
            assertEquals(TEST_DEFAULT_DATE_TIME, dest[Dest.at])
        }
    }

    @Test
    fun testDelete() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.oracleRelatedDB) {
            Dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenMatchedDelete()
            }

            assertNull(Dest.getByKeyOrNull("in-source-and-dest-1"))
            assertNull(Dest.getByKeyOrNull("in-source-and-dest-2"))
            assertNull(Dest.getByKeyOrNull("in-source-and-dest-3"))
            assertNull(Dest.getByKeyOrNull("in-source-and-dest-4"))
        }
    }

    @Test
    fun testConditionOnInsertAndUpdate() {
        withMergeTestTablesAndDefaultData {
            Dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenNotMatchedInsert(and = (sourceQuery[Source.value] greater 2)) {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = sourceQuery[Source.value]
                }

                whenMatchedUpdate(and = (Dest.value greater 20)) {
                    it[Dest.value] = sourceQuery[Source.value] + Dest.value
                }
            }

            assertNull(Dest.getByKeyOrNull("only-in-source-1"))
            assertNull(Dest.getByKeyOrNull("only-in-source-2"))
            assertNotNull(Dest.getByKeyOrNull("only-in-source-3"))
            assertNotNull(Dest.getByKeyOrNull("only-in-source-4"))

            assertEquals(10, Dest.getByKey("in-source-and-dest-1")[Dest.value])
            assertEquals(20, Dest.getByKey("in-source-and-dest-2")[Dest.value])
            assertEquals(33, Dest.getByKey("in-source-and-dest-3")[Dest.value])
            assertEquals(44, Dest.getByKey("in-source-and-dest-4")[Dest.value])
        }
    }

    @Test
    fun testConditionOnDelete() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.oracleRelatedDB) {
            Dest.mergeFrom(
                sourceQuery,
                on = { defaultOnCondition() },
            ) {
                whenMatchedDelete(and = (sourceQuery[Source.value] greater 2) and (Dest.value greater 20))
            }

            assertNotNull(Dest.getByKeyOrNull("in-source-and-dest-1"))
            assertNotNull(Dest.getByKeyOrNull("in-source-and-dest-2"))
            assertNull(Dest.getByKeyOrNull("in-source-and-dest-3"))
            assertNull(Dest.getByKeyOrNull("in-source-and-dest-4"))
        }
    }

    @Test
    fun testMultipleClauses() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.oracleRelatedDB + TestDB.sqlServerRelatedDB) {
            addLogger(StdOutSqlLogger)
            Dest.mergeFrom(sourceQuery, on = { defaultOnCondition() }) {
                whenNotMatchedInsert(and = (sourceQuery[Source.value] eq 1)) {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = sourceQuery[Source.value]
                    it[Dest.optional] = "one"
                }
                whenNotMatchedInsert(and = (sourceQuery[Source.value] eq 2)) {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = sourceQuery[Source.value]
                    it[Dest.optional] = "two"
                }
                whenNotMatchedInsert {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = sourceQuery[Source.value]
                    it[Dest.optional] = "three-and-more"
                }

                whenMatchedDelete(and = (sourceQuery[Source.value] eq 1))
                whenMatchedUpdate(and = (sourceQuery[Source.value] eq 1)) {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = (Dest.value + sourceQuery[Source.value]) * 10
                }
                whenMatchedUpdate(and = (sourceQuery[Source.value] eq 2)) {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = (Dest.value + sourceQuery[Source.value]) * 100
                }
                whenMatchedDelete(and = (sourceQuery[Source.value] eq 3))

                whenMatchedUpdate {
                    it[Dest.key] = sourceQuery[Source.key]
                    it[Dest.value] = 1000
                }
            }

            assertEquals("one", Dest.getByKey("only-in-source-1")[Dest.optional])
            assertEquals("two", Dest.getByKey("only-in-source-2")[Dest.optional])
            assertEquals("three-and-more", Dest.getByKey("only-in-source-3")[Dest.optional])
            assertEquals("three-and-more", Dest.getByKey("only-in-source-4")[Dest.optional])

            assertNull(Dest.getByKeyOrNull("in-source-and-dest-1"))
            assertEquals(2200, Dest.getByKey("in-source-and-dest-2")[Dest.value])
            assertNull(Dest.getByKeyOrNull("in-source-and-dest-3"))
            assertEquals(1000, Dest.getByKey("in-source-and-dest-4")[Dest.value])
        }
    }
}

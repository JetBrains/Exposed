package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.TestDB.ORACLE
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MergeSelectTest : MergeBaseTest() {

    private val source = Source.selectAll().alias("sub")

    private fun SqlExpressionBuilder.defaultOnCondition() = Dest.key eq source[Source.key]

    @Test
    fun testInsert() {
        withMergeTestsTables {
            Insert.source("test1", 1)

            Dest.mergeFrom(source, on = { defaultOnCondition() }) {
                insertWhenNotMatched {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = source[Source.value] * 2
                }
            }

            assertEquals(2, Dest.selectAll().single()[Dest.value])
        }
    }

    @Test
    fun testInsertOptionalValue() {
        withMergeTestsTables {
            Insert.source("test1", 5)

            Dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                insertWhenNotMatched {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = source[Source.value]
                    it[Dest.optional] = stringLiteral("optional::") + source[Source.key]
                }
            }

            assertEquals("optional::test1", Dest.getByKey("test1")[Dest.optional])
        }
    }

    @Test
    fun testUpdate() {
        withMergeTestsTables {
            Insert.source("test1", 5)
            Insert.dest("test1", 10)

            Dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                updateWhenMatched {
                    it[Dest.value] = (source[Source.value] + Dest.value) * 2
                }
            }
            val dest = Dest.getByKey("test1")
            assertEquals(30, dest[Dest.value])
            assertNull(dest[Dest.optional])
            assertEquals(TEST_DEFAULT_DATE_TIME, dest[Dest.at])
        }
    }

    @Test
    fun testDelete() {
        withMergeTestsTables(excludeSettings = TestDB.oracleRelatedDB) {
            Insert.source("test1", 1)
            Insert.dest("test1", 2)

            Dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                deleteWhenMatched()
            }

            assertNull(Dest.getByKeyOrNull("test1"))
        }
    }

    @Test
    fun testDeleteOnUpdateInOracle() {
        withMergeTestsTables(excludeSettings = allDbExcept(listOf(ORACLE))) {
            Insert.source("test1", 1)
            Insert.dest("test1", 2)

            Dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                updateWhenMatched(deleteWhere = (Dest.value greater 1)) {
                    it[Dest.value] = Dest.value
                }
            }

            assertNull(Dest.getByKeyOrNull("test1"))
        }
    }

    @Test
    fun testConditionOnInsert() {
        withMergeTestsTables {
            Insert.source("test1", 1)
            Insert.source("test2", 3)

            Dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                insertWhenNotMatched(and = (source[Source.value] greater 2)) {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = source[Source.value]
                }
            }

            assertNull(Dest.getByKeyOrNull("test1"))
            assertNotNull(Dest.getByKeyOrNull("test2"))
        }
    }

    @Test
    fun testConditionOnUpdate() {
        withMergeTestsTables {
            Insert.source("test1", 1)
            Insert.source("test2", 3)

            Insert.dest("test1", 1)
            Insert.dest("test2", 5)

            Dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                updateWhenMatched(and = (source[Source.value] eq Dest.value)) {
                    it[Dest.value] = source[Source.value] + Dest.value
                }
            }

            assertEquals(2, Dest.getByKey("test1")[Dest.value])
            assertEquals(5, Dest.getByKey("test2")[Dest.value])
        }
    }

    @Test
    fun testConditionOnDelete() {
        withMergeTestsTables(excludeSettings = TestDB.oracleRelatedDB) {
            Insert.source("test1", 1)
            Insert.source("test2", 1)
            Insert.source("test3", 1)

            Insert.dest("test1", 1)
            Insert.dest("test2", 2)

            Dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                deleteWhenMatched(and = (source[Source.value] eq Dest.value))
            }

            assertNull(Dest.getByKeyOrNull("test1"))
            assertNotNull(Dest.getByKeyOrNull("test2"))
        }
    }

    @Test
    fun testInsertByDestAlias() {
        withMergeTestsTables {
            withMergeTestsTables {
                Insert.source("test1", 5)

                val alias = Dest.alias("dest_alias")

                alias.mergeFrom(
                    source,
                    on = { source[Source.key] eq alias[Dest.key] },
                ) {
                    insertWhenNotMatched {
                        it[Dest.key] = source[Source.key]
                        it[Dest.value] = source[Source.value] * 2
                    }
                }

                assertEquals(10, Dest.getByKey("test1")[Dest.value])
            }
        }
    }

    @Test
    fun testUpdateByDestAlias() {
        withMergeTestsTables {
            Insert.source("test1", 5)
            Insert.dest("test1", 10)

            val alias = Dest.alias("dest_alias")
            alias.mergeFrom(
                source,
                on = { source[Source.key] eq alias[Dest.key] },
            ) {
                updateWhenMatched {
                    it[Dest.value] = (source[Source.value] + alias[Dest.value]) * 2
                }
            }

            assertEquals(30, Dest.getByKey("test1")[Dest.value])
        }
    }

    @Test
    fun testMultipleInserts() {
        withMergeTestsTables(excludeSettings = TestDB.oracleRelatedDB + TestDB.sqlServerRelatedDB) {
            Insert.source("test1", 5)
            Insert.source("test2", 10)
            Insert.source("test3", 15)

            Dest.mergeFrom(source, on = { defaultOnCondition() }) {
                insertWhenNotMatched(and = (source[Source.value] less 7)) {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = source[Source.value]
                    it[Dest.optional] = "first"
                }
                insertWhenNotMatched(and = (source[Source.value] less 20)) {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = source[Source.value]
                    it[Dest.optional] = "second"
                }
                insertWhenNotMatched(and = (source[Source.value] less 12)) {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = source[Source.value]
                    it[Dest.optional] = "never"
                }
            }

            assertEquals("first", Dest.getByKey("test1")[Dest.optional])
            assertEquals("second", Dest.getByKey("test2")[Dest.optional])
            assertEquals("second", Dest.getByKey("test3")[Dest.optional])
        }
    }

    @Test
    fun testMultipleUpdates() {
        withMergeTestsTables(excludeSettings = TestDB.oracleRelatedDB + TestDB.sqlServerRelatedDB) {
            Insert.source("test1", 5)
            Insert.source("test2", 10)
            Insert.source("test3", 15)

            Insert.dest("test1", 100)
            Insert.dest("test2", 100)
            Insert.dest("test3", 100)

            Dest.mergeFrom(source, on = { defaultOnCondition() }) {
                updateWhenMatched(and = (source[Source.value] less 7)) {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = Dest.value + 1
                }
                updateWhenMatched(and = (source[Source.value] less 20)) {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = Dest.value + 2
                }
                updateWhenMatched(and = (source[Source.value] less 12)) {
                    it[Dest.key] = source[Source.key]
                    it[Dest.value] = Dest.value + 3
                    it[Dest.optional] = "never"
                }
            }

            assertEquals(101, Dest.getByKey("test1")[Dest.value])
            assertEquals(102, Dest.getByKey("test2")[Dest.value])
            assertEquals(102, Dest.getByKey("test3")[Dest.value])
        }
    }

    @Test
    fun testMultipleDeletes() {
        withMergeTestsTables(excludeSettings = TestDB.oracleRelatedDB + TestDB.sqlServerRelatedDB) {
            Insert.source("test1", 5)
            Insert.source("test2", 10)
            Insert.source("test3", 15)

            Insert.dest("test1", 100)
            Insert.dest("test2", 100)
            Insert.dest("test3", 100)

            Dest.mergeFrom(source, on = { defaultOnCondition() }) {
                deleteWhenMatched(and = (source[Source.value] less 7))
                deleteWhenMatched(and = (source[Source.value] greater 12))
            }

            assertNull(Dest.getByKeyOrNull("test1"))
            assertNotNull(Dest.getByKeyOrNull("test2"))
            assertNull(Dest.getByKeyOrNull("test3"))
        }
    }

    @Test
    fun testMultipleInsertsInOracleAndSqlserver() {
        withMergeTestsTables(excludeSettings = allDbExcept(TestDB.oracleRelatedDB + TestDB.sqlServerRelatedDB)) {
            assertFailsWith(IllegalArgumentException::class) {
                Dest.mergeFrom(source, on = { defaultOnCondition() }) {
                    insertWhenNotMatched {
                        it[Dest.key] = source[Source.key]
                        it[Dest.value] = source[Source.value]
                    }
                    insertWhenNotMatched {
                        it[Dest.key] = source[Source.key]
                        it[Dest.value] = source[Source.value]
                    }
                }
            }
        }
    }
}

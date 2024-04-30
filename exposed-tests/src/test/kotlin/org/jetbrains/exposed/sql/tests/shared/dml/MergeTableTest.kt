package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IdTable
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

class MergeTableTest : MergeBaseTest() {

    @Test
    fun testInsert() {
        withMergeTestsTables {
            Insert.source("test1", 5)

            Dest.mergeFrom(
                Source,
                on = { Source.key eq Dest.key },
            ) {
                insertWhenNotMatched {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value * 2
                }
            }

            val dest = Dest.getByKey("test1")
            assertEquals(10, dest[Dest.value])
            assertNull(dest[Dest.optional])
            assertEquals(TEST_DEFAULT_DATE_TIME, dest[Dest.at])
        }
    }

    @Test
    fun testInsertOptionalValue() {
        withMergeTestsTables {
            Insert.source("test1", 5)

            Dest.mergeFrom(
                Source,
                on = { Source.key eq Dest.key },
            ) {
                insertWhenNotMatched {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
                    it[Dest.optional] = stringLiteral("optional::") + Source.key
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
                Source,
                on = { Source.key eq Dest.key },
            ) {
                updateWhenMatched {
                    it[Dest.value] = (Source.value + Dest.value) * 2
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
                Source,
                on = { Source.key eq Dest.key },
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
                Source,
                on = { Source.key eq Dest.key },
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
                Source,
                on = { Source.key eq Dest.key },
            ) {
                insertWhenNotMatched(and = (Source.value greater 2)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
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
                Source,
                on = { Source.key eq Dest.key },
            ) {
                updateWhenMatched(and = (Source.value eq Dest.value)) {
                    it[Dest.value] = Source.value + Dest.value
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
                Source,
                on = { Source.key eq Dest.key },
            ) {
                deleteWhenMatched(and = (Source.value eq Dest.value))
            }

            assertNull(Dest.getByKeyOrNull("test1"))
            assertNotNull(Dest.getByKeyOrNull("test2"))
        }
    }

    @Test
    fun testInsertBySourceAlias() {
        withMergeTestsTables {
            Insert.source("test1", 5)

            val alias = Source.alias("source_alias")
            Dest.mergeFrom(
                alias,
                on = { alias[Source.key] eq alias[Source.key] },
            ) {
                insertWhenNotMatched {
                    it[Dest.key] = alias[Source.key]
                    it[Dest.value] = alias[Source.value] * 2
                }
            }

            assertEquals(10, Dest.getByKey("test1")[Dest.value])
        }
    }

    @Test
    fun testInsertByDestAlias() {
        withMergeTestsTables {
            withMergeTestsTables {
                Insert.source("test1", 5)

                val alias = Dest.alias("dest_alias")

                alias.mergeFrom(
                    Source,
                    on = { Source.key eq alias[Dest.key] },
                ) {
                    insertWhenNotMatched {
                        it[Dest.key] = Source.key
                        it[Dest.value] = Source.value * 2
                    }
                }

                assertEquals(10, Dest.getByKey("test1")[Dest.value])
            }
        }
    }

    @Test
    fun testUpdateBySourceAlias() {
        withMergeTestsTables {
            Insert.source("test1", 5)
            Insert.dest("test1", 10)

            val alias = Source.alias("source_alias")
            Dest.mergeFrom(
                alias,
                on = { alias[Source.key] eq Dest.key },
            ) {
                updateWhenMatched {
                    it[Dest.value] = (alias[Source.value] + Dest.value) * 2
                }
            }

            assertEquals(30, Dest.getByKey("test1")[Dest.value])
        }
    }

    @Test
    fun testUpdateByDestAlias() {
        withMergeTestsTables {
            Insert.source("test1", 5)
            Insert.dest("test1", 10)

            val alias = Dest.alias("dest_alias")
            alias.mergeFrom(
                Source,
                on = { Source.key eq alias[Dest.key] },
            ) {
                updateWhenMatched {
                    it[Dest.value] = (Source.value + alias[Dest.value]) * 2
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

            Dest.mergeFrom(Source, on = { Source.key eq Dest.key }) {
                insertWhenNotMatched(and = (Source.value less 7)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
                    it[Dest.optional] = "first"
                }
                insertWhenNotMatched(and = (Source.value less 20)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
                    it[Dest.optional] = "second"
                }
                insertWhenNotMatched(and = (Source.value less 12)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
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

            Dest.mergeFrom(Source, on = { Source.key eq Dest.key }) {
                updateWhenMatched(and = (Source.value less 7)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Dest.value + 1
                }
                updateWhenMatched(and = (Source.value less 20)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Dest.value + 2
                }
                updateWhenMatched(and = (Source.value less 12)) {
                    it[Dest.key] = Source.key
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

            Dest.mergeFrom(Source, on = { Source.key eq Dest.key }) {
                deleteWhenMatched(and = (Source.value less 7))
                deleteWhenMatched(and = (Source.value greater 12))
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
                Dest.mergeFrom(Source, on = { Source.key eq Dest.key }) {
                    insertWhenNotMatched {
                        it[Dest.key] = Source.key
                        it[Dest.value] = Source.value
                    }
                    insertWhenNotMatched {
                        it[Dest.key] = Source.key
                        it[Dest.value] = Source.value
                    }
                }
            }
        }
    }

    @Test
    fun testMultipleUpdatesInOracleAndSqlserver() {
        withMergeTestsTables(excludeSettings = allDbExcept(TestDB.oracleRelatedDB + TestDB.sqlServerRelatedDB)) {
            assertFailsWith(IllegalArgumentException::class) {
                Dest.mergeFrom(Source, on = { Source.key eq Dest.key }) {
                    updateWhenMatched {
                        it[Dest.value] = Source.value
                    }
                    updateWhenMatched {
                        it[Dest.value] = Source.value
                    }
                }
            }
        }
    }

    @Test
    fun testDeleteInOracleAndSqlserver() {
        withMergeTestsTables(excludeSettings = allDbExcept(TestDB.oracleRelatedDB + TestDB.sqlServerRelatedDB)) {
            assertFailsWith(IllegalArgumentException::class) {
                Dest.mergeFrom(Source, on = { Source.key eq Dest.key }) {
                    deleteWhenMatched()
                    deleteWhenMatched()
                }
            }
        }
    }

    @Test
    fun testDeleteWhereCondition() {
        withMergeTestsTables(excludeSettings = TestDB.oracleRelatedDB) {
            assertFailsWith(IllegalArgumentException::class) {
                Dest.mergeFrom(Source, on = { Source.key eq Dest.key }) {
                    updateWhenMatched(deleteWhere = (Dest.value greater 1)) {
                        it[Dest.value] = Source.value
                    }
                }
            }
        }
    }

    object SourceNoAutoId : IdTable<Int>("test_source_no_auto_id") {
        override val id = integer("id").entityId()
        val value = varchar("value", 128)
        override val primaryKey = PrimaryKey(id)
    }

    object DestNoAutoId : IdTable<Int>("test_dest_no_auto_id") {
        override val id = integer("id").entityId()
        val value = varchar("value", 128)
        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testAutoGeneratedOnCondition() {
        withTables(excludeSettings = defaultExcludeSettings, SourceNoAutoId, DestNoAutoId) {
            SourceNoAutoId.insert {
                it[id] = 1
                it[value] = "1"
            }
            SourceNoAutoId.insert {
                it[id] = 2
                it[value] = "2"
            }
            DestNoAutoId.insert {
                it[id] = 1
                it[value] = "1"
            }

            DestNoAutoId.mergeFrom(SourceNoAutoId) {
                insertWhenNotMatched {
                    it[DestNoAutoId.id] = SourceNoAutoId.id
                    it[DestNoAutoId.value] = SourceNoAutoId.value
                }
                updateWhenMatched {
                    it[DestNoAutoId.value] = DestNoAutoId.value + SourceNoAutoId.value
                }
            }

            assertEquals("11", DestNoAutoId.selectAll().where { DestNoAutoId.id eq 1 }.single()[DestNoAutoId.value])
            assertEquals("2", DestNoAutoId.selectAll().where { DestNoAutoId.id eq 2 }.single()[DestNoAutoId.value])
        }
    }

    @Test
    fun testAutoGeneratedOnConditionWithTableAlias() {
        withTables(excludeSettings = defaultExcludeSettings, SourceNoAutoId, DestNoAutoId) {
            SourceNoAutoId.insert {
                it[id] = 1
                it[value] = "1"
            }
            SourceNoAutoId.insert {
                it[id] = 2
                it[value] = "2"
            }
            DestNoAutoId.insert {
                it[id] = 1
                it[value] = "1"
            }

            val sourceAlias = SourceNoAutoId.alias("test_source_alias")
            val destAlias = DestNoAutoId.alias("test_dest_alias")

            destAlias.mergeFrom(sourceAlias) {
                insertWhenNotMatched {
                    it[destAlias[DestNoAutoId.id]] = sourceAlias[SourceNoAutoId.id]
                    it[destAlias[DestNoAutoId.value]] = sourceAlias[SourceNoAutoId.value]
                }
                updateWhenMatched {
                    it[destAlias[DestNoAutoId.value]] = destAlias[DestNoAutoId.value] + sourceAlias[SourceNoAutoId.value]
                }
            }

            assertEquals("11", DestNoAutoId.selectAll().where { DestNoAutoId.id eq 1 }.single()[DestNoAutoId.value])
            assertEquals("2", DestNoAutoId.selectAll().where { DestNoAutoId.id eq 2 }.single()[DestNoAutoId.value])
        }
    }
}

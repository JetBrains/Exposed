package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.TestDB.ORACLE
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MergeTableTest : MergeBaseTest() {

    private fun SqlExpressionBuilder.defaultOnCondition() = Source.key eq Dest.key

    @Test
    fun testInsert() {
        withMergeTestTablesAndDefaultData {
            Dest.mergeFrom(
                Source,
                on = { defaultOnCondition() },
            ) {
                whenNotMatchedInsert {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value * 2
                    it[Dest.optional] = stringLiteral("optional::") + Source.key
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
            val sourceAlias = Source.alias("source_alias")

            destAlias.mergeFrom(
                sourceAlias,
                on = { sourceAlias[Source.key] eq destAlias[Dest.key] },
            ) {
                whenNotMatchedInsert {
                    it[Dest.key] = sourceAlias[Source.key]
                    it[Dest.value] = sourceAlias[Source.value] * 2
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
                Source,
                on = { defaultOnCondition() },
            ) {
                whenMatchedUpdate {
                    it[Dest.value] = (Source.value + Dest.value) * 2
                    it[Dest.optional] = Source.key + stringLiteral("::") + Dest.key
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
            val sourceAlias = Source.alias("source_alias")

            destAlias.mergeFrom(
                sourceAlias,
                on = { sourceAlias[Source.key] eq destAlias[Dest.key] },
            ) {
                whenMatchedUpdate {
                    it[Dest.value] = (sourceAlias[Source.value] + destAlias[Dest.value]) * 2
                    it[Dest.optional] = sourceAlias[Source.key] + stringLiteral("::") + destAlias[Dest.key]
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
                Source,
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
    fun testOracleDeleteOnUpdate() {
        withMergeTestTablesAndDefaultData(excludeSettings = allDbExcept(listOf(ORACLE))) {
            Dest.mergeFrom(
                Source,
                on = { defaultOnCondition() },
            ) {
                whenMatchedUpdate(deleteWhere = (Dest.value greater 20)) {
                    it[Dest.value] = Dest.value
                }
            }

            assertNotNull(Dest.getByKeyOrNull("in-source-and-dest-1"))
            assertNotNull(Dest.getByKeyOrNull("in-source-and-dest-2"))
            assertNull(Dest.getByKeyOrNull("in-source-and-dest-3"))
            assertNull(Dest.getByKeyOrNull("in-source-and-dest-4"))
        }
    }

    @Test
    fun testConditionOnInsertAndUpdate() {
        withMergeTestTablesAndDefaultData {
            Dest.mergeFrom(
                Source,
                on = { defaultOnCondition() },
            ) {
                whenNotMatchedInsert(and = (Source.value greater 2)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
                }

                whenMatchedUpdate(and = (Dest.value greater 20)) {
                    it[Dest.value] = Source.value + Dest.value
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
                Source,
                on = { defaultOnCondition() },
            ) {
                whenMatchedDelete(and = (Source.value greater 2) and (Dest.value greater 20))
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
            Dest.mergeFrom(Source, on = { defaultOnCondition() }) {
                whenNotMatchedInsert(and = (Source.value eq 1)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
                    it[Dest.optional] = "one"
                }
                whenNotMatchedInsert(and = (Source.value eq 2)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
                    it[Dest.optional] = "two"
                }
                whenNotMatchedInsert {
                    it[Dest.key] = Source.key
                    it[Dest.value] = Source.value
                    it[Dest.optional] = "three-and-more"
                }

                whenMatchedDelete(and = (Source.value eq 1))
                whenMatchedUpdate(and = (Source.value eq 1)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = (Dest.value + Source.value) * 10
                }
                whenMatchedUpdate(and = (Source.value eq 2)) {
                    it[Dest.key] = Source.key
                    it[Dest.value] = (Dest.value + Source.value) * 100
                }
                whenMatchedDelete(and = (Source.value eq 3))

                whenMatchedUpdate {
                    it[Dest.key] = Source.key
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

    @Test
    fun testUnsupportedByDialectExceptions() {
        withMergeTestTables(excludeSettings = allDbExcept(TestDB.oracleRelatedDB + TestDB.sqlServerRelatedDB)) {
            expectException<UnsupportedByDialectException> {
                Dest.mergeFrom(Source, on = { defaultOnCondition() }) {
                    whenNotMatchedInsert {
                        it[Dest.key] = Source.key
                        it[Dest.value] = Source.value
                    }
                    whenNotMatchedInsert {
                        it[Dest.key] = Source.key
                        it[Dest.value] = Source.value
                    }
                }
            }

            expectException<UnsupportedByDialectException> {
                Dest.mergeFrom(Source, on = { defaultOnCondition() }) {
                    whenMatchedUpdate {
                        it[Dest.value] = Source.value
                    }
                    whenMatchedUpdate {
                        it[Dest.value] = Source.value
                    }
                }
            }

            expectException<UnsupportedByDialectException> {
                Dest.mergeFrom(Source, on = { defaultOnCondition() }) {
                    whenMatchedDelete()
                    whenMatchedDelete()
                }
            }
        }

        withMergeTestTables(excludeSettings = TestDB.oracleRelatedDB) {
            expectException<UnsupportedByDialectException> {
                Dest.mergeFrom(Source, on = { defaultOnCondition() }) {
                    whenMatchedUpdate(deleteWhere = (Dest.value greater 1)) {
                        it[Dest.value] = Source.value
                    }
                }
            }
        }
    }

    @Test
    fun testAutoGeneratedOnCondition() {
        val source = object : IdTable<Int>("test_source") {
            override val id = integer("id").entityId()
            val value = varchar("test_value", 128)
            override val primaryKey = PrimaryKey(id)
        }

        val dest = object : IdTable<Int>("test_dest") {
            override val id = integer("id").entityId()
            val value = varchar("test_value", 128)
            override val primaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = defaultExcludeSettings, source, dest) {
            source.insert {
                it[id] = 1
                it[value] = "1"
            }
            source.insert {
                it[id] = 2
                it[value] = "2"
            }

            dest.mergeFrom(source) {
                whenNotMatchedInsert {
                    it[dest.id] = source.id
                    it[dest.value] = source.value
                }
            }

            val destAlias = dest.alias("dest_alias")
            val sourceAlias = source.alias("source_alias")
            destAlias.mergeFrom(sourceAlias) {
                whenNotMatchedInsert {
                    it[dest.id] = sourceAlias[source.id]
                    it[dest.value] = sourceAlias[source.value]
                }
            }

            assertEquals("1", dest.selectAll().where { dest.id eq 1 }.single()[dest.value])
            assertEquals("2", dest.selectAll().where { dest.id eq 2 }.single()[dest.value])
        }
    }
}

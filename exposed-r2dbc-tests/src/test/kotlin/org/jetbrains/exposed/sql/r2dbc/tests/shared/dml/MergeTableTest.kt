package org.jetbrains.exposed.sql.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.r2dbc.sql.mergeFrom
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.TestDB.ORACLE
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MergeTableTest : MergeBaseTest() {

    private fun SqlExpressionBuilder.defaultOnCondition() = Source.key eq Dest.key

    @Test
    fun testInsert() {
        withMergeTestTablesAndDefaultData { dest, source ->
            dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                whenNotMatchedInsert {
                    it[dest.key] = source.key
                    it[dest.value] = source.value * 2
                    it[dest.optional] = stringLiteral("optional::") + source.key
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
            val sourceAlias = source.alias("source_alias")

            destAlias.mergeFrom(
                sourceAlias,
                on = { sourceAlias[source.key] eq destAlias[dest.key] },
            ) {
                whenNotMatchedInsert {
                    it[dest.key] = sourceAlias[source.key]
                    it[dest.value] = sourceAlias[source.value] * 2
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
                source,
                on = { defaultOnCondition() },
            ) {
                whenMatchedUpdate {
                    it[dest.value] = (source.value + dest.value) * 2
                    it[dest.optional] = source.key + stringLiteral("::") + dest.key
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
            val sourceAlias = source.alias("source_alias")

            destAlias.mergeFrom(
                sourceAlias,
                on = { sourceAlias[source.key] eq destAlias[dest.key] },
            ) {
                whenMatchedUpdate {
                    it[dest.value] = (sourceAlias[source.value] + destAlias[dest.value]) * 2
                    it[dest.optional] = sourceAlias[source.key] + stringLiteral("::") + destAlias[dest.key]
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
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.ALL_ORACLE_LIKE) { dest, source ->
            dest.mergeFrom(
                source,
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
    fun testOracleDeleteOnUpdate() {
        withMergeTestTablesAndDefaultData(excludeSettings = allDbExcept(listOf(ORACLE))) { dest, source ->
            dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                whenMatchedUpdate(deleteWhere = (dest.value greater 20)) {
                    it[dest.value] = dest.value
                }
            }

            assertNotNull(dest.getByKeyOrNull("in-source-and-dest-1"))
            assertNotNull(dest.getByKeyOrNull("in-source-and-dest-2"))
            assertNull(dest.getByKeyOrNull("in-source-and-dest-3"))
            assertNull(dest.getByKeyOrNull("in-source-and-dest-4"))
        }
    }

    @Test
    fun testConditionOnInsertAndUpdate() {
        withMergeTestTablesAndDefaultData { dest, source ->
            dest.mergeFrom(
                source,
                on = { defaultOnCondition() },
            ) {
                whenNotMatchedInsert(and = (source.value greater 2)) {
                    it[dest.key] = source.key
                    it[dest.value] = source.value
                }

                whenMatchedUpdate(and = (dest.value greater 20)) {
                    it[dest.value] = source.value + dest.value
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
                source,
                on = { defaultOnCondition() },
            ) {
                whenMatchedDelete(and = (source.value greater 2) and (dest.value greater 20))
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
            dest.mergeFrom(source, on = { defaultOnCondition() }) {
                whenNotMatchedInsert(and = (source.value eq 1)) {
                    it[dest.key] = source.key
                    it[dest.value] = source.value
                    it[dest.optional] = "one"
                }
                whenNotMatchedInsert(and = (source.value eq 2)) {
                    it[dest.key] = source.key
                    it[dest.value] = source.value
                    it[dest.optional] = "two"
                }
                whenNotMatchedInsert {
                    it[dest.key] = source.key
                    it[dest.value] = source.value
                    it[dest.optional] = "three-and-more"
                }

                whenMatchedDelete(and = (source.value eq 1))
                whenMatchedUpdate(and = (source.value eq 1)) {
                    it[dest.key] = source.key
                    it[dest.value] = (dest.value + source.value) * 10
                }
                whenMatchedUpdate(and = (source.value eq 2)) {
                    it[dest.key] = source.key
                    it[dest.value] = (dest.value + source.value) * 100
                }
                whenMatchedDelete(and = (source.value eq 3))

                whenMatchedUpdate {
                    it[dest.key] = source.key
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
    fun testUnsupportedByDialectExceptions() {
        withMergeTestTables(excludeSettings = allDbExcept(TestDB.ALL_ORACLE_LIKE + TestDB.ALL_SQLSERVER_LIKE)) { dest, source ->
            expectException<UnsupportedByDialectException> {
                dest.mergeFrom(source, on = { defaultOnCondition() }) {
                    whenNotMatchedInsert {
                        it[dest.key] = source.key
                        it[dest.value] = source.value
                    }
                    whenNotMatchedInsert {
                        it[dest.key] = source.key
                        it[dest.value] = source.value
                    }
                }
            }

            expectException<UnsupportedByDialectException> {
                dest.mergeFrom(source, on = { defaultOnCondition() }) {
                    whenMatchedUpdate {
                        it[dest.value] = source.value
                    }
                    whenMatchedUpdate {
                        it[dest.value] = source.value
                    }
                }
            }

            expectException<UnsupportedByDialectException> {
                dest.mergeFrom(source, on = { defaultOnCondition() }) {
                    whenMatchedDelete()
                    whenMatchedDelete()
                }
            }
        }

        withMergeTestTables(excludeSettings = TestDB.ALL_ORACLE_LIKE) { dest, source ->
            expectException<UnsupportedByDialectException> {
                dest.mergeFrom(source, on = { defaultOnCondition() }) {
                    whenMatchedUpdate(deleteWhere = (dest.value greater 1)) {
                        it[dest.value] = source.value
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

    @Test
    fun testWhenNotMatchedDoNothing() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES) { dest, source ->
            dest.mergeFrom(source, on = { defaultOnCondition() }) {
                whenNotMatchedDoNothing(and = source.value greater 1)
                whenNotMatchedInsert {
                    it[dest.key] = source.key
                    it[dest.value] = source.value + 100
                }
            }

            assertEquals(101, dest.selectAll().where { dest.key eq "only-in-source-1" }.first()[dest.value])
            assertNull(dest.selectAll().where { dest.key inList listOf("only-in-source-2", "only-in-source-3") }.firstOrNull())
        }
    }

    @Test
    fun testWhenMatchedDoNothing() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES) { dest, source ->
            dest.mergeFrom(source, on = { defaultOnCondition() }) {
                whenMatchedDoNothing(and = source.value eq 1)
                whenMatchedDelete()
            }

            assertEquals(10, dest.selectAll().where { dest.key eq "in-source-and-dest-1" }.first()[dest.value])
            assertEqualLists(emptyList(), dest.selectAll().where { dest.key inList listOf("in-source-and-dest-2", "in-source-and-dest-3") }.toList())
        }
    }

    @Test
    fun testOverridingSystemValue() {
        val source = object : IntIdTable() {
        }
        val dest = object : IdTable<Int>() {
            override val id = integer("id").withDefinition("generated always as identity").entityId()
            override val primaryKey: PrimaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, source, dest) {
            val id = source.insertAndGetId { }

            dest.mergeFrom(source) {
                // `overridingSystemValue` allows to overwrite `generated always as identity` value
                // otherwise Postgres will throw exception that auto generated value must not be overwritten
                whenNotMatchedInsert(overridingSystemValue = true) {
                    it[dest.id] = source.id
                }
            }

            assertEquals(id.value, dest.selectAll().single()[dest.id].value)
        }
    }

    @Test
    fun testOverridingUserValue() {
        val source = object : IntIdTable() {
        }

        val sequenceStartNumber = 100
        val dest = object : IdTable<Int>() {
            override val id =
                integer("id").withDefinition("GENERATED BY DEFAULT AS IDENTITY(SEQUENCE NAME testOverridingUserValueSequence START WITH $sequenceStartNumber)")
                    .entityId()
            override val primaryKey: PrimaryKey = PrimaryKey(id)
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, source, dest) {
            try {
                source.insertAndGetId { }

                dest.mergeFrom(source) {
                    // `overridingUserValue` here allows to avoid setting id value from source table, and take generated by sequence instead
                    whenNotMatchedInsert(overridingUserValue = true) {
                        it[dest.id] = source.id
                    }
                }

                assertEquals(sequenceStartNumber, dest.selectAll().single()[dest.id].value)
            } finally {
                SchemaUtils.drop(dest)
                exec("drop sequence if exists testOverridingUserValueSequence")
            }
        }
    }

    @Test
    fun testInsertDefaultValues() {
        val source = object : IntIdTable("test_source") {
            val value = varchar("value", 128)
        }
        val dest = object : IntIdTable("test_dest") {
            // `withDefinition()` here is a workaround to avoid insert explicitly pass default value
            val value = varchar("value", 128).withDefinition("DEFAULT", stringLiteral("default-test-value")).databaseGenerated()
        }

        withTables(excludeSettings = defaultExcludeSettings, source, dest) {
            source.insertAndGetId {
                it[value] = "user-defined-value"
            }

            dest.mergeFrom(source) {
                whenNotMatchedInsert {}
            }

            assertEquals("default-test-value", dest.selectAll().single()[dest.value])
        }
    }

    @Test
    fun testPostgresFeaturesAreUnsupportedInOtherDatabases() {
        withMergeTestTablesAndDefaultData(excludeSettings = TestDB.ALL_POSTGRES) { dest, source ->
            // DO NOTHING
            expectException<UnsupportedByDialectException> {
                dest.mergeFrom(source, on = { defaultOnCondition() }) {
                    whenNotMatchedDoNothing(and = source.value greater 1)
                }
            }

            // OVERRIDING SYSTEM VALUE
            expectException<UnsupportedByDialectException> {
                dest.mergeFrom(source) {
                    whenNotMatchedInsert(overridingSystemValue = true) {}
                }
            }

            // OVERRIDING USER VALUE
            expectException<UnsupportedByDialectException> {
                dest.mergeFrom(source) {
                    whenNotMatchedInsert(overridingUserValue = true) {}
                }
            }
        }
    }
}

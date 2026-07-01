package org.jetbrains.exposed.dao.r2dbc.tests.jodatime

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jodatime.CurrentDate
import org.jetbrains.exposed.v1.jodatime.CurrentDateTime
import org.jetbrains.exposed.v1.jodatime.DateTimeWithTimeZoneColumnType
import org.jetbrains.exposed.v1.jodatime.date
import org.jetbrains.exposed.v1.jodatime.datetime
import org.jetbrains.exposed.v1.jodatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private val dbTimestampNow: CustomFunction<DateTime>
    get() = object : CustomFunction<DateTime>("now", DateTimeWithTimeZoneColumnType()) {}

class JodaTimeDefaultTests : R2dbcDatabaseTestsBase() {

    object TableWithDBDefault : IntIdTable() {
        var cIndex = 0
        val field = varchar("field", 100)
        val t1 = datetime("t1").defaultExpression(CurrentDateTime)
        val t2 = date("t2").defaultExpression(CurrentDate)
        val clientDefault = integer("clientDefault").clientDefault { cIndex++ }
    }

    class DBDefault(id: EntityID<Int>) : IntEntity(id) {
        var field by TableWithDBDefault.field
        var t1 by TableWithDBDefault.t1
        var t2 by TableWithDBDefault.t2
        val clientDefault by TableWithDBDefault.clientDefault

        override fun equals(other: Any?): Boolean {
            return (other as? DBDefault)?.let {
                id == it.id && field == it.field && equalDateTime(t1, it.t1) && equalDateTime(t2, it.t2)
            } ?: false
        }

        override fun hashCode(): Int = id.value.hashCode()

        companion object : IntEntityClass<DBDefault>(TableWithDBDefault)
    }

    @Test
    fun testDefaultsWithExplicit01() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                DBDefault.new { field = "1" }.flush(),
                DBDefault.new {
                    field = "2"
                    t1 = DateTime.now().minusDays(5)
                }.flush()
            )
            created.forEach {
                DBDefault.removeFromCache(it)
            }

            val entities = DBDefault.all().toList()
            assertEqualCollections(created.map { it.id }, entities.map { it.id })
        }
    }

    @Test
    fun testDefaultsWithExplicit02() {
        // MySql 5 is excluded because it does not support `CURRENT_DATE()` as a default value
        withTables(excludeSettings = listOf(TestDB.MYSQL_V5), TableWithDBDefault) {
            val created = listOf(
                DBDefault.new {
                    field = "2"
                    t1 = DateTime.now().minusDays(5)
                }.flush(),
                DBDefault.new { field = "1" }.flush()
            )

            // R2DBC: INSERT/RETURNING doesn't bring back `defaultExpression` columns (`t1`, `t2`),
            // and `Column.getValue` is non-suspend so it can't lazy-load like JDBC does. Refresh
            // explicitly so `created[i].t1`/`t2` (read by `equals`) have values to compare.
            created.forEach { it.refresh() }
            created.forEach { DBDefault.removeFromCache(it) }
            val entities = DBDefault.all().toList()
            assertEqualCollections(created, entities)
        }
    }

    @Test
    fun testDefaultsInvokedOnlyOncePerEntity() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.cIndex = 0
            val db1 = DBDefault.new { field = "1" }.flush()
            val db2 = DBDefault.new { field = "2" }.flush()
            assertEquals(0, db1.clientDefault)
            assertEquals(1, db2.clientDefault)
            assertEquals(2, TableWithDBDefault.cIndex)
        }
    }

    object DefaultTimestampTable : IntIdTable("test_table") {
        val timestamp: Column<DateTime> =
            timestampWithTimeZone("timestamp").defaultExpression(dbTimestampNow)
    }

    class DefaultTimestampEntity(id: EntityID<Int>) : Entity<Int>(id) {
        companion object : EntityClass<Int, DefaultTimestampEntity>(DefaultTimestampTable)

        var timestamp: DateTime by DefaultTimestampTable.timestamp
    }

    @Test
    fun testCustomDefaultTimestampFunctionWithEntity() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES - TestDB.MYSQL_V8 - TestDB.ALL_H2_V2, DefaultTimestampTable) {
            val entity = DefaultTimestampEntity.new {}.flush()
            // R2DBC: `defaultExpression(dbTimestampNow)` is evaluated by the DB and isn't part of
            // the INSERT's resultedValues, so `entity.timestamp` has no cached value yet. Flush and
            // refresh so the row is loaded back from the DB (JDBC does this implicitly on read).
            entity.refresh(flush = true)

            val timestamp = DefaultTimestampTable.selectAll().first()[DefaultTimestampTable.timestamp]

            assertEquals(timestamp, entity.timestamp)
        }
    }
}

fun assertEqualDateTime(d1: DateTime?, d2: DateTime?) {
    when {
        d1 == null && d2 == null -> return
        d1 == null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error("d1 is not null while d2 is null on ${currentDialectTest.name}")
        else -> assertEquals(d1.millis, d2.millis, "Failed on ${currentDialectTest.name}")
    }
}

fun equalDateTime(d1: DateTime?, d2: DateTime?) = try {
    assertEqualDateTime(d1, d2)
    true
} catch (_: Exception) {
    false
}

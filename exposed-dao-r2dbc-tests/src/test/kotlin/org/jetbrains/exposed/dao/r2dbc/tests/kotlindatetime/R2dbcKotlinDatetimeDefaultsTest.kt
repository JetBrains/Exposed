package org.jetbrains.exposed.dao.r2dbc.tests.kotlindatetime

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.KotlinOffsetDateTimeColumnType
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val dbTimestampNow: CustomFunction<OffsetDateTime>
    get() = object : CustomFunction<OffsetDateTime>("now", KotlinOffsetDateTimeColumnType()) {}

class R2dbcKotlinDatetimeDefaultsTest : R2dbcDatabaseTestsBase() {

    private fun localDateTimeNowMinusUnit(value: Int, unit: DurationUnit) =
        Clock.System.now().minus(value.toDuration(unit)).toLocalDateTime(TimeZone.currentSystemDefault())

    object TableWithDBDefault : IntIdTable() {
        var cIndex = 0
        val field = varchar("field", 100)
        val t1 = datetime("t1").defaultExpression(CurrentDateTime)
        val t2 = date("t2").defaultExpression(CurrentDate)
        val clientDefault = integer("clientDefault").clientDefault { cIndex++ }
    }

    class DBDefault(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var field by TableWithDBDefault.field
        var t1 by TableWithDBDefault.t1
        var t2 by TableWithDBDefault.t2
        val clientDefault by TableWithDBDefault.clientDefault

        override fun equals(other: Any?): Boolean {
            return (other as? DBDefault)?.let { id == it.id && field == it.field && t1 == it.t1 && t2 == it.t2 } ?: false
        }

        override fun hashCode(): Int = id.value.hashCode()

        companion object : IntR2dbcEntityClass<DBDefault>(TableWithDBDefault)
    }

    @Test
    fun testDefaultsWithExplicit01() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                DBDefault.new { field = "1" },
                DBDefault.new {
                    field = "2"
                    t1 = localDateTimeNowMinusUnit(5, DurationUnit.DAYS)
                }
            )
            commit()
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
                    t1 = localDateTimeNowMinusUnit(5, DurationUnit.DAYS)
                },
                DBDefault.new { field = "1" }
            )

            flushCache()
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
            val db1 = DBDefault.new { field = "1" }
            val db2 = DBDefault.new { field = "2" }
            flushCache()
            assertEquals(0, db1.clientDefault)
            assertEquals(1, db2.clientDefault)
            assertEquals(2, TableWithDBDefault.cIndex)
        }
    }

    object DefaultTimestampTable : IntIdTable("test_table") {
        val timestamp: Column<OffsetDateTime> =
            timestampWithTimeZone("timestamp").defaultExpression(dbTimestampNow)
    }

    class DefaultTimestampEntity(id: EntityID<Int>) : R2dbcEntity<Int>(id) {
        companion object : R2dbcEntityClass<Int, DefaultTimestampEntity>(DefaultTimestampTable)

        var timestamp: OffsetDateTime by DefaultTimestampTable.timestamp
    }

    @Test
    fun testCustomDefaultTimestampFunctionWithEntity() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES - TestDB.MYSQL_V8 - TestDB.ALL_H2_V2, DefaultTimestampTable) {
            val entity = DefaultTimestampEntity.new {}
            // R2DBC: `defaultExpression(dbTimestampNow)` is evaluated by the DB and isn't part of
            // the INSERT's resultedValues, so `entity.timestamp` has no cached value yet. Flush and
            // refresh so the row is loaded back from the DB (JDBC does this implicitly on read).
            entity.refresh(flush = true)

            val timestamp = DefaultTimestampTable.selectAll().first()[DefaultTimestampTable.timestamp]

            assertEquals(timestamp, entity.timestamp)
        }
    }
}

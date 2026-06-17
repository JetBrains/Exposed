package org.jetbrains.exposed.dao.r2dbc.tests.javatime

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Table.PrimaryKey
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.javatime.CurrentDateTime
import org.jetbrains.exposed.v1.javatime.JavaOffsetDateTimeColumnType
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.Temporal
import kotlin.random.Random.Default.nextInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private val dbTimestampNow: CustomFunction<OffsetDateTime>
    get() = object : CustomFunction<OffsetDateTime>("now", JavaOffsetDateTimeColumnType()) {}

class R2dbcJavatimeDefaultsTest : R2dbcDatabaseTestsBase() {
    object TableWithDBDefault : IntIdTable() {
        var cIndex = 0
        val field = varchar("field", 100)
        val t1 = datetime("t1").defaultExpression(CurrentDateTime)
        val clientDefault = integer("clientDefault").clientDefault { cIndex++ }
    }

    class DBDefault(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var field by TableWithDBDefault.field
        var t1 by TableWithDBDefault.t1
        var clientDefault by TableWithDBDefault.clientDefault

        override fun equals(other: Any?): Boolean {
            return (other as? DBDefault)?.let { id == it.id && field == it.field && equalDateTime(t1, it.t1) } ?: false
        }

        override fun hashCode(): Int = id.value.hashCode()

        companion object : IntR2dbcEntityClass<DBDefault>(TableWithDBDefault)
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
    fun testDefaultsWithExplicit01() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                DBDefault.new { field = "1" },
                DBDefault.new {
                    field = "2"
                    t1 = LocalDateTime.now().minusDays(5)
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
        withTables(TableWithDBDefault) {
            val created = listOf(
                DBDefault.new {
                    field = "2"
                    t1 = LocalDateTime.now().minusDays(5)
                },
                DBDefault.new { field = "1" }
            )

            flushCache()
            // R2DBC: INSERT/RETURNING doesn't bring back `defaultExpression` columns (`t1`), and
            // `Column.getValue` is non-suspend so it can't lazy-load like JDBC does. Refresh
            // explicitly so `created[i].t1` (read by `equals`) has a value to compare.
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

    @Test
    fun testDefaultsCanBeOverridden() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.cIndex = 0
            val db1 = DBDefault.new { field = "1" }
            val db2 = DBDefault.new { field = "2" }
            db1.clientDefault = 12345
            flushCache()
            assertEquals(12345, db1.clientDefault)
            assertEquals(1, db2.clientDefault)
            assertEquals(2, TableWithDBDefault.cIndex)

            flushCache()
            assertEquals(12345, db1.clientDefault)
        }
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

    object TableWithDefaultValue : IdTable<Int>() {
        const val DEFAULT_VALUE = 10
        val value = integer("value")
        val valueWithDefault = integer("valueWithDefault").default(DEFAULT_VALUE)

        override val id = integer("id").clientDefault { nextInt() }.entityId()
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    class TableWithDefaultValueEntity(id: EntityID<Int>) : R2dbcEntity<Int>(id) {
        var value by TableWithDefaultValue.value
        var valueWithDefault by TableWithDefaultValue.valueWithDefault

        companion object : R2dbcEntityClass<Int, TableWithDefaultValueEntity>(TableWithDefaultValue)
    }

    @Test
    fun testExplicitInsertionOfDefaultValuesWithIdTable() {
        withTables(TableWithDefaultValue) {
            TableWithDefaultValueEntity.new(5) {
                value = 94
                valueWithDefault = TableWithDefaultValue.DEFAULT_VALUE
            }.run {
                assertTrue(this.writeValues.values.contains(TableWithDefaultValue.DEFAULT_VALUE))
            }
        }
    }
}

/**
 * Duplicated from `exposed-java-time` module
 */
fun equalDateTime(d1: Temporal?, d2: Temporal?) = try {
    assertEqualDateTime(d1, d2)
    true
} catch (_: Exception) {
    false
}

/**
 * Duplicated from `exposed-java-time` module
 */
fun <T : Temporal> assertEqualDateTime(d1: T?, d2: T?) {
    when {
        d1 == null && d2 == null -> return
        d1 == null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error("d1 is not null while d2 is null on ${currentDialectTest.name}")
        d1 is LocalTime && d2 is LocalTime -> {
            assertEquals(d1.toSecondOfDay(), d2.toSecondOfDay(), "Failed on seconds ${currentDialectTest.name}")
            if (d2.nano != 0) {
                assertEqualFractionalPart(d1.nano, d2.nano)
            }
        }
        d1 is LocalDateTime && d2 is LocalDateTime -> {
            assertEquals(
                d1.toEpochSecond(ZoneOffset.UTC),
                d2.toEpochSecond(ZoneOffset.UTC),
                "Failed on epoch seconds ${currentDialectTest.name}"
            )
            assertEqualFractionalPart(d1.nano, d2.nano)
        }
        d1 is Instant && d2 is Instant -> {
            assertEquals(d1.epochSecond, d2.epochSecond, "Failed on epoch seconds ${currentDialectTest.name}")
            assertEqualFractionalPart(d1.nano, d2.nano)
        }
        d1 is OffsetDateTime && d2 is OffsetDateTime -> {
            assertEqualDateTime(d1.toLocalDateTime(), d2.toLocalDateTime())
            assertEquals(d1.offset, d2.offset)
        }
        else -> assertEquals(d1, d2, "Failed on ${currentDialectTest.name}")
    }
}

/**
 * Duplicated from `exposed-java-time` module
 */
private fun assertEqualFractionalPart(nano1: Int, nano2: Int) {
    val dialect = currentDialectTest
    val db = dialect.name
    when (dialect) {
        // accurate to 100 nanoseconds
        is SQLServerDialect ->
            assertEquals(roundTo100Nanos(nano1), roundTo100Nanos(nano2), "Failed on 1/10th microseconds $db")
        // microseconds
        is MariaDBDialect ->
            assertEquals(floorToMicro(nano1), floorToMicro(nano2), "Failed on microseconds $db")
        is H2Dialect, is PostgreSQLDialect, is MysqlDialect -> {
            when ((dialect as? MysqlDialect)?.isFractionDateTimeSupported()) {
                null, true -> {
                    assertEquals(roundToMicro(nano1), roundToMicro(nano2), "Failed on microseconds $db")
                }
                else -> {} // don't compare fractional part
            }
        }
        // milliseconds
        is OracleDialect ->
            assertEquals(roundToMilli(nano1), roundToMilli(nano2), "Failed on milliseconds $db")
        is SQLiteDialect ->
            assertEquals(floorToMilli(nano1), floorToMilli(nano2), "Failed on milliseconds $db")
        else -> fail("Unknown dialect $db")
    }
}

private fun roundTo100Nanos(nanos: Int): Int {
    return BigDecimal(nanos).divide(BigDecimal(100), RoundingMode.HALF_UP).toInt()
}

private fun roundToMicro(nanos: Int): Int {
    return BigDecimal(nanos).divide(BigDecimal(1_000), RoundingMode.HALF_UP).toInt()
}

private fun floorToMicro(nanos: Int): Int = nanos / 1_000

private fun roundToMilli(nanos: Int): Int {
    return BigDecimal(nanos).divide(BigDecimal(1_000_000), RoundingMode.HALF_UP).toInt()
}

private fun floorToMilli(nanos: Int): Int {
    return nanos / 1_000_000
}

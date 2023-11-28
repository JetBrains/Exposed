@file:OptIn(ExperimentalTime::class)

package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.*
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.BatchDataInconsistentException
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.constraintNamePart
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.insertAndWait
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

class DefaultsTest : DatabaseTestsBase() {

    private fun localDateTimeNowMinusUnit(value: Int, unit: DurationUnit) =
        Clock.System.now().minus(value.toDuration(unit)).toLocalDateTime(TimeZone.currentSystemDefault())

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
            return (other as? DBDefault)?.let { id == it.id && field == it.field && t1 == it.t1 && t2 == it.t2 } ?: false
        }

        override fun hashCode(): Int = id.value.hashCode()

        companion object : IntEntityClass<DBDefault>(TableWithDBDefault)
    }

    @Test
    fun testCanUseClientDefaultOnNullableColumn() {
        val defaultValue: Int? = null
        val table = object : IntIdTable() {
            val clientDefault = integer("clientDefault").nullable().clientDefault { defaultValue }
        }
        val returnedDefault = table.clientDefault.defaultValueFun?.invoke()

        assertTrue(table.clientDefault.columnType.nullable, "Expected clientDefault columnType to be nullable")
        assertNotNull(table.clientDefault.defaultValueFun, "Expected clientDefault column to have a default value fun, but was null")
        assertEquals(defaultValue, returnedDefault, "Expected clientDefault to return $defaultValue, but was $returnedDefault")
    }

    @Test
    fun testCanSetNullableColumnToUseClientDefault() {
        val defaultValue = 123
        val table = object : IntIdTable() {
            val clientDefault = integer("clientDefault").clientDefault { defaultValue }.nullable()
        }
        val returnedDefault = table.clientDefault.defaultValueFun?.invoke()

        assertTrue(table.clientDefault.columnType.nullable, "Expected clientDefault columnType to be nullable")
        assertNotNull(table.clientDefault.defaultValueFun, "Expected clientDefault column to have a default value fun, but was null")
        assertEquals(defaultValue, returnedDefault, "Expected clientDefault to return $defaultValue, but was $returnedDefault")
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
        withTables(TableWithDBDefault) {
            val created = listOf(
                DBDefault.new {
                    field = "2"
                    t1 = localDateTimeNowMinusUnit(5, DurationUnit.DAYS)
                },
                DBDefault.new { field = "1" }
            )

            flushCache()
            created.forEach {
                DBDefault.removeFromCache(it)
            }
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

    private val initBatch = listOf<(BatchInsertStatement) -> Unit>(
        {
            it[TableWithDBDefault.field] = "1"
        },
        {
            it[TableWithDBDefault.field] = "2"
            it[TableWithDBDefault.t1] = now()
        }
    )

    @Test
    fun testRawBatchInsertFails01() {
        withTables(TableWithDBDefault) {
            expectException<BatchDataInconsistentException> {
                BatchInsertStatement(TableWithDBDefault).run {
                    initBatch.forEach {
                        addBatch()
                        it(this)
                    }
                }
            }
        }
    }

    @Test
    fun testBatchInsertNotFails01() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.batchInsert(initBatch) { foo ->
                foo(this)
            }
        }
    }

    @Test
    fun testBatchInsertFails01() {
        withTables(TableWithDBDefault) {
            expectException<BatchDataInconsistentException> {
                TableWithDBDefault.batchInsert(listOf(1)) {
                    this[TableWithDBDefault.t1] = now()
                }
            }
        }
    }

    @Test
    fun testDefaults01() {
        val currentDT = CurrentDateTime
        val nowExpression = object : Expression<LocalDateTime>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
                +when (val dialect = currentDialectTest) {
                    is OracleDialect -> "SYSDATE"
                    is SQLServerDialect -> "GETDATE()"
                    is MysqlDialect -> if (dialect.isFractionDateTimeSupported()) "NOW(6)" else "NOW()"
                    else -> "NOW()"
                }
            }
        }
        val dateConstValue = LocalDate(2010, 1, 1)
        val instConstValue = dateConstValue.atStartOfDayIn(TimeZone.UTC)
        val dateTimeConstValue = instConstValue.toLocalDateTime(TimeZone.UTC)
        val dLiteral = dateLiteral(dateConstValue)
        val dtLiteral = dateTimeLiteral(dateTimeConstValue)
        val tsConstValue = instConstValue.plus(42.toDuration(DurationUnit.SECONDS))
        val tsLiteral = timestampLiteral(tsConstValue)
        val durConstValue = tsConstValue.toEpochMilliseconds().toDuration((DurationUnit.MILLISECONDS))
        val durLiteral = durationLiteral(durConstValue)
        val tmConstValue = LocalTime(12, 0)
        val tLiteral = timeLiteral(tmConstValue)

        val testTable = object : IntIdTable("t") {
            val s = varchar("s", 100).default("test")
            val sn = varchar("sn", 100).default("testNullable").nullable()
            val l = long("l").default(42)
            val c = char("c").default('X')
            val t1 = datetime("t1").defaultExpression(currentDT)
            val t2 = datetime("t2").defaultExpression(nowExpression)
            val t3 = datetime("t3").defaultExpression(dtLiteral)
            val t4 = date("t4").default(dateConstValue)
            val t5 = timestamp("t5").default(tsConstValue)
            val t6 = timestamp("t6").defaultExpression(tsLiteral)
            val t7 = duration("t7").default(durConstValue)
            val t8 = duration("t8").defaultExpression(durLiteral)
            val t9 = time("t9").default(tmConstValue)
            val t10 = time("t10").defaultExpression(tLiteral)
        }

        fun Expression<*>.itOrNull() = when {
            currentDialectTest.isAllowedAsColumnDefault(this) ->
                "DEFAULT ${currentDialectTest.dataTypeProvider.processForDefaultValue(this)} NOT NULL"

            else -> "NULL"
        }

        withTables(listOf(TestDB.SQLITE), testTable) {
            val dtType = currentDialectTest.dataTypeProvider.dateTimeType()
            val longType = currentDialectTest.dataTypeProvider.longType()
            val timeType = currentDialectTest.dataTypeProvider.timeType()
            val varCharType = currentDialectTest.dataTypeProvider.varcharType(100)
            val q = db.identifierManager.quoteString
            val baseExpression = "CREATE TABLE " + addIfNotExistsIfSupported() +
                "${"t".inProperCase()} (" +
                "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerAutoincType()} PRIMARY KEY, " +
                "${"s".inProperCase()} $varCharType${testTable.s.constraintNamePart()} DEFAULT 'test' NOT NULL, " +
                "${"sn".inProperCase()} $varCharType${testTable.sn.constraintNamePart()} DEFAULT 'testNullable' NULL, " +
                "${"l".inProperCase()} ${currentDialectTest.dataTypeProvider.longType()}${testTable.l.constraintNamePart()} DEFAULT 42 NOT NULL, " +
                "$q${"c".inProperCase()}$q CHAR${testTable.c.constraintNamePart()} DEFAULT 'X' NOT NULL, " +
                "${"t1".inProperCase()} $dtType${testTable.t1.constraintNamePart()} ${currentDT.itOrNull()}, " +
                "${"t2".inProperCase()} $dtType${testTable.t2.constraintNamePart()} ${nowExpression.itOrNull()}, " +
                "${"t3".inProperCase()} $dtType${testTable.t3.constraintNamePart()} ${dtLiteral.itOrNull()}, " +
                "${"t4".inProperCase()} DATE${testTable.t4.constraintNamePart()} ${dLiteral.itOrNull()}, " +
                "${"t5".inProperCase()} $dtType${testTable.t5.constraintNamePart()} ${tsLiteral.itOrNull()}, " +
                "${"t6".inProperCase()} $dtType${testTable.t6.constraintNamePart()} ${tsLiteral.itOrNull()}, " +
                "${"t7".inProperCase()} $longType${testTable.t7.constraintNamePart()} ${durLiteral.itOrNull()}, " +
                "${"t8".inProperCase()} $longType${testTable.t8.constraintNamePart()} ${durLiteral.itOrNull()}, " +
                "${"t9".inProperCase()} $timeType${testTable.t9.constraintNamePart()} ${tLiteral.itOrNull()}, " +
                "${"t10".inProperCase()} $timeType${testTable.t10.constraintNamePart()} ${tLiteral.itOrNull()}" +
                ")"

            val expected = if (currentDialectTest is OracleDialect || currentDialectTest.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
                arrayListOf("CREATE SEQUENCE t_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807", baseExpression)
            } else {
                arrayListOf(baseExpression)
            }

            assertEqualLists(expected, testTable.ddl)

            val id1 = testTable.insertAndGetId { }

            val row1 = testTable.select { testTable.id eq id1 }.single()
            assertEquals("test", row1[testTable.s])
            assertEquals("testNullable", row1[testTable.sn])
            assertEquals(42, row1[testTable.l])
            assertEquals('X', row1[testTable.c])
            assertEquals(dateTimeConstValue, row1[testTable.t3])
            assertEquals(dateConstValue, row1[testTable.t4])
            assertEquals(tsConstValue, row1[testTable.t5])
            assertEquals(tsConstValue, row1[testTable.t6])
            assertEquals(durConstValue, row1[testTable.t7])
            assertEquals(durConstValue, row1[testTable.t8])
            assertEquals(tmConstValue, row1[testTable.t9])
            assertEquals(tmConstValue, row1[testTable.t10])
        }
    }

    @Test
    fun testDefaultExpressions01() {
        fun abs(value: Int) = object : ExpressionWithColumnType<Int>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("ABS($value)") }

            override val columnType: IColumnType = IntegerColumnType()
        }

        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime)
            val defaultDate = date("defaultDate").defaultExpression(CurrentDate)
            val defaultInt = integer("defaultInteger").defaultExpression(abs(-100))
        }

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
            }
            val result = foo.select { foo.id eq id }.single()

            assertEquals(today, result[foo.defaultDateTime].date)
            assertEquals(today, result[foo.defaultDate])
            assertEquals(100, result[foo.defaultInt])
        }
    }

    @Test
    fun testDefaultExpressions02() {
        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentTimestamp())
        }

        val nonDefaultDate = LocalDate(2000, 1, 1)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toLocalDateTime(TimeZone.currentSystemDefault())

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
                it[foo.defaultDateTime] = nonDefaultDate
            }

            val result = foo.select { foo.id eq id }.single()

            assertEquals("bar", result[foo.name])
            assertEquals(nonDefaultDate, result[foo.defaultDateTime])

            foo.update({ foo.id eq id }) {
                it[foo.name] = "baz"
            }

            val result2 = foo.select { foo.id eq id }.single()
            assertEquals("baz", result2[foo.name])
            assertEquals(nonDefaultDate, result2[foo.defaultDateTime])
        }
    }

    @Test
    fun testBetweenFunction() {
        val foo = object : IntIdTable("foo") {
            val dt = datetime("dateTime")
        }

        withTables(foo) {
            val d2020 = LocalDate(2020, 1, 1)
            val dt2020 = d2020.atTime(0, 0, 0)
            val dt2020m1w = d2020.minus(DateTimeUnit.WEEK).atTime(0, 0, 0)
            val dt2020p1w = d2020.plus(DateTimeUnit.WEEK).atTime(0, 0, 0)

            foo.insert { it[dt] = LocalDateTime(2019, 1, 1, 1, 1) }
            foo.insert { it[dt] = dt2020 }
            foo.insert { it[dt] = LocalDateTime(2021, 1, 1, 1, 1) }
            val count = foo.select { foo.dt.between(dt2020m1w, dt2020p1w) }.count()
            assertEquals(1, count)
        }
    }

    @Test
    fun testConsistentSchemeWithFunctionAsDefaultExpression() {
        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDate = date("default_date").defaultExpression(CurrentDate)
            val defaultDateTime1 = datetime("default_date_time_1").defaultExpression(CurrentDateTime)
            val defaultDateTime2 = datetime("default_date_time_2").defaultExpression(CurrentTimestamp())
            val defaultTimeStamp = timestamp("default_time_stamp").defaultExpression(CurrentTimestamp())
        }

        withDb {
            try {
                SchemaUtils.create(foo)

                val actual = SchemaUtils.statementsRequiredToActualizeScheme(foo)

                if (currentDialectTest is MysqlDialect) {
                    // MySQL and MariaDB do not support CURRENT_DATE as default
                    // so the column is created with a NULL marker, which correctly triggers 1 alter statement
                    val tableName = foo.nameInDatabaseCase()
                    val dateColumnName = foo.defaultDate.nameInDatabaseCase()
                    val alter = "ALTER TABLE $tableName MODIFY COLUMN $dateColumnName DATE NULL"
                    assertEquals(alter, actual.single())
                } else {
                    assertTrue(actual.isEmpty())
                }
            } finally {
                SchemaUtils.drop(foo)
            }
        }
    }

    @Test
    fun testTimestampWithTimeZoneDefaults() {
        // UTC time zone
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneOffset.UTC))
        assertEquals("UTC", ZoneId.systemDefault().id)

        val nowWithTimeZone = OffsetDateTime.now()
        val timestampWithTimeZoneLiteral = timestampWithTimeZoneLiteral(nowWithTimeZone)

        val testTable = object : IntIdTable("t") {
            val t1 = timestampWithTimeZone("t1").default(nowWithTimeZone)
            val t2 = timestampWithTimeZone("t2").defaultExpression(timestampWithTimeZoneLiteral)
        }

        fun Expression<*>.itOrNull() = when {
            currentDialectTest.isAllowedAsColumnDefault(this) ->
                "DEFAULT ${currentDialectTest.dataTypeProvider.processForDefaultValue(this)} NOT NULL"
            else -> "NULL"
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE, TestDB.MARIADB)) {
            if (!isOldMySql()) {
                SchemaUtils.create(testTable)

                val timestampWithTimeZoneType = currentDialectTest.dataTypeProvider.timestampWithTimeZoneType()

                val baseExpression = "CREATE TABLE " + addIfNotExistsIfSupported() +
                    "${"t".inProperCase()} (" +
                    "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerAutoincType()} PRIMARY KEY, " +
                    "${"t1".inProperCase()} $timestampWithTimeZoneType${testTable.t1.constraintNamePart()} ${timestampWithTimeZoneLiteral.itOrNull()}, " +
                    "${"t2".inProperCase()} $timestampWithTimeZoneType${testTable.t2.constraintNamePart()} ${timestampWithTimeZoneLiteral.itOrNull()}" +
                    ")"

                val expected = if (currentDialectTest is OracleDialect ||
                    currentDialectTest.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
                ) {
                    arrayListOf(
                        "CREATE SEQUENCE t_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807",
                        baseExpression
                    )
                } else {
                    arrayListOf(baseExpression)
                }

                assertEqualLists(expected, testTable.ddl)

                val id1 = testTable.insertAndGetId { }

                val row1 = testTable.select { testTable.id eq id1 }.single()
                assertEqualDateTime(nowWithTimeZone, row1[testTable.t1])
                assertEqualDateTime(nowWithTimeZone, row1[testTable.t2])
            }
        }
    }

    @Test
    fun testDefaultCurrentDateTime() {
        val testDate = object : IntIdTable("TestDate") {
            val time = datetime("time").defaultExpression(CurrentDateTime)
        }

        fun LocalDateTime.millis(): Long = this.toJavaLocalDateTime().toEpochSecond(ZoneOffset.UTC) * 1000

        withTables(testDate) {
            val duration: Long = 2000

            repeat(2) {
                testDate.insertAndWait(duration)
            }

            Thread.sleep(duration)

            repeat(2) {
                testDate.insertAndWait(duration)
            }

            val sortedEntries: List<LocalDateTime> = testDate.selectAll().map { it[testDate.time] }.sorted()

            assertTrue(sortedEntries[1].millis() - sortedEntries[0].millis() >= 2000)
            assertTrue(sortedEntries[2].millis() - sortedEntries[0].millis() >= 6000)
            assertTrue(sortedEntries[3].millis() - sortedEntries[0].millis() >= 8000)
        }
    }
}

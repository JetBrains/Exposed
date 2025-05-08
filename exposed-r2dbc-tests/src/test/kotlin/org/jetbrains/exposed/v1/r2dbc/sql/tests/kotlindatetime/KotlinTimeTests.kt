package org.jetbrains.exposed.v1.r2dbc.sql.tests.kotlindatetime

import kotlinx.coroutines.flow.all
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.sql.insert
import org.jetbrains.exposed.v1.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.sql.select
import org.jetbrains.exposed.v1.r2dbc.sql.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.sql.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.expectException
import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.json.extract
import org.jetbrains.exposed.v1.sql.json.jsonb
import org.jetbrains.exposed.v1.sql.kotlin.datetime.*
import org.jetbrains.exposed.v1.sql.vendors.*
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.time.Duration

class KotlinTimeTests : R2dbcDatabaseTestsBase() {

    private val timestampWithTimeZoneUnsupportedDB = TestDB.ALL_MARIADB + TestDB.MYSQL_V5

    @Test
    fun kotlinTimeFunctions() {
        withTables(CitiesTime) {
            val now = now()

            val cityID = CitiesTime.insertAndGetId {
                it[name] = "Tunisia"
                it[local_time] = now
            }

            val insertedYear = CitiesTime.select(CitiesTime.local_time.year()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.year()]
            val insertedMonth = CitiesTime.select(CitiesTime.local_time.month()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.month()]
            val insertedDay = CitiesTime.select(CitiesTime.local_time.day()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.day()]
            val insertedHour = CitiesTime.select(CitiesTime.local_time.hour()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.hour()]
            val insertedMinute = CitiesTime.select(CitiesTime.local_time.minute()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.minute()]
            val insertedSecond = CitiesTime.select(CitiesTime.local_time.second()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.second()]

            assertEquals(now.year, insertedYear)
            assertEquals(now.month.value, insertedMonth)
            assertEquals(now.dayOfMonth, insertedDay)
            assertEquals(now.hour, insertedHour)
            assertEquals(now.minute, insertedMinute)
            assertEquals(now.second, insertedSecond)
        }
    }

    @Test
    fun testStoringLocalDateTimeWithNanos() {
        val testDate = object : IntIdTable("TestLocalDateTime") {
            val time = datetime("time")
        }

        withTables(testDate) {
            val dateTime = Instant.parse("2023-05-04T05:04:00.000Z") // has 0 nanoseconds
            val nanos = DateTimeUnit.NANOSECOND * 111111
            // insert 2 separate constants to ensure test's rounding mode matches DB precision
            val dateTimeWithFewNanos = dateTime.plus(1, nanos).toLocalDateTime(TimeZone.currentSystemDefault())
            val dateTimeWithManyNanos = dateTime.plus(7, nanos).toLocalDateTime(TimeZone.currentSystemDefault())
            testDate.insert {
                it[testDate.time] = dateTimeWithFewNanos
            }
            testDate.insert {
                it[testDate.time] = dateTimeWithManyNanos
            }

            val dateTimesFromDB = testDate.selectAll().map { it[testDate.time] }.toList()
            assertEqualDateTime(dateTimeWithFewNanos, dateTimesFromDB[0])
            assertEqualDateTime(dateTimeWithManyNanos, dateTimesFromDB[1])
        }
    }

    @Test
    fun `test selecting Instant using expressions`() {
        val testTable = object : Table() {
            val ts = timestamp("ts")
            val tsn = timestamp("tsn").nullable()
        }

        val now = Clock.System.nowAsJdk8()

        withTables(testTable) {
            testTable.insert {
                it[ts] = now
                it[tsn] = now
            }

            val maxTsExpr = testTable.ts.max()
            val maxTimestamp = testTable.select(maxTsExpr).single()[maxTsExpr]
            assertEqualDateTime(now, maxTimestamp)

            val minTsExpr = testTable.ts.min()
            val minTimestamp = testTable.select(minTsExpr).single()[minTsExpr]
            assertEqualDateTime(now, minTimestamp)

            val maxTsnExpr = testTable.tsn.max()
            val maxNullableTimestamp = testTable.select(maxTsnExpr).single()[maxTsnExpr]
            assertEqualDateTime(now, maxNullableTimestamp)

            val minTsnExpr = testTable.tsn.min()
            val minNullableTimestamp = testTable.select(minTsnExpr).single()[minTsnExpr]
            assertEqualDateTime(now, minNullableTimestamp)
        }
    }

    @Test
    fun testLocalDateComparison() {
        val testTable = object : Table("test_table") {
            val created = date("created")
            val deleted = date("deleted")
        }

        withTables(testTable) {
            val mayTheFourth = LocalDate(2023, 5, 4)
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth
            }
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth.plus(1, DateTimeUnit.DAY)
            }

            val sameDateResult = testTable.selectAll().where { testTable.created eq testTable.deleted }.toList()
            assertEquals(1, sameDateResult.size)
            assertEquals(mayTheFourth, sameDateResult.single()[testTable.deleted])

            val sameMonthResult = testTable.selectAll().where { testTable.created.month() eq testTable.deleted.month() }.toList()
            assertEquals(2, sameMonthResult.size)

            val year2023 = if (currentDialectTest is PostgreSQLDialect) {
                // PostgreSQL requires explicit type cast to resolve function date_part
                dateParam(mayTheFourth).castTo(KotlinLocalDateColumnType()).year()
            } else {
                dateParam(mayTheFourth).year()
            }
            val createdIn2023 = testTable.selectAll().where { testTable.created.year() eq year2023 }.toList()
            assertEquals(2, createdIn2023.size)
        }
    }

    @Test
    fun testLocalDateTimeComparison() {
        val testTableDT = object : IntIdTable("test_table_dt") {
            val created = datetime("created")
            val modified = datetime("modified")
        }

        withTables(testTableDT) { testDb ->
            val mayTheFourth = "2011-05-04T13:00:21.871130789Z"
            val mayTheFourthDT = Instant.parse(mayTheFourth).toLocalDateTime(TimeZone.currentSystemDefault())
            val nowDT = now()
            val id1 = testTableDT.insertAndGetId {
                it[created] = mayTheFourthDT
                it[modified] = mayTheFourthDT
            }
            val id2 = testTableDT.insertAndGetId {
                it[created] = mayTheFourthDT
                it[modified] = nowDT
            }

            // these DB take the nanosecond value 871_130_789 and round up to default precision (e.g. in Oracle: 871_131)
            val requiresExplicitDTCast = listOf(TestDB.ORACLE, TestDB.H2_V2_ORACLE, TestDB.H2_V2_PSQL, TestDB.H2_V2_SQLSERVER)
            val dateTime = when (testDb) {
                in requiresExplicitDTCast -> Cast(dateTimeParam(mayTheFourthDT), KotlinLocalDateTimeColumnType())
                else -> dateTimeParam(mayTheFourthDT)
            }
            val createdMayFourth = testTableDT.selectAll().where { testTableDT.created eq dateTime }.count()
            assertEquals(2, createdMayFourth)

            val modifiedAtSameDT = testTableDT.selectAll().where { testTableDT.modified eq testTableDT.created }.single()
            assertEquals(id1, modifiedAtSameDT[testTableDT.id])

            val modifiedAtLaterDT = testTableDT.selectAll().where { testTableDT.modified greater testTableDT.created }.single()
            assertEquals(id2, modifiedAtLaterDT[testTableDT.id])
        }
    }

    @Test
    fun testDateTimeAsJsonB() {
        val tester = object : Table("tester") {
            val created = datetime("created")
            val modified = jsonb<ModifierData>("modified", Json.Default)
        }

        withTables(excludeSettings = TestDB.ALL_H2_V2 + TestDB.SQLSERVER + TestDB.ORACLE, tester) {
            val dateTimeNow = now()
            tester.insert {
                it[created] = dateTimeNow.date.minus(1, DateTimeUnit.YEAR).atTime(0, 0, 0)
                it[modified] = ModifierData(1, dateTimeNow)
            }
            tester.insert {
                it[created] = dateTimeNow.date.plus(1, DateTimeUnit.YEAR).atTime(0, 0, 0)
                it[modified] = ModifierData(2, dateTimeNow)
            }

            val prefix = if (currentDialectTest is PostgreSQLDialect) "" else "."

            // value extracted in same manner it is stored, a json string
            val modifiedAsString = tester.modified.extract<String>("${prefix}timestamp")
            val allModifiedAsString = tester.select(modifiedAsString)
            assertTrue(allModifiedAsString.all { it[modifiedAsString] == dateTimeNow.toString() })
            // value extracted as json, with implicit LocalDateTime serializer() performing conversions
            val modifiedAsJson = tester.modified.extract<LocalDateTime>("${prefix}timestamp", toScalar = false)
            val allModifiedAsJson = tester.select(modifiedAsJson)
            assertTrue(allModifiedAsJson.all { it[modifiedAsJson] == dateTimeNow })

            // PostgreSQL requires explicit type cast to timestamp for in-DB comparison
            val dateModified = if (currentDialectTest is PostgreSQLDialect) {
                tester.modified.extract<LocalDateTime>("${prefix}timestamp").castTo(KotlinLocalDateTimeColumnType())
            } else {
                tester.modified.extract<LocalDateTime>("${prefix}timestamp")
            }
            val modifiedBeforeCreation = tester.selectAll().where { dateModified less tester.created }.single()
            assertEquals(2, modifiedBeforeCreation[tester.modified].userId)
        }
    }

    @Test
    fun testTimestampWithTimeZone() {
        val testTable = object : IntIdTable("TestTable") {
            val timestampWithTimeZone = timestampWithTimeZone("timestamptz-column")
        }

        withTables(excludeSettings = timestampWithTimeZoneUnsupportedDB, testTable) { testDB ->
            // Cairo time zone
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))
            assertEquals("Africa/Cairo", ZoneId.systemDefault().id)

            val cairoNow = OffsetDateTime.now(ZoneId.systemDefault())

            val cairoId = testTable.insertAndGetId {
                it[timestampWithTimeZone] = cairoNow
            }

            val cairoNowInsertedInCairoTimeZone = testTable.selectAll().where { testTable.id eq cairoId }
                .single()[testTable.timestampWithTimeZone]

            // UTC time zone
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneOffset.UTC))
            assertEquals("UTC", ZoneId.systemDefault().id)

            val cairoNowRetrievedInUTCTimeZone = testTable.selectAll().where { testTable.id eq cairoId }
                .single()[testTable.timestampWithTimeZone]

            val utcID = testTable.insertAndGetId {
                it[timestampWithTimeZone] = cairoNow
            }

            val cairoNowInsertedInUTCTimeZone = testTable.selectAll().where { testTable.id eq utcID }
                .single()[testTable.timestampWithTimeZone]

            // Tokyo time zone
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("Asia/Tokyo", ZoneId.systemDefault().id)

            val cairoNowRetrievedInTokyoTimeZone = testTable.selectAll().where { testTable.id eq cairoId }
                .single()[testTable.timestampWithTimeZone]

            val tokyoID = testTable.insertAndGetId {
                it[timestampWithTimeZone] = cairoNow
            }

            val cairoNowInsertedInTokyoTimeZone = testTable.selectAll().where { testTable.id eq tokyoID }
                .single()[testTable.timestampWithTimeZone]

            // PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone.
            // To preserve the original time zone, store the time zone information in a separate column.
            val isOriginalTimeZonePreserved = testDB !in (TestDB.ALL_MYSQL + TestDB.ALL_POSTGRES)
            if (isOriginalTimeZonePreserved) {
                // Assert that time zone is preserved when the same value is inserted in different time zones
                assertEqualDateTime(cairoNow, cairoNowInsertedInCairoTimeZone)
                assertEqualDateTime(cairoNow, cairoNowInsertedInUTCTimeZone)
                assertEqualDateTime(cairoNow, cairoNowInsertedInTokyoTimeZone)

                // Assert that time zone is preserved when the same record is retrieved in different time zones
                assertEqualDateTime(cairoNow, cairoNowRetrievedInUTCTimeZone)
                assertEqualDateTime(cairoNow, cairoNowRetrievedInTokyoTimeZone)
            } else {
                // Assert equivalence in UTC when the same value is inserted in different time zones
                assertEqualDateTime(cairoNowInsertedInCairoTimeZone, cairoNowInsertedInUTCTimeZone)
                assertEqualDateTime(cairoNowInsertedInUTCTimeZone, cairoNowInsertedInTokyoTimeZone)

                // Assert equivalence in UTC when the same record is retrieved in different time zones
                assertEqualDateTime(cairoNowRetrievedInUTCTimeZone, cairoNowRetrievedInTokyoTimeZone)
            }

            // Reset to original time zone as set up in DatabaseTestsBase init block
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneOffset.UTC))
            assertEquals("UTC", ZoneId.systemDefault().id)
        }
    }

    @Test
    fun testTimestampWithTimeZoneThrowsExceptionForUnsupportedDialects() {
        val testTable = object : IntIdTable("TestTable") {
            val timestampWithTimeZone = timestampWithTimeZone("timestamptz-column")
        }

        withDb(db = timestampWithTimeZoneUnsupportedDB) {
            expectException<UnsupportedByDialectException> {
                SchemaUtils.create(testTable)
            }
        }
    }

    @Test
    fun testTimestampWithTimeZoneExtensionFunctions() {
        val testTable = object : IntIdTable("TestTable") {
            val timestampWithTimeZone = timestampWithTimeZone("timestamptz-column")
        }

        withTables(excludeSettings = timestampWithTimeZoneUnsupportedDB, testTable) { testDb ->
            // UTC time zone
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneOffset.UTC))
            assertEquals("UTC", ZoneId.systemDefault().id)

            val now = OffsetDateTime.parse("2023-05-04T05:04:01.123123123+00:00")
            val nowId = testTable.insertAndGetId {
                it[testTable.timestampWithTimeZone] = now
            }

            val dateExpr = testTable.timestampWithTimeZone.date()
            val timeExpr = testTable.timestampWithTimeZone.time()
            val yearExpr = testTable.timestampWithTimeZone.year()
            val monthExpr = testTable.timestampWithTimeZone.month()
            val dayExpr = testTable.timestampWithTimeZone.day()
            val hourExpr = testTable.timestampWithTimeZone.hour()
            val minuteExpr = testTable.timestampWithTimeZone.minute()
            val secondExpr = testTable.timestampWithTimeZone.second()

            val result = testTable.select(
                dateExpr, timeExpr, yearExpr, monthExpr, dayExpr, hourExpr, minuteExpr, secondExpr
            ).where { testTable.id eq nowId }
                .single()

            assertEquals(now.toLocalDate().toKotlinLocalDate(), result[dateExpr])

            val expectedTime =
                when (testDb) {
                    TestDB.MYSQL_V8, TestDB.SQLSERVER,
                    in TestDB.ALL_ORACLE_LIKE,
                    in TestDB.ALL_POSTGRES_LIKE -> OffsetDateTime.parse("2023-05-04T05:04:01.123123+00:00")
                    else -> now
                }.toLocalTime().toKotlinLocalTime()
            assertEquals(expectedTime, result[timeExpr])

            assertEquals(now.year, result[yearExpr])

            assertEquals(now.month.value, result[monthExpr])

            assertEquals(now.dayOfMonth, result[dayExpr])

            assertEquals(now.hour, result[hourExpr])

            assertEquals(now.minute, result[minuteExpr])

            assertEquals(now.second, result[secondExpr])
        }
    }

    @Test
    fun testCurrentDateTimeFunction() {
        val fakeTestTable = object : IntIdTable("fakeTable") {}

        withTables(fakeTestTable) {
            suspend fun currentDbDateTime(): LocalDateTime {
                return fakeTestTable.select(CurrentDateTime).first()[CurrentDateTime]
            }

            fakeTestTable.insert {}

            currentDbDateTime()
        }
    }

    @Test
    fun testInfiniteDuration() {
        val tester = object : Table("tester") {
            val duration = duration("duration")
        }
        withTables(tester) {
            tester.insert {
                it[duration] = Duration.INFINITE
            }
            val row = tester.selectAll().where { tester.duration eq Duration.INFINITE }.single()
            assertEquals(Duration.INFINITE, row[tester.duration])
        }
    }

    @Test
    fun testDateTimeAsArray() {
        val defaultDates = listOf(now().date)
        val defaultDateTimes = listOf(now())
        val tester = object : Table("array_tester") {
            val dates = array("dates", KotlinLocalDateColumnType()).default(defaultDates)
            val datetimes = array("datetimes", KotlinLocalDateTimeColumnType()).default(defaultDateTimes)
        }

        withTables(excludeSettings = TestDB.entries - TestDB.POSTGRESQL - TestDB.H2_V2, tester) {
            tester.insert { }
            val result1 = tester.selectAll().single()
            assertEqualLists(result1[tester.dates], defaultDates)
            assertEqualLists(result1[tester.datetimes], defaultDateTimes)

            val datesInput = List(3) { LocalDate(2020 + it, 5, 4) }
            val datetimeInput = List(3) { LocalDateTime(2020 + it, 5, 4, 9, 9, 9) }
            tester.insert {
                it[dates] = datesInput
                it[datetimes] = datetimeInput
            }

            val lastDate = tester.dates[3]
            val firstTwoDatetimes = tester.datetimes.slice(1, 2)
            val result2 = tester.select(lastDate, firstTwoDatetimes).where {
                tester.dates[1].year() eq 2020
            }.single()
            assertEqualDateTime(datesInput.last(), result2[lastDate])
            assertEqualLists(result2[firstTwoDatetimes], datetimeInput.take(2))
        }
    }

    @Test
    fun testSelectByTimeLiteralEquality() {
        val tableWithTime = object : IntIdTable("TableWithTime") {
            val time = time("time")
        }
        withTables(tableWithTime) {
            val localTime = LocalTime(13, 0)
            val localTimeLiteral = timeLiteral(localTime)

            // UTC time zone
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneOffset.UTC))
            assertEquals("UTC", ZoneId.systemDefault().id)

            tableWithTime.insert {
                it[time] = localTime
            }

            assertEquals(
                localTime,
                tableWithTime.select(tableWithTime.id, tableWithTime.time)
                    .where { tableWithTime.time eq localTimeLiteral }
                    .single()[tableWithTime.time]
            )
        }
    }

    @Test
    fun testTimestampAlwaysSavedInUTC() {
        val tester = object : Table("tester") {
            val timestamp_col = timestamp("timestamp_col")
        }

        withTables(tester) {
            // Cairo time zone
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))
            assertEquals("Africa/Cairo", ZoneId.systemDefault().id)

            val instant = Clock.System.nowAsJdk8()

            tester.insert {
                it[timestamp_col] = instant
            }

            assertEquals(
                instant,
                tester.selectAll().single()[tester.timestamp_col]
            )
        }
    }
}

fun <T> assertEqualDateTime(d1: T?, d2: T?) {
    when {
        d1 == null && d2 == null -> return
        d1 == null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error("d1 is not null while d2 is null on ${currentDialectTest.name}")
        d1 is LocalTime && d2 is LocalTime -> {
            assertEquals(d1.toSecondOfDay(), d2.toSecondOfDay(), "Failed on seconds ${currentDialectTest.name}")
            if (d2.nanosecond != 0) {
                assertEqualFractionalPart(d1.nanosecond, d2.nanosecond)
            }
        }
        d1 is LocalDateTime && d2 is LocalDateTime -> {
            assertEquals(
                d1.toJavaLocalDateTime().toEpochSecond(ZoneOffset.UTC),
                d2.toJavaLocalDateTime().toEpochSecond(ZoneOffset.UTC),
                "Failed on epoch seconds ${currentDialectTest.name}"
            )
            assertEqualFractionalPart(d1.nanosecond, d2.nanosecond)
        }
        d1 is Instant && d2 is Instant -> {
            assertEquals(d1.epochSeconds, d2.epochSeconds, "Failed on epoch seconds ${currentDialectTest.name}")
            assertEqualFractionalPart(d1.nanosecondsOfSecond, d2.nanosecondsOfSecond)
        }
        d1 is OffsetDateTime && d2 is OffsetDateTime -> {
            assertEqualDateTime(d1.toLocalDateTime().toKotlinLocalDateTime(), d2.toLocalDateTime().toKotlinLocalDateTime())
            assertEquals(d1.offset, d2.offset)
        }
        else -> assertEquals(d1, d2, "Failed on ${currentDialectTest.name}")
    }
}

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
    return nanos / 100
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

val today: LocalDate = now().date

object CitiesTime : IntIdTable("CitiesTime") {
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}

@Serializable
data class ModifierData(val userId: Int, val timestamp: LocalDateTime)

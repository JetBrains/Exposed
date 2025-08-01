package org.jetbrains.exposed.v1.datetime

import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.between
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.like
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.migration.MigrationUtils
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class KotlinTimeTests : DatabaseTestsBase() {

    private val timestampWithTimeZoneUnsupportedDB = TestDB.ALL_MARIADB + TestDB.MYSQL_V5

    @Test
    fun kotlinTimeFunctions() {
        withTables(CitiesTime) {
            val now = now()

            val cityID = CitiesTime.insertAndGetId {
                it[name] = "Tunisia"
                it[local_time] = now
            }

            val insertedYear = CitiesTime.select(CitiesTime.local_time.year()).where { CitiesTime.id.eq(cityID) }
                .single()[CitiesTime.local_time.year()]
            val insertedMonth = CitiesTime.select(CitiesTime.local_time.month()).where { CitiesTime.id.eq(cityID) }
                .single()[CitiesTime.local_time.month()]
            val insertedDay = CitiesTime.select(CitiesTime.local_time.day()).where { CitiesTime.id.eq(cityID) }
                .single()[CitiesTime.local_time.day()]
            val insertedHour = CitiesTime.select(CitiesTime.local_time.hour()).where { CitiesTime.id.eq(cityID) }
                .single()[CitiesTime.local_time.hour()]
            val insertedMinute = CitiesTime.select(CitiesTime.local_time.minute()).where { CitiesTime.id.eq(cityID) }
                .single()[CitiesTime.local_time.minute()]
            val insertedSecond = CitiesTime.select(CitiesTime.local_time.second()).where { CitiesTime.id.eq(cityID) }
                .single()[CitiesTime.local_time.second()]

            assertEquals(now.year, insertedYear)
            assertEquals(now.month.number, insertedMonth)
            assertEquals(now.dayOfMonth, insertedDay)
            assertEquals(now.hour, insertedHour)
            assertEquals(now.minute, insertedMinute)
            assertEquals(now.second, insertedSecond)
        }
    }

    // Checks that old numeric datetime columns works fine with new text representation
    @Test
    fun testSQLiteDateTimeFieldRegression() {
        val testDate = object : IntIdTable("TestDate") {
            val time = datetime("time").defaultExpression(CurrentDateTime)
        }

        withDb(TestDB.SQLITE) {
            try {
                exec("CREATE TABLE IF NOT EXISTS TestDate (id INTEGER PRIMARY KEY AUTOINCREMENT, \"time\" NUMERIC DEFAULT (CURRENT_TIMESTAMP) NOT NULL);")
                testDate.insert { }
                val year = testDate.time.year()
                val month = testDate.time.month()
                val day = testDate.time.day()
                val hour = testDate.time.hour()
                val minute = testDate.time.minute()

                val result = testDate.select(year, month, day, hour, minute).single()

                val now = now()
                assertEquals(now.year, result[year])
                assertEquals(now.monthNumber, result[month])
                assertEquals(now.dayOfMonth, result[day])
                assertEquals(now.hour, result[hour])
                assertEquals(now.minute, result[minute])
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(testDate)
            }
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

            val dateTimesFromDB = testDate.selectAll().map { it[testDate.time] }
            assertEqualDateTime(dateTimeWithFewNanos, dateTimesFromDB[0])
            assertEqualDateTime(dateTimeWithManyNanos, dateTimesFromDB[1])
        }
    }

    @Test
    fun `test selecting Instant using expressions`() {
        val testTable = object : Table() {
            val xTs = xTimestamp("xts")
            val xTsn = xTimestamp("xtsn").nullable()

            val ts = timestamp("ts")
            val tsn = timestamp("tsn").nullable()
        }

        val now = Clock.System.now()

        withTables(testTable) {
            testTable.insert {
                it[xTs] = now.toDeprecatedInstant()
                it[xTsn] = now.toDeprecatedInstant()
                it[ts] = now
                it[tsn] = now
            }

            val xMaxTsExpr = testTable.xTs.max()
            val xMaxTimestamp = testTable.select(xMaxTsExpr).single()[xMaxTsExpr]
            assertEqualDateTime(now.toDeprecatedInstant(), xMaxTimestamp)

            val xMinTsExpr = testTable.xTs.min()
            val xMinTimestamp = testTable.select(xMinTsExpr).single()[xMinTsExpr]
            assertEqualDateTime(now.toDeprecatedInstant(), xMinTimestamp)

            val xMaxTsnExpr = testTable.xTsn.max()
            val xMaxNullableTimestamp = testTable.select(xMaxTsnExpr).single()[xMaxTsnExpr]
            assertEqualDateTime(now.toDeprecatedInstant(), xMaxNullableTimestamp)

            val xMinTsnExpr = testTable.xTsn.min()
            val xMinNullableTimestamp = testTable.select(xMinTsnExpr).single()[xMinTsnExpr]
            assertEqualDateTime(now.toDeprecatedInstant(), xMinNullableTimestamp)

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
    fun testSQLiteDateFieldRegression01() {
        val (tableName, columnName) = "test_table" to "date_col"
        val testTable = object : IntIdTable(tableName) {
            val dateCol = date(columnName).defaultExpression(CurrentDate)
        }

        withDb(TestDB.SQLITE) {
            // force table creation using old numeric date column instead of new text column
            val createStatement = "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$columnName DATE DEFAULT (CURRENT_DATE) NOT NULL);"
            try {
                exec(createStatement)
                testTable.insert { }

                val year = testTable.dateCol.year()
                val month = testTable.dateCol.month()
                val day = testTable.dateCol.day()

                val result1 = testTable.select(year, month, day).single()
                assertEquals(today.year, result1[year])
                assertEquals(today.monthNumber, result1[month])
                assertEquals(today.dayOfMonth, result1[day])

                val lastDayOfMonth = CustomDateFunction(
                    "date",
                    testTable.dateCol,
                    stringLiteral("start of month"),
                    stringLiteral("+1 month"),
                    stringLiteral("-1 day")
                )
                val nextMonth = LocalDate(today.year, today.monthNumber, 1).plus(1, DateTimeUnit.MONTH)
                val expectedLastDayOfMonth = nextMonth.minus(1, DateTimeUnit.DAY)

                val result2 = testTable.select(lastDayOfMonth).single()
                assertEquals(expectedLastDayOfMonth, result2[lastDayOfMonth])
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(testTable)
            }
        }
    }

    @Test
    fun testSQLiteDateFieldRegression02() {
        val (tableName, eventColumn, dateColumn) = Triple("test_table", "event", "date_col")
        val testTable = object : IntIdTable(tableName) {
            val event = varchar(eventColumn, 32)
            val defaultDate = date(dateColumn).defaultExpression(CurrentDate)
        }

        withDb(TestDB.SQLITE) {
            // force table creation using old numeric date column instead of new text column
            val createStatement = "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$eventColumn VARCHAR(32) NOT NULL, $dateColumn DATE DEFAULT (CURRENT_DATE) NOT NULL);"
            try {
                exec(createStatement)
                val eventAId = testTable.insertAndGetId {
                    it[event] = "A"
                    it[defaultDate] = LocalDate(2000, 12, 25)
                }
                val eventBId = testTable.insertAndGetId {
                    it[event] = "B"
                }

                val inYear2000 = testTable.defaultDate.castTo(TextColumnType()) like "2000%"
                assertEquals(1, testTable.selectAll().where { inYear2000 }.count())

                val todayResult1 = testTable.selectAll().where { testTable.defaultDate eq today }.single()
                assertEquals(eventBId, todayResult1[testTable.id])

                testTable.update({ testTable.id eq eventAId }) {
                    it[testTable.defaultDate] = today
                }

                val todayResult2 = testTable.selectAll().where { testTable.defaultDate eq today }.count()
                assertEquals(2, todayResult2)

                val twoYearsAgo = today.minus(2, DateTimeUnit.YEAR)
                val twoYearsInFuture = today.plus(2, DateTimeUnit.YEAR)
                val isWithinTwoYears = testTable.defaultDate.between(twoYearsAgo, twoYearsInFuture)
                assertEquals(2, testTable.selectAll().where { isWithinTwoYears }.count())

                val yesterday = today.minus(1, DateTimeUnit.DAY)

                testTable.deleteWhere {
                    testTable.defaultDate.day() eq dateParam(yesterday).day()
                }

                assertEquals(2, testTable.selectAll().count())
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(testTable)
            }
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

            val sameMonthResult =
                testTable.selectAll().where { testTable.created.month() eq testTable.deleted.month() }.toList()
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
            val requiresExplicitDTCast =
                listOf(TestDB.ORACLE, TestDB.H2_V2_ORACLE, TestDB.H2_V2_PSQL, TestDB.H2_V2_SQLSERVER)
            val dateTime = when (testDb) {
                in requiresExplicitDTCast -> Cast(dateTimeParam(mayTheFourthDT), KotlinLocalDateTimeColumnType())
                else -> dateTimeParam(mayTheFourthDT)
            }
            val createdMayFourth = testTableDT.selectAll().where { testTableDT.created eq dateTime }.count()
            assertEquals(2, createdMayFourth)

            val modifiedAtSameDT =
                testTableDT.selectAll().where { testTableDT.modified eq testTableDT.created }.single()
            assertEquals(id1, modifiedAtSameDT[testTableDT.id])

            val modifiedAtLaterDT =
                testTableDT.selectAll().where { testTableDT.modified greater testTableDT.created }.single()
            assertEquals(id2, modifiedAtLaterDT[testTableDT.id])
        }
    }

    @Test
    fun testDateTimeAsJsonB() {
        val tester = object : Table("tester") {
            val created = datetime("created")
            val modified = jsonb<ModifierData>("modified", Json.Default)
        }

        withTables(excludeSettings = TestDB.ALL_H2_V2 + TestDB.SQLITE + TestDB.SQLSERVER + TestDB.ORACLE, tester) {
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

            assertEquals(
                now.toLocalDate().toKotlinLocalDate(),
                testTable.select(testTable.timestampWithTimeZone.date()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.date()]
            )

            val expectedTime =
                when (testDb) {
                    TestDB.SQLITE -> OffsetDateTime.parse("2023-05-04T05:04:01.123+00:00")
                    TestDB.MYSQL_V8, TestDB.SQLSERVER,
                    in TestDB.ALL_ORACLE_LIKE,
                    in TestDB.ALL_POSTGRES_LIKE -> OffsetDateTime.parse("2023-05-04T05:04:01.123123+00:00")

                    else -> now
                }.toLocalTime().toKotlinLocalTime()
            assertEquals(
                expectedTime,
                testTable.select(testTable.timestampWithTimeZone.time()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.time()]
            )

            assertEquals(
                now.year,
                testTable.select(testTable.timestampWithTimeZone.year()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.year()]
            )

            assertEquals(
                now.month.value,
                testTable.select(testTable.timestampWithTimeZone.month()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.month()]
            )

            assertEquals(
                now.dayOfMonth,
                testTable.select(testTable.timestampWithTimeZone.day()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.day()]
            )

            assertEquals(
                now.hour,
                testTable.select(testTable.timestampWithTimeZone.hour()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.hour()]
            )

            assertEquals(
                now.minute,
                testTable.select(testTable.timestampWithTimeZone.minute()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.minute()]
            )

            assertEquals(
                now.second,
                testTable.select(testTable.timestampWithTimeZone.second()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.second()]
            )
        }
    }

    @Test
    fun testCurrentDateTimeFunction() {
        val fakeTestTable = object : IntIdTable("fakeTable") {}

        withTables(fakeTestTable) {
            fun currentDbDateTime(): LocalDateTime {
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
    fun testCurrentDateAsDefaultExpression() {
        val testTable = object : LongIdTable("test_table") {
            val date: Column<LocalDate> = date("date").index().defaultExpression(CurrentDate)
        }
        withTables(testTable) {
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(testTable)
            assertTrue(statements.isEmpty())
        }
    }

    @Test
    fun testXTimestampAlwaysSavedInUTC() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))

        val tester = object : Table("tester") {
            val x_timestamp_col = xTimestamp("timestamp_col")
        }

        withTables(tester) {
            // Cairo time zone
            assertEquals("Africa/Cairo", ZoneId.systemDefault().id)

            val instant = Clock.System.now()

            tester.insert {
                it[x_timestamp_col] = instant.toDeprecatedInstant()
            }

            assertEquals(
                instant.toDeprecatedInstant(),
                tester.selectAll().single()[tester.x_timestamp_col]
            )
        }
    }

    @Test
    fun testTimestampAlwaysSavedInUTC() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))

        val tester = object : Table("tester") {
            val timestamp_col = timestamp("timestamp_col")
        }

        withTables(tester) {
            // Cairo time zone
            assertEquals("Africa/Cairo", ZoneId.systemDefault().id)

            val instant = Clock.System.now()

            tester.insert {
                it[timestamp_col] = instant
            }

            assertEquals(
                instant,
                tester.selectAll().single()[tester.timestamp_col]
            )
        }
    }

    @Test
    fun testInsertAndReadWithNonUtcTimezone() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))

        val tester = object : Table("testInsertAndReadWithNonUtcTimezone") {
            val ts = timestamp("ts")
        }

        val testerText = object : Table("testInsertAndReadWithNonUtcTimezone") {
            val ts = text("ts")
        }

        withTables(tester) {
            val now = Clock.System.now()

            tester.insert {
                it[tester.ts] = now
            }

            // It gives HH:MM:SS format in local time zone
            val nowTimeString = now.toLocalDateTime(TimeZone.currentSystemDefault()).time.toString().trim('0')

            // This check validates that the value on database has local time (instead of UTC)
            // It should prevent from the case when we convert value to UTC on insert, and back from UTC to local on reading
            testerText.selectAll().first()[testerText.ts].let { valueAsText ->
                kotlin.test.assertTrue(
                    valueAsText.contains(nowTimeString),
                    "Timestamp as text from database must contain the time in local time zone. Timestamp: $valueAsText, timeString: $nowTimeString"
                )
            }

            val valueFromDb = tester.selectAll().first()[tester.ts]
            assertEquals(now, valueFromDb)
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
            assertEqualDateTime(
                d1.toLocalDateTime().toKotlinLocalDateTime(),
                d2.toLocalDateTime().toKotlinLocalDateTime()
            )
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

val today: LocalDate = now().date

object CitiesTime : IntIdTable("CitiesTime") {
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}

@Serializable
data class ModifierData(val userId: Int, val timestamp: LocalDateTime)

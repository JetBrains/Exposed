package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.json.extract
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.*
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.time.Duration

open class KotlinTimeBaseTest : DatabaseTestsBase() {

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
                SchemaUtils.drop(testDate)
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
            val dateTimeWithFewNanos = dateTime.plus(nanos).toLocalDateTime(TimeZone.currentSystemDefault())
            val dateTimeWithManyNanos = dateTime.plus(nanos * 7).toLocalDateTime(TimeZone.currentSystemDefault())
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
            val ts = timestamp("ts")
            val tsn = timestamp("tsn").nullable()
        }

        val now = Clock.System.now()

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
                SchemaUtils.drop(testTable)
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

                val inYear2000 = testTable.defaultDate.castTo<String>(TextColumnType()) like "2000%"
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
                SchemaUtils.drop(testTable)
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

            val sameMonthResult = testTable.selectAll().where { testTable.created.month() eq testTable.deleted.month() }.toList()
            assertEquals(2, sameMonthResult.size)

            val year2023 = if (currentDialectTest is PostgreSQLDialect) {
                // PostgreSQL requires explicit type cast to resolve function date_part
                dateParam(mayTheFourth).castTo<LocalDate>(KotlinLocalDateColumnType()).year()
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
            val requiresExplicitDTCast = listOf(TestDB.ORACLE, TestDB.H2_ORACLE, TestDB.H2_PSQL, TestDB.H2_SQLSERVER)
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

        withTables(excludeSettings = TestDB.allH2TestDB + TestDB.SQLITE + TestDB.SQLSERVER + TestDB.ORACLE, tester) {
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

        withDb(excludeSettings = listOf(TestDB.MARIADB)) { testDB ->
            if (!isOldMySql()) {
                SchemaUtils.create(testTable)

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
                val isOriginalTimeZonePreserved = testDB !in listOf(
                    TestDB.POSTGRESQL,
                    TestDB.POSTGRESQLNG,
                    TestDB.MYSQL
                )
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
    }

    @Test
    fun testTimestampWithTimeZoneThrowsExceptionForUnsupportedDialects() {
        val testTable = object : IntIdTable("TestTable") {
            val timestampWithTimeZone = timestampWithTimeZone("timestamptz-column")
        }

        withDb(db = listOf(TestDB.MYSQL, TestDB.MARIADB)) { testDB ->
            if (testDB == TestDB.MARIADB || isOldMySql()) {
                expectException<UnsupportedByDialectException> {
                    SchemaUtils.create(testTable)
                }
            }
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

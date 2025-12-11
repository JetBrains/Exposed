package org.jetbrains.exposed.v1.jodatime

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.function
import org.jetbrains.exposed.v1.core.get
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.slice
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalTime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JodaTimeTests : DatabaseTestsBase() {
    init {
        DateTimeZone.setDefault(DateTimeZone.UTC)
    }

    private val timestampWithTimeZoneUnsupportedDB = setOf(TestDB.MARIADB, TestDB.MYSQL_V5)

    @Test
    fun jodaTimeFunctions() {
        withTables(CitiesTime) {
            val now = DateTime.now()

            val cityID = CitiesTime.insertAndGetId {
                it[name] = "St. Petersburg"
                it[local_time] = now.toDateTime()
            }

            val insertedYear = CitiesTime.select(CitiesTime.local_time.year()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.year()]
            val insertedMonth = CitiesTime.select(CitiesTime.local_time.month()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.month()]
            val insertedDay = CitiesTime.select(CitiesTime.local_time.day()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.day()]
            val insertedHour = CitiesTime.select(CitiesTime.local_time.hour()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.hour()]
            val insertedMinute = CitiesTime.select(CitiesTime.local_time.minute()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.minute()]
            val insertedSecond = CitiesTime.select(CitiesTime.local_time.second()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.second()]

            assertEquals(now.year, insertedYear)
            assertEquals(now.monthOfYear, insertedMonth)
            assertEquals(now.dayOfMonth, insertedDay)
            assertEquals(now.hourOfDay, insertedHour)
            assertEquals(now.minuteOfHour, insertedMinute)
            assertEquals(now.secondOfMinute, insertedSecond)
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
                assertEquals(today.monthOfYear, result1[month])
                assertEquals(today.dayOfMonth, result1[day])

                val lastDayOfMonth = CustomDateFunction(
                    "date",
                    testTable.dateCol,
                    stringLiteral("start of month"),
                    stringLiteral("+1 month"),
                    stringLiteral("-1 day")
                )
                val nextMonth = DateTime.parse("${today.year}-${today.monthOfYear}-01").plusMonths(1)
                val expectedLastDayOfMonth = nextMonth.minusDays(1)

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
                    it[defaultDate] = DateTime.parse("2000-12-25")
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

                val twoYearsAgo = today.minusYears(2)
                val twoYearsInFuture = today.plusYears(2)
                val isWithinTwoYears = testTable.defaultDate.between(twoYearsAgo, twoYearsInFuture)
                assertEquals(2, testTable.selectAll().where { isWithinTwoYears }.count())

                val yesterday = today.minusDays(1)

                testTable.deleteWhere {
                    testTable.defaultDate.day() eq dateParam(
                        yesterday
                    ).day()
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
            val mayTheFourth = DateTime.parse("2023-05-04")
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth
            }
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth.plusDays(1)
            }

            val sameDateResult = testTable.selectAll().where { testTable.created eq testTable.deleted }.toList()
            assertEquals(1, sameDateResult.size)
            assertEquals(mayTheFourth, sameDateResult.single()[testTable.deleted])

            val sameMonthResult = testTable.selectAll().where {
                testTable.created.month() eq testTable.deleted.month()
            }.toList()
            assertEquals(2, sameMonthResult.size)

            val year2023 = if (currentDialectTest is PostgreSQLDialect) {
                // PostgreSQL requires explicit type cast to resolve function date_part
                dateParam(mayTheFourth)
                    .castTo(JodaLocalDateColumnType()).year()
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

        withTables(testTableDT) {
            val mayTheFourthDT = DateTime.parse("2011-05-04T13:00:21.871130789Z")
            val nowDT = DateTime.now()
            val id1 = testTableDT.insertAndGetId {
                it[created] = mayTheFourthDT
                it[modified] = mayTheFourthDT
            }
            val id2 = testTableDT.insertAndGetId {
                it[created] = mayTheFourthDT
                it[modified] = nowDT
            }

            val createdMayFourth = testTableDT.selectAll().where {
                testTableDT.created eq dateTimeParam(mayTheFourthDT)
            }.count()
            assertEquals(2, createdMayFourth)

            val modifiedAtSameDT = testTableDT.selectAll().where {
                testTableDT.modified eq testTableDT.created
            }.single()
            assertEquals(id1, modifiedAtSameDT[testTableDT.id])

            val modifiedAtLaterDT = testTableDT.selectAll().where {
                testTableDT.modified greater testTableDT.created
            }.single()
            assertEquals(id2, modifiedAtLaterDT[testTableDT.id])
        }
    }

    @Test
    fun testDateTimeAsJsonB() {
        val tester = object : Table("tester") {
            val created = datetime("created")
            val modified = jsonb<ModifierData>("modified", Json.Default)
        }

        withTables(excludeSettings = TestDB.ALL_H2_V2 + TestDB.SQLSERVER + TestDB.ORACLE, tester) { testDb ->
            val dateTimeNow = DateTime.now()
            tester.insert {
                it[created] = dateTimeNow.minusYears(1)
                it[modified] = ModifierData(1, dateTimeNow)
            }
            tester.insert {
                it[created] = dateTimeNow.plusYears(1)
                it[modified] = ModifierData(2, dateTimeNow)
            }

            val prefix = if (currentDialectTest is PostgreSQLDialect) "" else "."

            // value extracted in same manner it is stored, a json string
            val modifiedAsString = tester.modified.extract<String>("${prefix}timestamp")
            val allModifiedAsString = tester.select(modifiedAsString)
            assertTrue(allModifiedAsString.all { it[modifiedAsString] == dateTimeNow.toString() })

            // PostgreSQL requires explicit type cast to timestamp for in-DB comparison
            val dateModified = if (currentDialectTest is PostgreSQLDialect) {
                tester.modified.extract<DateTime>("${prefix}timestamp").castTo(JodaLocalDateTimeColumnType())
            } else {
                tester.modified.extract<DateTime>("${prefix}timestamp")
            }
            // SQLite requires JSON() function to convert JSONB binary format to readable text format
            val modifiedColumn = if (testDb == TestDB.SQLITE) {
                tester.modified.function("JSON")
            } else {
                tester.modified
            }
            val modifiedBeforeCreation = tester
                .select(tester.created, modifiedColumn)
                .where { dateModified less tester.created }
                .single()
            assertEquals(2, modifiedBeforeCreation[modifiedColumn]?.userId)
        }
    }

    @Test
    fun testTimestampWithTimeZone() {
        val testTable = object : IntIdTable("TestTable") {
            val timestampWithTimeZone = timestampWithTimeZone("timestamptz-column")
        }

        withTables(excludeSettings = timestampWithTimeZoneUnsupportedDB, testTable) { testDB ->
            // Cairo time zone
            DateTimeZone.setDefault(DateTimeZone.forID("Africa/Cairo"))
            assertEquals("Africa/Cairo", DateTimeZone.getDefault().id)

            val cairoNow = DateTime.now(DateTimeZone.getDefault())

            val cairoId = testTable.insertAndGetId {
                it[timestampWithTimeZone] = cairoNow
            }

            val cairoNowInsertedInCairoTimeZone = testTable.selectAll().where { testTable.id eq cairoId }
                .single()[testTable.timestampWithTimeZone]

            // UTC time zone
            DateTimeZone.setDefault(DateTimeZone.UTC)
            assertEquals("UTC", DateTimeZone.getDefault().id)

            val cairoNowRetrievedInUTCTimeZone = testTable.selectAll().where { testTable.id eq cairoId }
                .single()[testTable.timestampWithTimeZone]

            val utcID = testTable.insertAndGetId {
                it[timestampWithTimeZone] = cairoNow
            }

            val cairoNowInsertedInUTCTimeZone = testTable.selectAll().where { testTable.id eq utcID }
                .single()[testTable.timestampWithTimeZone]

            // Tokyo time zone
            DateTimeZone.setDefault(DateTimeZone.forID("Asia/Tokyo"))
            assertEquals("Asia/Tokyo", DateTimeZone.getDefault().id)

            val cairoNowRetrievedInTokyoTimeZone = testTable.selectAll().where { testTable.id eq cairoId }
                .single()[testTable.timestampWithTimeZone]

            val tokyoID = testTable.insertAndGetId {
                it[timestampWithTimeZone] = cairoNow
            }

            val cairoNowInsertedInTokyoTimeZone = testTable.selectAll().where { testTable.id eq tokyoID }
                .single()[testTable.timestampWithTimeZone]

            // PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone.
            // To preserve the original time zone, store the time zone information in a separate column.
            val isOriginalTimeZonePreserved = testDB !in (TestDB.ALL_POSTGRES + TestDB.ALL_MYSQL)
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

        withTables(excludeSettings = timestampWithTimeZoneUnsupportedDB, testTable) {
            // UTC time zone
            DateTimeZone.setDefault(DateTimeZone.UTC)
            assertEquals("UTC", DateTimeZone.getDefault().id)

            val now = DateTime.parse("2023-05-04T05:04:01.123123123+00:00")
            val nowId = testTable.insertAndGetId {
                it[timestampWithTimeZone] = now
            }

            assertEquals(
                now.toLocalTime(),
                testTable.select(testTable.timestampWithTimeZone.time()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.time()]
            )
        }
    }

    @Test
    fun testCurrentDateTimeFunction() {
        val fakeTestTable = object : IntIdTable("fakeTable") {}

        withTables(fakeTestTable) {
            fun currentDbDateTime(): DateTime {
                return fakeTestTable.select(CurrentDateTime).first()[CurrentDateTime]
            }

            fakeTestTable.insert {}

            currentDbDateTime()
        }
    }

    @Test
    fun testDateTimeAsArray() {
        val defaultDates = listOf(today)
        val defaultDateTimes = listOf(DateTime.now())
        val tester = object : Table("array_tester") {
            val dates = array("dates", JodaLocalDateColumnType()).default(defaultDates)
            val datetimes = array("datetimes", JodaLocalDateTimeColumnType()).default(defaultDateTimes)
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.POSTGRESQL - TestDB.H2_V2 - TestDB.H2_V2_PSQL, tester) {
            tester.insert { }
            val result1 = tester.selectAll().single()
            assertEqualLists(result1[tester.dates], defaultDates)
            assertEqualLists(result1[tester.datetimes], defaultDateTimes)

            val datesInput = List(3) { DateTime.parse("${2020 + it}-5-4") }
            val datetimeInput = List(3) { DateTime(2020 + it, 5, 4, 9, 9, 9) }
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
            DateTimeZone.setDefault(DateTimeZone.UTC)
            assertEquals("UTC", DateTimeZone.getDefault().id)

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

val today: DateTime = DateTime.now(DateTimeZone.UTC).withTimeAtStartOfDay()

object CitiesTime : IntIdTable("CitiesTime") {
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}

@Serializable
data class ModifierData(
    val userId: Int,
    @Serializable(with = DateTimeSerializer::class)
    val timestamp: DateTime
)

object DateTimeSerializer : KSerializer<DateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): DateTime = DateTime.parse(decoder.decodeString())
}

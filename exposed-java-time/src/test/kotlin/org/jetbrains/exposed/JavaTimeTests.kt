package org.jetbrains.exposed

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.javatime.*
import org.jetbrains.exposed.sql.json.extract
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import java.time.*

@Suppress("LargeClass")
class JavaTimeTests : DatabaseTestsBase() {

    private val timestampWithTimeZoneUnsupportedDB = TestDB.ALL_MARIADB + TestDB.MYSQL_V5

    @Test
    fun javaTimeFunctions() {
        withTables(CitiesTime) { testDb ->
            val now = LocalDateTime.now()

            val cityID = CitiesTime.insertAndGetId {
                it[name] = "Tunisia"
                it[datetime] = now
            }

            val insertedYear = CitiesTime.select(CitiesTime.datetime.year()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.datetime.year()]
            val insertedMonth = CitiesTime.select(CitiesTime.datetime.month()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.datetime.month()]
            val insertedDay = CitiesTime.select(CitiesTime.datetime.day()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.datetime.day()]
            val insertedHour = CitiesTime.select(CitiesTime.datetime.hour()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.datetime.hour()]
            val insertedMinute = CitiesTime.select(CitiesTime.datetime.minute()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.datetime.minute()]
            val insertedSecond = CitiesTime.select(CitiesTime.datetime.second()).where { CitiesTime.id.eq(cityID) }.single()[CitiesTime.datetime.second()]

            assertEquals(now.year, insertedYear)
            assertEquals(now.month.value, insertedMonth)
            assertEquals(now.dayOfMonth, insertedDay)
            assertEquals(now.hour, insertedHour)
            assertEquals(now.minute, insertedMinute)
            assertEquals(
                if (now.nano > 500_000_000 && testDb in TestDB.ALL_MYSQL) {
                    now.second + 1 // MySQL default precision is 0 and it rounds up the seconds
                } else {
                    now.second
                },
                insertedSecond
            )
        }
    }

    // Checks that old numeric datetime columns works fine with new text representation
    @Test
    fun testSQLiteDateTimeFieldRegression() {
        val testDateTime = object : IntIdTable("TestDateTime") {
            val datetime = datetime("datetime").defaultExpression(CurrentDateTime)
        }

        withDb(TestDB.SQLITE) {
            try {
                exec("CREATE TABLE IF NOT EXISTS TestDateTime (id INTEGER PRIMARY KEY AUTOINCREMENT, \"datetime\" NUMERIC DEFAULT (CURRENT_TIMESTAMP) NOT NULL);")
                testDateTime.insert { }
                val year = testDateTime.datetime.year()
                val month = testDateTime.datetime.month()
                val day = testDateTime.datetime.day()
                val hour = testDateTime.datetime.hour()
                val minute = testDateTime.datetime.minute()

                val result = testDateTime.select(year, month, day, hour, minute).single()

                val now = LocalDateTime.now()
                assertEquals(now.year, result[year])
                assertEquals(now.monthValue, result[month])
                assertEquals(now.dayOfMonth, result[day])
                assertEquals(now.hour, result[hour])
                assertEquals(now.minute, result[minute])
            } finally {
                SchemaUtils.drop(testDateTime)
            }
        }
    }

    @Test
    fun testStoringLocalDateTimeWithNanos() {
        withDb { testDb ->
            val maxPrecisionAllowed: Byte = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB, in TestDB.ALL_POSTGRES -> 6
                TestDB.SQLSERVER -> 7
                else -> 9
            }

            val tester = object : IntIdTable("tester") {
                val datetime = datetime("time", maxPrecisionAllowed)
            }

            try {
                SchemaUtils.create(tester)

                val dateTime = when (testDb) {
                    in TestDB.ALL_MYSQL_MARIADB, in TestDB.ALL_POSTGRES -> LocalDateTime.parse("2023-05-04T05:04:00.111111")
                    TestDB.SQLSERVER -> LocalDateTime.parse("2023-05-04T05:04:00.1111111")
                    else -> LocalDateTime.parse("2023-05-04T05:04:00.111111111")
                }
                tester.insert {
                    it[tester.datetime] = dateTime
                }

                assertEquals(dateTime, tester.selectAll().map { it[tester.datetime] }.single())
            } finally {
                SchemaUtils.drop(tester)
            }
        }
    }

    @Test
    fun `test selecting Instant using expressions`() {
        val testTable = object : Table() {
            val ts = timestamp("ts", 6)
            val tsn = timestamp("tsn", 6).nullable()
        }

        val now = Instant.now()

        withTables(testTable) {
            testTable.insert {
                it[ts] = now
                it[tsn] = now
            }

            val maxTsExpr = testTable.ts.max()
            val maxTimestamp = testTable.select(maxTsExpr).single()[maxTsExpr]
            assertEquals(now, maxTimestamp)

            val minTsExpr = testTable.ts.min()
            val minTimestamp = testTable.select(minTsExpr).single()[minTsExpr]
            assertEquals(now, minTimestamp)

            val maxTsnExpr = testTable.tsn.max()
            val maxNullableTimestamp = testTable.select(maxTsnExpr).single()[maxTsnExpr]
            assertEquals(now, maxNullableTimestamp)

            val minTsnExpr = testTable.tsn.min()
            val minNullableTimestamp = testTable.select(minTsnExpr).single()[minTsnExpr]
            assertEquals(now, minNullableTimestamp)
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
                assertEquals(today.monthValue, result1[month])
                assertEquals(today.dayOfMonth, result1[day])

                val lastDayOfMonth = CustomDateFunction(
                    "date",
                    testTable.dateCol,
                    stringLiteral("start of month"),
                    stringLiteral("+1 month"),
                    stringLiteral("-1 day")
                )
                val nextMonth = LocalDate.of(today.year, today.monthValue, 1).plusMonths(1)
                val expectedLastDayOfMonth = nextMonth.minusDays(1)

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
                    it[defaultDate] = LocalDate.of(2000, 12, 25)
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
            val mayTheFourth = LocalDate.of(2023, 5, 4)
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth
            }
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth.plusDays(1L)
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
                dateParam(mayTheFourth).castTo(JavaLocalDateColumnType()).year()
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
            val created = datetime("created", 6)
            val modified = datetime("modified", 6)
        }

        withTables(testTableDT) {
            val mayTheFourth = "2011-05-04T13:00:21.871130"
            val mayTheFourthDT = LocalDateTime.parse(mayTheFourth)
            val nowDT = LocalDateTime.now()
            val id1 = testTableDT.insertAndGetId {
                it[created] = mayTheFourthDT
                it[modified] = mayTheFourthDT
            }
            val id2 = testTableDT.insertAndGetId {
                it[created] = mayTheFourthDT
                it[modified] = nowDT
            }

            val createdMayFourth = testTableDT.selectAll().where { testTableDT.created eq dateTimeParam(mayTheFourthDT) }.count()
            assertEquals(2, createdMayFourth)

            val modifiedAtSameDT = testTableDT.selectAll().where { testTableDT.modified eq testTableDT.created }.single()
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

        withTables(excludeSettings = TestDB.ALL_H2 + TestDB.SQLITE + TestDB.SQLSERVER + TestDB.ORACLE, tester) {
            val dateTimeNow = LocalDateTime.now()
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
                tester.modified.extract<LocalDateTime>("${prefix}timestamp").castTo(JavaLocalDateTimeColumnType())
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
                assertEquals(cairoNow, cairoNowInsertedInCairoTimeZone)
                assertEquals(cairoNow, cairoNowInsertedInUTCTimeZone)
                assertEquals(cairoNow, cairoNowInsertedInTokyoTimeZone)

                // Assert that time zone is preserved when the same record is retrieved in different time zones
                assertEquals(cairoNow, cairoNowRetrievedInUTCTimeZone)
                assertEquals(cairoNow, cairoNowRetrievedInTokyoTimeZone)
            } else {
                // Assert equivalence in UTC when the same value is inserted in different time zones
                assertEquals(cairoNowInsertedInCairoTimeZone, cairoNowInsertedInUTCTimeZone)
                assertEquals(cairoNowInsertedInUTCTimeZone, cairoNowInsertedInTokyoTimeZone)

                // Assert equivalence in UTC when the same record is retrieved in different time zones
                assertEquals(cairoNowRetrievedInUTCTimeZone, cairoNowRetrievedInTokyoTimeZone)
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

        withTables(excludeSettings = timestampWithTimeZoneUnsupportedDB + TestDB.ALL_H2_V1, testTable) { testDb ->
            // UTC time zone
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneOffset.UTC))
            assertEquals("UTC", ZoneId.systemDefault().id)

            val now = OffsetDateTime.parse("2023-05-04T05:04:01.123123123+00:00")
            val nowId = testTable.insertAndGetId {
                it[timestampWithTimeZone] = now
            }

            assertEquals(
                now.toLocalDate(),
                testTable.select(testTable.timestampWithTimeZone.date()).where { testTable.id eq nowId }
                    .single()[testTable.timestampWithTimeZone.date()]
            )

            val expectedTime =
                when (testDb) {
                    TestDB.MYSQL_V8 -> OffsetDateTime.parse("2023-05-04T05:04:01+00:00") // MySQL default precision is 0
                    TestDB.SQLSERVER, TestDB.ORACLE, in TestDB.ALL_POSTGRES, in TestDB.ALL_H2_V2 ->
                        OffsetDateTime.parse("2023-05-04T05:04:01.123123+00:00")
                    else -> now
                }.toLocalTime()
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

        withTables(excludeSettings = TestDB.ALL_H2_V1, fakeTestTable) {
            fun currentDbDateTime(): LocalDateTime {
                return fakeTestTable.select(CurrentDateTime).first()[CurrentDateTime]
            }

            fakeTestTable.insert {}

            currentDbDateTime()
        }
    }

    @Test
    fun testDateTimeAsArray() {
        val defaultDates = listOf(today)
        val defaultDateTimes = listOf(LocalDateTime.now())
        val tester = object : Table("array_tester") {
            val dates = array("dates", JavaLocalDateColumnType()).default(defaultDates)
            val datetimes = array("datetimes", JavaLocalDateTimeColumnType()).default(defaultDateTimes)
        }

        withTables(excludeSettings = TestDB.entries - TestDB.POSTGRESQL - TestDB.H2_V2, tester) {
            tester.insert { }
            val result1 = tester.selectAll().single()
            assertEqualLists(result1[tester.dates], defaultDates)
            assertEqualLists(result1[tester.datetimes], defaultDateTimes)

            val datesInput = List(3) { LocalDate.of(2020 + it, 5, 4) }
            val datetimeInput = List(3) { LocalDateTime.of(2020 + it, 5, 4, 9, 9, 9) }
            tester.insert {
                it[dates] = datesInput
                it[datetimes] = datetimeInput
            }

            val lastDate = tester.dates[3]
            val firstTwoDatetimes = tester.datetimes.slice(1, 2)
            val result2 = tester.select(lastDate, firstTwoDatetimes).where {
                tester.dates[1].year() eq 2020
            }.single()
            assertEquals(datesInput.last(), result2[lastDate])
            assertEqualLists(result2[firstTwoDatetimes], datetimeInput.take(2))
        }
    }

    @Test
    fun testSelectByTimeLiteralEquality() {
        val tableWithTime = object : IntIdTable("TableWithTime") {
            val time = time("time")
        }
        withTables(tableWithTime) {
            val localTime = LocalTime.of(13, 0)
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
    fun testTimestampAlwaysSavedInUTC() {
        val tester = object : Table("tester") {
            val timestamp_col = timestamp("timestamp_col", 3)
        }

        withTables(tester) {
            // Cairo time zone
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))
            assertEquals("Africa/Cairo", ZoneId.systemDefault().id)

            val instant = Instant.now()

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
    fun testDateTimeWithCustomPrecision() {
        val localDateTime3 = LocalDateTime.parse("2025-02-26T11:21:00.838")
        val localDateTime9 = LocalDateTime.parse("2025-02-26T11:21:00.838123456")

        withDb { testDb ->
            val maxPrecisionAllowed: Byte = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> 6
                TestDB.SQLSERVER -> 7
                else -> 9
            }

            val tester = object : Table("tester") {
                val datetimeWithDefaultPrecision = datetime("datetimeWithDefaultPrecision")
                val datetimeWithPrecision3 = datetime("datetimeWithPrecision3", 3)
                val datetimeWithMaxPrecision = datetime("datetimeWithMaxPrecision", maxPrecisionAllowed)
            }

            try {
                SchemaUtils.create(tester)

                tester.insert {
                    it[datetimeWithDefaultPrecision] = localDateTime9
                    it[datetimeWithPrecision3] = localDateTime9
                    it[datetimeWithMaxPrecision] = localDateTime9
                }

                assertEquals(
                    when (testDb) {
                        TestDB.MARIADB -> LocalDateTime.parse("2025-02-26T11:21:00") // MariaDB default precision is 0
                        in TestDB.ALL_MYSQL -> LocalDateTime.parse("2025-02-26T11:21:01") // MySQL default precision is 0 and it rounds up
                        TestDB.SQLSERVER -> LocalDateTime.parse("2025-02-26T11:21:00.8381235") // SQL Server default precision is 7 and it rounds up
                        TestDB.ORACLE, in TestDB.ALL_POSTGRES, in TestDB.ALL_H2 -> LocalDateTime.parse("2025-02-26T11:21:00.838123")
                        else -> localDateTime9
                    },
                    tester.selectAll().single()[tester.datetimeWithDefaultPrecision]
                )

                assertEquals(
                    when (testDb) {
                        TestDB.SQLITE -> localDateTime9 // SQLite stores the value as is because it is of type TEXT and the precision is ignored
                        else -> localDateTime3
                    },
                    tester.selectAll().single()[tester.datetimeWithPrecision3]
                )

                assertEquals(
                    when (testDb) {
                        in TestDB.ALL_MYSQL_MARIADB, in TestDB.ALL_POSTGRES -> LocalDateTime.parse("2025-02-26T11:21:00.838123")
                        TestDB.SQLSERVER -> LocalDateTime.parse("2025-02-26T11:21:00.8381235") // SQL Server's max precision is 7 and it rounds up
                        else -> localDateTime9
                    },
                    tester.selectAll().single()[tester.datetimeWithMaxPrecision]
                )

                tester.deleteWhere {
                    tester.datetimeWithPrecision3 eq when (testDb) {
                        TestDB.SQLITE -> localDateTime9 // SQLite stores the value as is because it is of type TEXT and the precision is ignored
                        else -> localDateTime3
                    }
                }

                tester.insert {
                    it[datetimeWithDefaultPrecision] = localDateTime3
                    it[datetimeWithPrecision3] = localDateTime3
                    it[datetimeWithMaxPrecision] = localDateTime3
                }

                assertEquals(
                    when (testDb) {
                        TestDB.MARIADB -> LocalDateTime.parse("2025-02-26T11:21:00") // MariaDB default precision is 0
                        in TestDB.ALL_MYSQL -> LocalDateTime.parse("2025-02-26T11:21:01") // MySQL default precision is 0 and it rounds up
                        else -> localDateTime3
                    },
                    tester.selectAll().single()[tester.datetimeWithDefaultPrecision]
                )

                assertEquals(
                    localDateTime3,
                    tester.selectAll().single()[tester.datetimeWithPrecision3]
                )

                assertEquals(
                    localDateTime3,
                    tester.selectAll().single()[tester.datetimeWithMaxPrecision]
                )
            } finally {
                SchemaUtils.drop(tester)
            }
        }
    }

    @Test
    fun testTimeWithCustomPrecision() {
        val localTime2 = LocalTime.parse("01:23:45.670")
        val localTime9 = LocalTime.parse("01:23:45.678123456")

        withDb { testDb ->
            val maxPrecisionAllowed: Byte = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> 6
                TestDB.SQLSERVER -> 7
                else -> 9
            }

            val tester = object : Table("tester") {
                val timeWithDefaultPrecision = time("timeWithDefaultPrecision")
                val timeWithPrecision3 = time("timeWithPrecision3", 3)
                val timeWithMaxPrecision = time("timeWithMaxPrecision", maxPrecisionAllowed)
            }

            try {
                SchemaUtils.create(tester)

                tester.insert {
                    it[timeWithDefaultPrecision] = localTime9
                    it[timeWithPrecision3] = localTime9
                    it[timeWithMaxPrecision] = localTime9
                }

                assertEquals(
                    when (testDb) {
                        TestDB.MARIADB -> LocalTime.parse("01:23:45") // MariaDB default precision is 0
                        in TestDB.ALL_MYSQL, in TestDB.ALL_H2 -> LocalTime.parse("01:23:46") // MySQL and H2 default precision is 0 and they round up
                        TestDB.SQLSERVER -> LocalTime.parse("01:23:45.6781235") // SQL Server default precision is 7 and it rounds up
                        TestDB.ORACLE, in TestDB.ALL_POSTGRES -> LocalTime.parse("01:23:45.678123")
                        else -> localTime9
                    },
                    tester.selectAll().single()[tester.timeWithDefaultPrecision]
                )

                assertEquals(
                    when (testDb) {
                        TestDB.SQLITE -> localTime9 // SQLite stores the value as is because it is of type TEXT and the precision is ignored
                        else -> LocalTime.parse("01:23:45.678")
                    },
                    tester.selectAll().single()[tester.timeWithPrecision3]
                )

                assertEquals(
                    when (testDb) {
                        in TestDB.ALL_MYSQL -> LocalTime.parse("01:23:45.678124") // MySQL rounds up
                        TestDB.MARIADB, in TestDB.ALL_POSTGRES -> LocalTime.parse("01:23:45.678123")
                        TestDB.SQLSERVER -> LocalTime.parse("01:23:45.6781235") // SQL Server rounds up
                        else -> localTime9
                    },
                    tester.selectAll().single()[tester.timeWithMaxPrecision]
                )

                tester.deleteWhere {
                    tester.timeWithPrecision3 eq when (testDb) {
                        TestDB.SQLITE -> localTime9 // SQLite stores the value as is because it is of type TEXT and the precision is ignored
                        else -> LocalTime.parse("01:23:45.678")
                    }
                }

                tester.insert {
                    it[timeWithDefaultPrecision] = localTime2
                    it[timeWithPrecision3] = localTime2
                    it[timeWithMaxPrecision] = localTime2
                }

                assertEquals(
                    when (testDb) {
                        TestDB.MARIADB -> LocalTime.parse("01:23:45") // MariaDB default precision is 0
                        in TestDB.ALL_MYSQL, in TestDB.ALL_H2 -> LocalTime.parse("01:23:46") // MySQL and H2 default precision is 0 and they round up
                        else -> localTime2
                    },
                    tester.selectAll().single()[tester.timeWithDefaultPrecision]
                )

                assertEquals(
                    localTime2,
                    tester.selectAll().single()[tester.timeWithPrecision3]
                )

                assertEquals(
                    localTime2,
                    tester.selectAll().single()[tester.timeWithMaxPrecision]
                )
            } finally {
                SchemaUtils.drop(tester)
            }
        }
    }

    @Test
    fun testTimestampWithCustomPrecision() {
        val instant2 = Instant.parse("2025-02-26T01:23:45.670Z")
        val instant9 = Instant.parse("2025-02-26T01:23:45.678123456Z")

        withDb { testDb ->
            val maxPrecisionAllowed: Byte = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> 6
                TestDB.SQLSERVER -> 7
                else -> 9
            }

            val tester = object : Table("tester") {
                val timestampWithDefaultPrecision = timestamp("timestampWithDefaultPrecision")
                val timestampWithPrecision3 = timestamp("timestampWithPrecision3", 3)
                val timestampWithMaxPrecision = timestamp("timestampWithMaxPrecision", maxPrecisionAllowed)
            }

            try {
                SchemaUtils.create(tester)

                tester.insert {
                    it[timestampWithDefaultPrecision] = instant9
                    it[timestampWithPrecision3] = instant9
                    it[timestampWithMaxPrecision] = instant9
                }

                assertEquals(
                    when (testDb) {
                        TestDB.MARIADB -> Instant.parse("2025-02-26T01:23:45Z") // MariaDB default precision is 0
                        in TestDB.ALL_MYSQL -> Instant.parse("2025-02-26T01:23:46Z") // MySQL default precision is 0 and it rounds up
                        TestDB.SQLSERVER -> Instant.parse("2025-02-26T01:23:45.6781235Z") // SQL Server default precision is 7 and it rounds up
                        TestDB.ORACLE, in TestDB.ALL_POSTGRES, in TestDB.ALL_H2 -> Instant.parse("2025-02-26T01:23:45.678123Z")
                        else -> instant9
                    },
                    tester.selectAll().single()[tester.timestampWithDefaultPrecision]
                )

                assertEquals(
                    when (testDb) {
                        TestDB.SQLITE -> instant9 // SQLite stores the value as is because it is of type TEXT and the precision is ignored
                        else -> Instant.parse("2025-02-26T01:23:45.678Z")
                    },
                    tester.selectAll().single()[tester.timestampWithPrecision3]
                )

                assertEquals(
                    when (testDb) {
                        in TestDB.ALL_MYSQL_MARIADB, in TestDB.ALL_POSTGRES -> Instant.parse("2025-02-26T01:23:45.678123Z")
                        TestDB.SQLSERVER -> Instant.parse("2025-02-26T01:23:45.6781235Z") // SQL Server rounds up
                        else -> instant9
                    },
                    tester.selectAll().single()[tester.timestampWithMaxPrecision]
                )

                tester.deleteWhere {
                    tester.timestampWithPrecision3 eq when (testDb) {
                        TestDB.SQLITE -> instant9 // SQLite stores the value as is because it is of type TEXT and the precision is ignored
                        else -> Instant.parse("2025-02-26T01:23:45.678Z")
                    }
                }

                tester.insert {
                    it[timestampWithDefaultPrecision] = instant2
                    it[timestampWithPrecision3] = instant2
                    it[timestampWithMaxPrecision] = instant2
                }

                assertEquals(
                    when (testDb) {
                        TestDB.MARIADB -> Instant.parse("2025-02-26T01:23:45Z") // MariaDB default precision is 0
                        in TestDB.ALL_MYSQL -> Instant.parse("2025-02-26T01:23:46Z") // MySQL default precision is 0 and it rounds up
                        else -> instant2
                    },
                    tester.selectAll().single()[tester.timestampWithDefaultPrecision]
                )

                assertEquals(
                    instant2,
                    tester.selectAll().single()[tester.timestampWithPrecision3]
                )

                assertEquals(
                    instant2,
                    tester.selectAll().single()[tester.timestampWithMaxPrecision]
                )
            } finally {
                SchemaUtils.drop(tester)
            }
        }
    }

    @Test
    fun testTimestampWithTimeZoneWithCustomPrecision() {
        val offsetDateTime2 = OffsetDateTime.parse("2025-02-26T01:23:45.670Z")
        val offsetDateTime9 = OffsetDateTime.parse("2025-02-26T01:23:45.678123456Z")

        withDb(excludeSettings = timestampWithTimeZoneUnsupportedDB) { testDb ->
            val maxPrecisionAllowed: Byte = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> 6
                TestDB.SQLSERVER -> 7
                else -> 9
            }

            val tester = object : Table("tester") {
                val timestampWithTimeZoneDefaultPrecision = timestampWithTimeZone("timestampWithTimeZoneDefaultPrecision")
                val timestampWithTimeZone3 = timestampWithTimeZone("timestampWithTimeZone3", 3)
                val timestampWithTimeZoneMaxPrecision = timestampWithTimeZone("timestampWithTimeZoneMaxPrecision", maxPrecisionAllowed)
            }

            try {
                SchemaUtils.create(tester)

                tester.insert {
                    it[timestampWithTimeZoneDefaultPrecision] = offsetDateTime9
                    it[timestampWithTimeZone3] = offsetDateTime9
                    it[timestampWithTimeZoneMaxPrecision] = offsetDateTime9
                }

                assertEquals(
                    when (testDb) {
                        TestDB.MARIADB -> OffsetDateTime.parse("2025-02-26T01:23:45Z") // MariaDB default precision is 0
                        in TestDB.ALL_MYSQL -> OffsetDateTime.parse("2025-02-26T01:23:46Z") // MySQL default precision is 0 and it rounds up
                        TestDB.SQLSERVER -> OffsetDateTime.parse("2025-02-26T01:23:45.6781235Z") // SQL Server default precision is 7 and it rounds up
                        TestDB.ORACLE, in TestDB.ALL_POSTGRES, in TestDB.ALL_H2 -> OffsetDateTime.parse("2025-02-26T01:23:45.678123Z")
                        else -> offsetDateTime9
                    },
                    tester.selectAll().single()[tester.timestampWithTimeZoneDefaultPrecision]
                )

                assertEquals(
                    when (currentDialect) {
                        is SQLiteDialect -> offsetDateTime9 // SQLite stores the value as is because it is of type TEXT and the precision is ignored
                        else -> OffsetDateTime.parse("2025-02-26T01:23:45.678Z")
                    },
                    tester.selectAll().single()[tester.timestampWithTimeZone3]
                )

                assertEquals(
                    when (testDb) {
                        TestDB.MYSQL_V8, in TestDB.ALL_POSTGRES -> OffsetDateTime.parse("2025-02-26T01:23:45.678123Z")
                        TestDB.SQLSERVER -> OffsetDateTime.parse("2025-02-26T01:23:45.6781235Z") // SQL Server rounds up
                        else -> offsetDateTime9
                    },
                    tester.selectAll().single()[tester.timestampWithTimeZoneMaxPrecision]
                )

                tester.deleteWhere {
                    tester.timestampWithTimeZone3 eq when (currentDialect) {
                        is SQLiteDialect -> offsetDateTime9 // SQLite stores the value as is because it is of type TEXT and the precision is ignored
                        else -> OffsetDateTime.parse("2025-02-26T01:23:45.678Z")
                    }
                }

                tester.insert {
                    it[timestampWithTimeZoneDefaultPrecision] = offsetDateTime2
                    it[timestampWithTimeZone3] = offsetDateTime2
                    it[timestampWithTimeZoneMaxPrecision] = offsetDateTime2
                }

                assertEquals(
                    when (testDb) {
                        TestDB.MARIADB -> OffsetDateTime.parse("2025-02-26T01:23:45Z") // MariaDB default precision is 0
                        in TestDB.ALL_MYSQL -> OffsetDateTime.parse("2025-02-26T01:23:46Z") // MySQL default precision is 0 and it rounds up
                        else -> offsetDateTime2
                    },
                    tester.selectAll().single()[tester.timestampWithTimeZoneDefaultPrecision]
                )

                assertEquals(
                    offsetDateTime2,
                    tester.selectAll().single()[tester.timestampWithTimeZone3]
                )

                assertEquals(
                    offsetDateTime2,
                    tester.selectAll().single()[tester.timestampWithTimeZoneMaxPrecision]
                )
            } finally {
                SchemaUtils.drop(tester)
            }
        }
    }
}

val today: LocalDate = LocalDate.now()

object CitiesTime : IntIdTable("CitiesTime") {
    val name = varchar("name", 50)
    val datetime = datetime("datetime").nullable()
}

@Serializable
data class ModifierData(
    val userId: Int,
    @Serializable(with = DateTimeSerializer::class)
    val timestamp: LocalDateTime
)

object DateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}

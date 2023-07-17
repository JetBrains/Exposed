package org.jetbrains.exposed

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.jodatime.*
import org.jetbrains.exposed.sql.json.extract
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Test
import kotlin.test.assertEquals

open class JodaTimeBaseTest : DatabaseTestsBase() {
    init {
        DateTimeZone.setDefault(DateTimeZone.UTC)
    }

    @Test
    fun jodaTimeFunctions() {
        withTables(CitiesTime) {
            val now = DateTime.now()

            val cityID = CitiesTime.insertAndGetId {
                it[name] = "St. Petersburg"
                it[local_time] = now.toDateTime()
            }

            val insertedYear = CitiesTime.slice(CitiesTime.local_time.year()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.year()]
            val insertedMonth = CitiesTime.slice(CitiesTime.local_time.month()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.month()]
            val insertedDay = CitiesTime.slice(CitiesTime.local_time.day()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.day()]
            val insertedHour = CitiesTime.slice(CitiesTime.local_time.hour()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.hour()]
            val insertedMinute = CitiesTime.slice(CitiesTime.local_time.minute()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.minute()]
            val insertedSecond = CitiesTime.slice(CitiesTime.local_time.second()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.second()]

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

                val result1 = testTable.slice(year, month, day).selectAll().single()
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

                val result2 = testTable.slice(lastDayOfMonth).selectAll().single()
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
                    it[defaultDate] = DateTime.parse("2000-12-25")
                }
                val eventBId = testTable.insertAndGetId {
                    it[event] = "B"
                }

                val inYear2000 = testTable.defaultDate.castTo<String>(TextColumnType()) like "2000%"
                assertEquals(1, testTable.select { inYear2000 }.count())

                val todayResult1 = testTable.select { testTable.defaultDate eq today }.single()
                assertEquals(eventBId, todayResult1[testTable.id])

                testTable.update({ testTable.id eq eventAId }) {
                    it[testTable.defaultDate] = today
                }

                val todayResult2 = testTable.select { testTable.defaultDate eq today }.count()
                assertEquals(2, todayResult2)

                val twoYearsAgo = today.minusYears(2)
                val twoYearsInFuture = today.plusYears(2)
                val isWithinTwoYears = testTable.defaultDate.between(twoYearsAgo, twoYearsInFuture)
                assertEquals(2, testTable.select { isWithinTwoYears }.count())

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
            val mayTheFourth = DateTime.parse("2023-05-04")
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth
            }
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth.plusDays(1)
            }

            val sameDateResult = testTable.select { testTable.created eq testTable.deleted }.toList()
            assertEquals(1, sameDateResult.size)
            assertEquals(mayTheFourth, sameDateResult.single()[testTable.deleted])

            val sameMonthResult = testTable.select { testTable.created.month() eq testTable.deleted.month() }.toList()
            assertEquals(2, sameMonthResult.size)

            val year2023 = if (currentDialectTest is PostgreSQLDialect) {
                // PostgreSQL requires explicit type cast to resolve function date_part
                dateParam(mayTheFourth).castTo<DateTime>(DateColumnType(false)).year()
            } else {
                dateParam(mayTheFourth).year()
            }
            val createdIn2023 = testTable.select { testTable.created.year() eq year2023 }.toList()
            assertEquals(2, createdIn2023.size)
        }
    }

    @Test
    fun testDateTimeAsJsonB() {
        val tester = object : Table("tester") {
            val created = datetime("created")
            val modified = jsonb<ModifierData>("modified", Json.Default)
        }

        withTables(excludeSettings = TestDB.allH2TestDB + TestDB.SQLITE + TestDB.SQLSERVER + TestDB.ORACLE, tester) {
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
            val allModifiedAsString = tester.slice(modifiedAsString).selectAll()
            assertTrue(allModifiedAsString.all { it[modifiedAsString] == dateTimeNow.toString() })

            // PostgreSQL requires explicit type cast to timestamp for in-DB comparison
            val dateModified = if (currentDialectTest is PostgreSQLDialect) {
                tester.modified.extract<DateTime>("${prefix}timestamp").castTo(DateColumnType(true))
            } else {
                tester.modified.extract<DateTime>("${prefix}timestamp")
            }
            val modifiedBeforeCreation = tester.select { dateModified less tester.created }.single()
            assertEquals(2, modifiedBeforeCreation[tester.modified].userId)
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

val today: DateTime = DateTime.now().withTimeAtStartOfDay()

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

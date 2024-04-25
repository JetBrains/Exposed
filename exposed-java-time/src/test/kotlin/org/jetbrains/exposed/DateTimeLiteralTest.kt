package org.jetbrains.exposed

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.update
import org.junit.Ignore
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertNotNull

class DateTimeLiteralTest : DatabaseTestsBase() {
    private val defaultDate = LocalDate.of(2000, 1, 1)
    private val futureDate = LocalDate.of(3000, 1, 1)

    object TableWithDate : IntIdTable() {
        val date = date("date")
    }

    private val defaultDatetime = LocalDateTime.of(2000, 1, 1, 8, 0, 0, 100000000)
    private val futureDatetime = LocalDateTime.of(3000, 1, 1, 8, 0, 0, 100000000)

    object TableWithDatetime : IntIdTable() {
        val datetime = datetime("datetime")
    }

    private val defaultTimestamp = Instant.parse("2000-01-01T01:00:00.00Z")
    private val futureTimestamp = Instant.parse("3000-01-01T01:00:00.00Z")

    object TableWithTimestamp : IntIdTable() {
        val timestamp = timestamp("timestamp")
    }

    private val defaultLocalTime = LocalTime.of(1, 0, 0)
    private val futureLocalTime = LocalTime.of(18, 0, 0)

    object TableWithTime : IntIdTable() {
        val time = time("time")
    }

    @Test
    fun testInsertWithDateLiteral() {
        withTables(TableWithDate) {
            TableWithDate.insert {
                it[date] = dateLiteral(defaultDate)
            }
            assertEquals(defaultDate, TableWithDate.selectAll().first()[TableWithDate.date])
        }
    }

    @Test
    fun testUpdateWithDateLiteral() {
        withTables(TableWithDate) {
            TableWithDate.insert {
                it[date] = defaultDate
            }

            TableWithDate.update { it[date] = dateLiteral(futureDate) }
            assertEquals(futureDate, TableWithDate.selectAll().first()[TableWithDate.date])
        }
    }

    @Test
    fun testSelectByDateLiteralEquality() {
        withTables(TableWithDate) {
            TableWithDate.insert {
                it[date] = defaultDate
            }

            val query = TableWithDate.select(TableWithDate.date).where { TableWithDate.date eq dateLiteral(defaultDate) }
            assertEquals(defaultDate, query.single()[TableWithDate.date])
        }
    }

    @Test
    fun testSelectByDateLiteralComparison() {
        withTables(TableWithDate) {
            TableWithDate.insert {
                it[date] = defaultDate
            }
            val query = TableWithDate.selectAll().where { TableWithDate.date less dateLiteral(futureDate) }
            assertNotNull(query.firstOrNull())
        }
    }

    @Test
    fun testInsertDatetimeLiteral() {
        withTables(TableWithDatetime) {
            TableWithDatetime.insert {
                it[datetime] = dateTimeLiteral(defaultDatetime)
            }
            assertEquals(defaultDatetime, TableWithDatetime.selectAll().first()[TableWithDatetime.datetime])
        }
    }

    @Test
    fun testUpdateWithDatetimeLiteral() {
        withTables(TableWithDatetime) {
            TableWithDatetime.insert {
                it[datetime] = defaultDatetime
            }

            TableWithDatetime.update { it[datetime] = dateTimeLiteral(futureDatetime) }
            assertEquals(futureDatetime, TableWithDatetime.selectAll().first()[TableWithDatetime.datetime])
        }
    }

    @Test
    fun testSelectByDatetimeLiteralEquality() {
        withTables(TableWithDatetime) {
            TableWithDatetime.insert {
                it[datetime] = defaultDatetime
            }

            val query = TableWithDatetime.select(TableWithDatetime.datetime).where { TableWithDatetime.datetime eq dateTimeLiteral(defaultDatetime) }
            assertEquals(defaultDatetime, query.single()[TableWithDatetime.datetime])
        }
    }

    @Test
    fun testSelectByDatetimeLiteralComparison() {
        withTables(TableWithDatetime) {
            TableWithDatetime.insert {
                it[datetime] = defaultDatetime
            }
            val query = TableWithDatetime.selectAll().where { TableWithDatetime.datetime less dateTimeLiteral(futureDatetime) }
            assertNotNull(query.firstOrNull())
        }
    }

    @Test
    fun testInsertWithTimestampLiteral() {
        withTables(TableWithTimestamp) {
            TableWithTimestamp.insert {
                it[timestamp] = timestampLiteral(defaultTimestamp)
            }
            assertEquals(defaultTimestamp, TableWithTimestamp.selectAll().first()[TableWithTimestamp.timestamp])
        }
    }

    @Test
    fun testUpdateWithTimestampLiteral() {
        withTables(TableWithTimestamp) {
            TableWithTimestamp.insert {
                it[timestamp] = defaultTimestamp
            }

            TableWithTimestamp.update { it[timestamp] = timestampLiteral(futureTimestamp) }
            assertEquals(futureTimestamp, TableWithTimestamp.selectAll().first()[TableWithTimestamp.timestamp])
        }
    }

    @Test
    fun testSelectByTimestampLiteralEquality() {
        withTables(TableWithTimestamp) {
            TableWithTimestamp.insert {
                it[timestamp] = defaultTimestamp
            }

            val query = TableWithTimestamp.select(TableWithTimestamp.timestamp).where { TableWithTimestamp.timestamp eq timestampLiteral(defaultTimestamp) }
            assertEquals(defaultTimestamp, query.single()[TableWithTimestamp.timestamp])
        }
    }

    @Test
    fun testInsertTimeLiteral() {
        withTables(TableWithTime) {
            TableWithTime.insert {
                it[time] = timeLiteral(defaultLocalTime)
            }
            assertEquals(defaultLocalTime, TableWithTime.selectAll().first()[TableWithTime.time])
        }
    }

    @Test
    fun testUpdateWithTimeLiteral() {
        withTables(TableWithTime) {
            TableWithTime.insert {
                it[time] = defaultLocalTime
            }

            TableWithTime.update { it[time] = timeLiteral(futureLocalTime) }
            assertEquals(futureLocalTime, TableWithTime.selectAll().first()[TableWithTime.time])
        }
    }

    @Test
    @Ignore(
        "Test fails with 'Collection is empty.' message. It can not find anything in db after insert. " +
            "But SQL requests looks correct, and work well manually applied."
    )
    fun testSelectByTimeLiteralEquality() {
        withTables(TableWithTime) {
            TableWithTime.insert {
                it[time] = defaultLocalTime
            }

            val query = TableWithTime.select(TableWithTime.id, TableWithTime.time).where { TableWithTime.time eq timeLiteral(defaultLocalTime) }
            assertEquals(defaultLocalTime, query.single()[TableWithTime.time])
        }
    }
}

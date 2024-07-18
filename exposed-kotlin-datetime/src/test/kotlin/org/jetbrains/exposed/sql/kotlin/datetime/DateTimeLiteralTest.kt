package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertNotNull

class DateTimeLiteralTest : DatabaseTestsBase() {
    private val defaultDate = LocalDate(2000, 1, 1)
    private val futureDate = LocalDate(3000, 1, 1)

    object TableWithDate : IntIdTable() {
        val date = date("date")
    }

    private val defaultDatetime = LocalDateTime(2000, 1, 1, 8, 0, 0, 100000000)
    private val futureDatetime = LocalDateTime(3000, 1, 1, 8, 0, 0, 100000000)

    object TableWithDatetime : IntIdTable() {
        val datetime = datetime("datetime")
    }

    private val defaultTimestamp = Instant.parse("2000-01-01T01:00:00.00Z")

    object TableWithTimestamp : IntIdTable() {
        val timestamp = timestamp("timestamp")
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
    fun testSelectByTimestampLiteralEquality() {
        withTables(TableWithTimestamp) {
            TableWithTimestamp.insert {
                it[timestamp] = defaultTimestamp
            }

            val query = TableWithTimestamp.select(TableWithTimestamp.timestamp).where { TableWithTimestamp.timestamp eq timestampLiteral(defaultTimestamp) }
            assertEquals(defaultTimestamp, query.single()[TableWithTimestamp.timestamp])
        }
    }
}

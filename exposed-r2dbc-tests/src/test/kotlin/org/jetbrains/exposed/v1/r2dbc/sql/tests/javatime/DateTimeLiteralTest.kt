package org.jetbrains.exposed.v1.r2dbc.sql.tests.javatime

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.dateLiteral
import org.jetbrains.exposed.v1.javatime.dateTimeLiteral
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.javatime.timestampLiteral
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertNotNull

class DateTimeLiteralTest : R2dbcDatabaseTestsBase() {
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

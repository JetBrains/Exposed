package org.jetbrains.exposed

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.jodatime.dateTimeLiteral
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.junit.Test
import kotlin.test.assertNotNull

class JodaDateTimeLiteralTest : DatabaseTestsBase() {
    private val pattern = "dd-mm-yyyy hh.mm.ss"

    private val defaultDatetime: DateTime = DateTime.parse("01-01-2000 01.00.00", DateTimeFormat.forPattern(pattern))
    private val futureDatetime: DateTime = DateTime.parse("01-01-3000 01.00.00", DateTimeFormat.forPattern(pattern))

    object TableWithDatetime : IntIdTable() {
        val datetime = datetime("datetime")
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
}

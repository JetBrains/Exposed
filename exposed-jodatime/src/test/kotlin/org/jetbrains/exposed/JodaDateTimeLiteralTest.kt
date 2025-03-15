package org.jetbrains.exposed

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.jodatime.dateTimeLiteral
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.joda.time.DateTime
import org.junit.Test
import kotlin.test.assertNotNull

class JodaDateTimeLiteralTest : DatabaseTestsBase() {
    private val defaultDatetime: DateTime = DateTime.parse("2000-01-01T08:00:00.100")
    private val futureDatetime: DateTime = DateTime.parse("3000-01-01T08:00:00.100")

    object TableWithDatetime : IntIdTable() {
        val datetime = datetime("datetime")
    }

    @Test
    fun testSelectByDatetimeLiteralEquality() {
        withTables(TableWithDatetime) { testDb ->
            TableWithDatetime.insert {
                it[datetime] = defaultDatetime
            }

            val expectedDefaultDatetime = if (testDb in TestDB.ALL_MYSQL_MARIADB) {
                // MySQL default precision is 0
                DateTime.parse("2000-01-01T08:00:00")
            } else {
                defaultDatetime
            }

            val query = TableWithDatetime.select(TableWithDatetime.datetime).where { TableWithDatetime.datetime eq dateTimeLiteral(expectedDefaultDatetime) }
            assertEquals(expectedDefaultDatetime, query.single()[TableWithDatetime.datetime])
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

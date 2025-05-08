package org.jetbrains.exposed.v1.r2dbc.sql.tests.jodatime

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.sql.insert
import org.jetbrains.exposed.v1.r2dbc.sql.select
import org.jetbrains.exposed.v1.r2dbc.sql.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.jodatime.dateTimeLiteral
import org.jetbrains.exposed.v1.sql.jodatime.datetime
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.junit.Test
import kotlin.test.assertNotNull

class JodaDateTimeLiteralTest : R2dbcDatabaseTestsBase() {
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

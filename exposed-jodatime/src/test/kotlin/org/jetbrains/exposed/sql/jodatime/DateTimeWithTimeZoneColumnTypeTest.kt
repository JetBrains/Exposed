package org.jetbrains.exposed.sql.jodatime

import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Test

class DateTimeWithTimeZoneColumnTypeTest {

    @Test
    fun testDateTimeTzTypeWithDateTime() {
        val columnType = DateTimeWithTimeZoneColumnType()
        val datetime = DateTime.parse("2020-10-05T04:03:02.000+02:00")
        Assert.assertEquals(columnType.nonNullValueToString(datetime), "'${datetime}'")
    }

    @Test
    fun testDateTimeTzTypeWithString() {
        val columnType = DateTimeWithTimeZoneColumnType()
        val datetime = "2020-10-05T04:03:02.000+02:00"
        Assert.assertEquals(columnType.nonNullValueToString(datetime), "'${datetime}'")
    }
}
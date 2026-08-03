package org.example.examples

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jodatime.CurrentDateTime
import org.jetbrains.exposed.v1.jodatime.date
import org.jetbrains.exposed.v1.jodatime.datetime
import org.jetbrains.exposed.v1.jodatime.time
import org.jetbrains.exposed.v1.jodatime.timestampWithTimeZone
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalTime

class JodaTimeExamples {
    companion object {
        private const val NAME_LENGTH = 50
        private const val SAMPLE_YEAR = 1990
        private const val SAMPLE_MONTH = 1
        private const val SAMPLE_DAY = 1
        private const val STANDUP_HOUR = 9
        private const val STANDUP_MINUTE = 0
    }

    object JodaTimeEvents : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", NAME_LENGTH)
        val startDate = date("start_date")
        val startTime = time("start_time").nullable()
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
        val scheduledAt = timestampWithTimeZone("scheduled_at")

        override val primaryKey = PrimaryKey(id)
    }

    fun insertEvent() {
        JodaTimeEvents.insert {
            it[name] = "Birthday Party"
            it[startDate] = DateTime(SAMPLE_YEAR, SAMPLE_MONTH, SAMPLE_DAY, STANDUP_HOUR, STANDUP_MINUTE)
            it[startTime] = LocalTime(STANDUP_HOUR, STANDUP_MINUTE) // 09:00
            it[createdAt] = DateTime.now()
            it[scheduledAt] = DateTime.now().withZone(DateTimeZone.UTC)
        }
    }
}

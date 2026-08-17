package org.example.examples

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentDateTime
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.javatime.duration
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class JavaTimeExamples {
    companion object {
        private const val NAME_LENGTH = 50
        private const val SAMPLE_YEAR = 1990
        private const val SAMPLE_MONTH = 1
        private const val SAMPLE_DAY = 1
        private const val STANDUP_HOUR = 9
        private const val STANDUP_MINUTE = 0
        private const val SAMPLE_DURATION = 4L
    }

    object JavaTimeEvents : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", NAME_LENGTH)
        val startDate = date("start_date")
        val startTime = time("start_time").nullable()
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
        val lastModified = timestamp("last_modified")
        val scheduledAt = timestampWithTimeZone("scheduled_at")
        val period = duration("period")

        override val primaryKey = PrimaryKey(id)
    }

    fun insertEvent() {
        JavaTimeEvents.insert {
            it[name] = "Birthday Party"
            it[startDate] = LocalDate.of(SAMPLE_YEAR, SAMPLE_MONTH, SAMPLE_DAY)
            it[createdAt] = LocalDateTime.now()
            it[startTime] = LocalTime.of(STANDUP_HOUR, STANDUP_MINUTE) // 09:00
            it[lastModified] = Instant.now()
            it[scheduledAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[period] = Duration.ofHours(SAMPLE_DURATION)
        }
    }
}

package org.example.examples

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.time
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

class KotlinDateTimeExamples {
    companion object {
        private const val NAME_LENGTH = 50
        private const val SAMPLE_YEAR = 1990
        private const val SAMPLE_MONTH = 1
        private const val SAMPLE_DAY = 1
        private const val STANDUP_HOUR = 9
        private const val STANDUP_MINUTE = 0
        private const val SAMPLE_DURATION = 4
    }

    object Events : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", NAME_LENGTH)
        val startDate = date("start_date")
        val startTime = time("start_time").nullable()
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

        @OptIn(ExperimentalTime::class)
        val lastModified = timestamp("last_modified")
        val scheduledAt = timestampWithTimeZone("scheduled_at")
        val period = duration("period")

        override val primaryKey = PrimaryKey(id)
    }

    @OptIn(ExperimentalTime::class)
    fun insertEvent() {
        Events.insert {
            it[name] = "Birthday Party"
            it[startDate] = LocalDate(SAMPLE_YEAR, SAMPLE_MONTH, SAMPLE_DAY)
            it[startTime] = LocalTime(STANDUP_HOUR, STANDUP_MINUTE) // 09:00
            it[createdAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            it[lastModified] = Clock.System.now()
            it[scheduledAt] = Clock.System.now().toJavaInstant().atOffset(ZoneOffset.UTC)
            it[period] = SAMPLE_DURATION.hours
        }
    }
}

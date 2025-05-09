package org.example.examples

import kotlinx.datetime.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.insert
import org.jetbrains.exposed.v1.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.kotlin.datetime.date
import org.jetbrains.exposed.v1.kotlin.datetime.datetime
import org.jetbrains.exposed.v1.kotlin.datetime.time
import org.jetbrains.exposed.v1.kotlin.datetime.timestamp

class DateTimeExamples {
    companion object {
        private const val NAME_LENGTH = 50
        private const val SAMPLE_YEAR = 1990
        private const val SAMPLE_MONTH = 1
        private const val SAMPLE_DAY = 1
        private const val STANDUP_HOUR = 9
        private const val STANDUP_MINUTE = 0
    }

    object Events : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", NAME_LENGTH)
        val startDate = date("start_date")
        val startTime = time("start_time").nullable()
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
        val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

        override val primaryKey = PrimaryKey(id)
    }

    fun dateExample() {
        Events.insert {
            it[name] = "Birthday Party"
            it[startDate] = LocalDate(SAMPLE_YEAR, SAMPLE_MONTH, SAMPLE_DAY)
        }
    }

    fun datetimeExample() {
        Events.insert {
            it[name] = "Team Meeting"
            it[startDate] = LocalDate(SAMPLE_YEAR, SAMPLE_MONTH, SAMPLE_DAY)
            it[createdAt] = Clock.System.now()
                .toLocalDateTime(TimeZone.UTC)
        }
    }

    fun timeExample() {
        Events.insert {
            it[name] = "Daily Standup"
            it[startDate] = LocalDate(SAMPLE_YEAR, SAMPLE_MONTH, SAMPLE_DAY)
            it[startTime] = LocalTime(STANDUP_HOUR, STANDUP_MINUTE) // 09:00
        }
    }

    fun timestampExample() {
        Events.insert {
            it[name] = "Project Deadline"
            it[startDate] = LocalDate(SAMPLE_YEAR, SAMPLE_MONTH, SAMPLE_DAY)
            it[updatedAt] = Clock.System.now()
        }
    }
}

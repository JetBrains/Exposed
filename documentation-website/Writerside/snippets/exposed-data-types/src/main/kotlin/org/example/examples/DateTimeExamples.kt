package org.example.examples

import kotlinx.datetime.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.kotlin.datetime.time
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

class DateTimeExamples {
    object Events : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 50)
        val startDate = date("start_date")
        val startTime = time("start_time")
        val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
        val updatedAt = timestamp("updated_at")

        override val primaryKey = PrimaryKey(id)
    }

    fun dateExample() {
        Events.insert {
            it[name] = "Birthday Party"
            it[startDate] = LocalDate(1990, 1, 1)
        }
    }

    fun datetimeExample() {
        Events.insert {
            it[name] = "Team Meeting"
            it[createdAt] = Clock.System.now()
                .toLocalDateTime(TimeZone.UTC)
        }
    }

    fun timeExample() {
        Events.insert {
            it[name] = "Daily Standup"
            it[startTime] = LocalTime(9, 0) // 09:00
        }
    }

    fun timestampExample() {
        Events.insert {
            it[name] = "Project Deadline"
            it[updatedAt] = Clock.System.now()
        }
    }
}

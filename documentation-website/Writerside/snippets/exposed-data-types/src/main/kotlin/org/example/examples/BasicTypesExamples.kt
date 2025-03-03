package org.example.examples

import org.jetbrains.exposed.sql.*

const val NAME_LENGTH = 50
const val RATING_TOTAL_DIGITS = 5
const val RATING_TOTAL_DIGITS = 2

class BasicTypesExamples {
    object Users : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", NAME_LENGTH)
        val bio = text("bio")
        val age = short("age")
        val rating = decimal("rating", RATING_TOTAL_DIGITS, RATING_TOTAL_DIGITS) // 5 total digits, 2 after decimal point
        val isActive = bool("is_active")

        override val primaryKey = PrimaryKey(id)
    }
}

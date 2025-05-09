package org.example.examples

import org.jetbrains.exposed.v1.*

/*
    Important: The code in this file is referenced by line number in `Numeric-Boolean-String-types.topic`.
    If you add, remove, or modify any lines prior to this one, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class BasicTypesExamples {
    companion object {
        private const val NAME_LENGTH = 50
        private const val RATING_TOTAL_DIGITS = 5
        private const val RATING_DECIMAL_DIGITS = 2
    }

    object Users : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", NAME_LENGTH)
        val bio = text("bio")
        val age = short("age")
        val rating = decimal("rating", RATING_TOTAL_DIGITS, RATING_DECIMAL_DIGITS) // 5 total digits, 2 after decimal point
        val isActive = bool("is_active")

        override val primaryKey = PrimaryKey(id)
    }
}

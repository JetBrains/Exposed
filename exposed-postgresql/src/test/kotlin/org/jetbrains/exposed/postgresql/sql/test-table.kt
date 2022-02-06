package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.dao.id.LongIdTable

object PostgresTestTable : LongIdTable("exposed_test") {
    val name = varchar("full_name", 50)
}



package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow

object PostgresTestTable : LongIdTable("exposed_test") {
    val name = varchar("full_name", 50)
}

data class ExposedPostgresTableData(
    val id: Long,
    val fullName: String,
)

fun ResultRow.toExposedPostgresTableData(): ExposedPostgresTableData {
    return ExposedPostgresTableData(
        id = this[PostgresTestTable.id].value,
        fullName = this[PostgresTestTable.name]
    )
}


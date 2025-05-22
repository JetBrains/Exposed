package org.example.tables

import org.jetbrains.exposed.v1.core.Table

const val MAX_TITLE_LENGTH = 150
const val MAX_REGION_LENGTH = 50
const val REVENUE_PRECISION = 12
const val REVENUE_SCALE = 2

object FilmBoxOfficeTable : Table() {
    val title = varchar("title", MAX_TITLE_LENGTH)
    val region = varchar("region", MAX_REGION_LENGTH)
    val revenue = decimal("revenue", REVENUE_PRECISION, REVENUE_SCALE)
    val month = integer("month")
    val year = integer("year")
}

package org.example.tables

import org.jetbrains.exposed.sql.Table

const val MAX_LABEL_LENGTH = 100
const val MAX_PRODUCT_LENGTH = 50
const val AMOUNT_PRECISION = 8
const val AMOUNT_SCALE = 2

object SalesTable : Table() {
    val label = varchar("label", MAX_LABEL_LENGTH)
    val product = varchar("product", MAX_PRODUCT_LENGTH)
    val amount = decimal("amount", AMOUNT_PRECISION, AMOUNT_SCALE)
    val month = integer("month")
    val year = integer("year")
}

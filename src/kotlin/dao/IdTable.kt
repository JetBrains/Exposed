package kotlin.dao

import kotlin.sql.Table

open class IdTable(name: String = ""): Table(name) {
    val id = integer("id").autoIncrement().primaryKey()
}

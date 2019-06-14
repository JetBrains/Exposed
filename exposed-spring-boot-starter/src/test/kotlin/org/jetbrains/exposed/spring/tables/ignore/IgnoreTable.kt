package org.jetbrains.exposed.spring.tables.ignore

import org.jetbrains.exposed.dao.IntIdTable

object IgnoreTable: IntIdTable("ignore_table") {
    var name = varchar("name", 100)
}
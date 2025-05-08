package org.jetbrains.exposed.v1.spring.tables.ignore

import org.jetbrains.exposed.v1.dao.id.IntIdTable

object IgnoreTable : IntIdTable("ignore_table") {
    var name = varchar("name", 100)
}

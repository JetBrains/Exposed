package org.jetbrains.exposed.v1.spring.boot.r2dbc.tables.ignore

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object IgnoreTable : IntIdTable("ignore_table") {
    var name = varchar("name", 100)
}

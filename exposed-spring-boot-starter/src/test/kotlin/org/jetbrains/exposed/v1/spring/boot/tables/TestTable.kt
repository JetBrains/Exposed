package org.jetbrains.exposed.v1.spring.boot.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object TestTable : IntIdTable("test_table") {
    var name = varchar("name", 100)
}

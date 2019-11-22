package org.jetbrains.exposed.spring.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object TestTable: IntIdTable("test_table") {
    var name = varchar("name", 100)
}
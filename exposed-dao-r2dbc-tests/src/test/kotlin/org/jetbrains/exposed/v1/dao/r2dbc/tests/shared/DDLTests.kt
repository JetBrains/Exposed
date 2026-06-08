package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import kotlin.test.Test

class DDLTests : R2dbcDatabaseTestsBase() {

    object KeyWordTable : IntIdTable(name = "keywords") {
        val bool = bool("bool")
    }

    @Test
    fun testDropTableFlushesCache() {
        class Keyword(id: EntityID<Int>) : IntEntity(id) {
            var bool by KeyWordTable.bool
        }

        val keywordEntityClass = object : IntEntityClass<Keyword>(KeyWordTable, Keyword::class.java) {}

        withTables(KeyWordTable) {
            keywordEntityClass.new { bool = true }
        }
    }
}

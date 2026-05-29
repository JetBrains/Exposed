package org.jetbrains.exposed.dao.r2dbc.tests.shared

import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import kotlin.test.Test

class R2dbcDDLTests : R2dbcDatabaseTestsBase() {

    object KeyWordTable : IntIdTable(name = "keywords") {
        val bool = bool("bool")
    }

    @Test
    fun testDropTableFlushesCache() {
        class Keyword(id: EntityID<Int>) : IntR2dbcEntity(id) {
            var bool by KeyWordTable.bool
        }

        val keywordEntityClass = object : IntR2dbcEntityClass<Keyword>(KeyWordTable, Keyword::class.java) {}

        withTables(KeyWordTable) {
            keywordEntityClass.new { bool = true }
        }
    }
}

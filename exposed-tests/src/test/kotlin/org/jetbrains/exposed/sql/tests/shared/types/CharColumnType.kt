package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class CharColumnType : DatabaseTestsBase() {
    object CharTable : IntIdTable("charTable") {
        val charColumn = char("charColumn")
    }

    @Test
    fun `test char column read and write`() {
        withTables(CharTable) {
            val id = CharTable.insertAndGetId {
                it[charColumn] = 'A'
            }

            val result = CharTable.selectAll().where { CharTable.id eq id }.singleOrNull()

            assertEquals('A', result?.get(CharTable.charColumn))
        }
    }
}

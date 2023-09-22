package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class BooleanColumnTypeTests : DatabaseTestsBase() {
    object BooleanTable : IntIdTable("booleanTable") {
        val boolColumn = bool("boolColumn")
    }

    @Test
    fun `true value`() {
        withTables(BooleanTable) {
            val id = BooleanTable.insertAndGetId {
                it[boolColumn] = true
            }

            val result = BooleanTable.select { BooleanTable.id eq id }.singleOrNull()
            assertEquals(true, result?.get(BooleanTable.boolColumn))
        }
    }

    @Test
    fun `false value`() {
        withTables(BooleanTable) {
            val id = BooleanTable.insertAndGetId {
                it[boolColumn] = false
            }

            val result = BooleanTable.select { BooleanTable.id eq id }.singleOrNull()
            assertEquals(false, result?.get(BooleanTable.boolColumn))
        }
    }

    @Test
    fun `bool in a condition`() {
        withTables(BooleanTable) {
            val idTrue = BooleanTable.insertAndGetId {
                it[boolColumn] = true
            }
            BooleanTable.insertAndGetId {
                it[boolColumn] = false
            }

            val resultTrue = BooleanTable.select { BooleanTable.boolColumn eq true }.singleOrNull()
            assertEquals(idTrue, resultTrue?.get(BooleanTable.id))
        }
    }
}

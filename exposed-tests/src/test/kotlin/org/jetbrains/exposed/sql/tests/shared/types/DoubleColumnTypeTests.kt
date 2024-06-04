package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class DoubleColumnTypeTests : DatabaseTestsBase() {
    object TestTable : IntIdTable("double_table") {
        val amount = double("amount")
    }

    @Test
    fun `test correctly gets data from the DB`() {
        withTables(TestTable) {
            val id = TestTable.insertAndGetId {
                it[amount] = 9.23
            }

            TestTable.selectAll().where { TestTable.id eq id }.singleOrNull()?.let {
                assertEquals(9.23, it[TestTable.amount])
            }
        }
    }
}

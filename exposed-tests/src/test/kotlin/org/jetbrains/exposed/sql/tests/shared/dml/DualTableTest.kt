package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class DualTableTest : DatabaseTestsBase() {

    @Test
    fun testDualTable() {
        withDb {
            val resultColumn = intLiteral(1)
            val result = Table.Dual.slice(resultColumn).selectAll().single()
            assertEquals(1, result[resultColumn])
        }
    }
}

package org.jetbrains.exposed.v1.sql.tests.shared.dml

import org.jetbrains.exposed.v1.sql.Table
import org.jetbrains.exposed.v1.sql.intLiteral
import org.jetbrains.exposed.v1.sql.select
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.junit.Test

class DualTableTest : DatabaseTestsBase() {

    @Test
    fun testDualTable() {
        withDb {
            val resultColumn = intLiteral(1)
            val result = Table.Dual.select(resultColumn).single()
            assertEquals(1, result[resultColumn])
        }
    }
}

package org.jetbrains.exposed.sql.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class DualTableTest : R2dbcDatabaseTestsBase() {
    @Test
    fun testDualTable() {
        withDb {
            val resultColumn = intLiteral(1)
            val result = Table.Dual.select(resultColumn).single()
            assertEquals(1, result[resultColumn])
        }
    }
}

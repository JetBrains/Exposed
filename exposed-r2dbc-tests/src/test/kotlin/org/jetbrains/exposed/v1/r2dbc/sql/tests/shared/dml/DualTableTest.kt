package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
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

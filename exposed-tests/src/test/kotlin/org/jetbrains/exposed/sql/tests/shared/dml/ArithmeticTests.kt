package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class ArithmeticTests : DatabaseTestsBase() {
    @Test
    fun `test operator precedence of minus() plus() div() times()`() {
        withCitiesAndUsers { _, _, userData ->
            val calculatedColumn = ((DMLTestsData.UserData.value - 5) * 2) / 2
            userData
                .slice(DMLTestsData.UserData.value, calculatedColumn)
                .selectAll()
                .forEach {
                    val value = it[DMLTestsData.UserData.value]
                    val actualResult = it[calculatedColumn]
                    val expectedResult = ((value - 5) * 2) / 2
                    assertEquals(expectedResult, actualResult)
                }
        }
    }
}
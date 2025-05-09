package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.DivideOp.Companion.withScale
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.div
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.minus
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.times
import org.jetbrains.exposed.v1.core.decimalLiteral
import org.jetbrains.exposed.v1.r2dbc.sql.select
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.sql.tests.forEach
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ArithmeticTests : R2dbcDatabaseTestsBase() {
    @Test
    fun `test operator precedence of minus() plus() div() times()`() {
        withCitiesAndUsers(exclude = listOf(TestDB.H2_V2_ORACLE)) { _, _, userData ->
            val calculatedColumn = ((DMLTestsData.UserData.value - 5) * 2) / 2
            userData
                .select(DMLTestsData.UserData.value, calculatedColumn)
                .forEach {
                    val value = it[DMLTestsData.UserData.value]
                    val actualResult = it[calculatedColumn]
                    val expectedResult = ((value - 5) * 2) / 2
                    assertEquals(expectedResult, actualResult)
                }
        }
    }

    @Test
    fun `test big decimal division with scale and without`() {
        withCitiesAndUsers { cities, _, _ ->
            val ten = decimalLiteral(BigDecimal(10))
            val three = decimalLiteral(BigDecimal(3))

            val divTenToThreeWithoutScale = Expression.build { ten / three }
            val resultWithoutScale = cities.select(divTenToThreeWithoutScale).limit(1).single()[divTenToThreeWithoutScale]
            assertEquals(BigDecimal(3), resultWithoutScale)

            val divTenToThreeWithScale = divTenToThreeWithoutScale.withScale(2)
            val resultWithScale = cities.select(divTenToThreeWithScale).limit(1).single()[divTenToThreeWithScale]
            assertEquals(BigDecimal.valueOf(3.33), resultWithScale)
        }
    }
}

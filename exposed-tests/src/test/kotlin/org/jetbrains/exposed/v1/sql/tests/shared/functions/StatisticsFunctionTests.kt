package org.jetbrains.exposed.v1.sql.tests.shared.functions

import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.Function
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.junit.Test
import java.math.*

class StatisticsFunctionTests : DatabaseTestsBase() {
    @Test
    fun testStdDevPop() {
        withSampleTable {
            val expectedStdDevPop = calculateStandardDeviation(isPopulation = true)
            assertExpressionEqual(expectedStdDevPop, SampleTestTable.number.stdDevPop(scale))
        }
    }

    @Test
    fun testStdDevSamp() {
        withSampleTable {
            val expectedStdDevSamp = calculateStandardDeviation(isPopulation = false)
            assertExpressionEqual(expectedStdDevSamp, SampleTestTable.number.stdDevSamp(scale))
        }
    }

    @Test
    fun testVarPop() {
        withSampleTable {
            val expectedVarPop = calculateVariance(isPopulation = true)
            assertExpressionEqual(expectedVarPop, SampleTestTable.number.varPop(scale))
        }
    }

    @Test
    fun testVarSamp() {
        withSampleTable {
            val expectedVarSamp = calculateVariance(isPopulation = false)
            assertExpressionEqual(expectedVarSamp, SampleTestTable.number.varSamp(scale))
        }
    }

    private object SampleTestTable : Table("sample_table") {
        val number = integer("number").nullable()
    }

    private val data: List<Int?> = listOf(4, null, 5, null, 6)
    private val scale = 4

    private fun withSampleTable(excludeDB: List<TestDB> = emptyList(), body: JdbcTransaction.(TestDB) -> Unit) {
        // SQLite does not have any built-in statistics-specific aggregate functions
        withTables(excludeSettings = excludeDB + TestDB.SQLITE, SampleTestTable) {
            SampleTestTable.batchInsert(data) { num ->
                this[SampleTestTable.number] = num
            }
            body(it)
        }
    }

    private fun JdbcTransaction.assertExpressionEqual(expected: BigDecimal, expression: Function<BigDecimal?>) {
        val result = SampleTestTable.select(expression).first()[expression]
        assertEquals(expected, result?.setScale(expected.scale(), RoundingMode.HALF_EVEN))
    }

    private fun calculateStandardDeviation(isPopulation: Boolean): BigDecimal {
        return calculateVariance(isPopulation).simpleSqrt()
    }

    fun BigDecimal.simpleSqrt(): BigDecimal {
        if (this < BigDecimal.ZERO) throw ArithmeticException("Square root of negative number")
        if (this == BigDecimal.ZERO) return BigDecimal.ZERO

        val two = BigDecimal(2)
        val epsilon = BigDecimal(0.1).pow(scale)

        var low = BigDecimal.ZERO
        var high = max(BigDecimal.ONE)
        var result = (low + high).divide(two)

        while (true) {
            val square = result.multiply(result)
            val diff = square.subtract(this).abs()
            if (diff < epsilon) {
                break
            }

            if (result.multiply(result) < this) {
                low = result
            } else {
                high = result
            }
            result = (low + high).divide(two)
        }

        result = result.round(MathContext(scale, RoundingMode.HALF_EVEN))
        result = result.setScale(scale)
        return result
    }

    private fun calculateVariance(isPopulation: Boolean): BigDecimal {
        val nonNullData = data.filterNotNull()
        val mean = nonNullData.average()
        val squaredSum = nonNullData.sumOf { n ->
            val deviation = n - mean
            deviation * deviation
        }
        val size = if (isPopulation) nonNullData.size else nonNullData.lastIndex
        return (squaredSum / size).toBigDecimal(MathContext(scale))
    }
}

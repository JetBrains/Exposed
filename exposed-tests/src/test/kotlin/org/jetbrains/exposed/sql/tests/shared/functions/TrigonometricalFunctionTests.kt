package org.jetbrains.exposed.sql.tests.shared.functions

import org.jetbrains.exposed.sql.decimalLiteral
import org.jetbrains.exposed.sql.doubleLiteral
import org.jetbrains.exposed.sql.functions.math.*
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.tests.TestDB
import org.junit.Test
import java.math.BigDecimal

class TrigonometricalFunctionTests : FunctionsTestBase() {

    @Test
    fun testACosFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("1.5707963267948966"), ACosFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("0"), ACosFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("1.318116071652818"), ACosFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("1.318116071652818"), ACosFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testASinFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0"), ASinFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("1.5707963267948966"), ASinFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.25268025514207865"), ASinFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.25268025514207865"), ASinFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testATanFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0"), ATanFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("0.7853981633974483"), ATanFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.24497866312686414"), ATanFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.24497866312686414"), ATanFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testCosFunction() {
        withTable { testDb ->
            assertExpressionEqual(BigDecimal("1"), CosFunction(intLiteral(0)))
            if (testDb != TestDB.SQLSERVER)
                assertExpressionEqual(BigDecimal("0.5403023058681398"), CosFunction(intLiteral(1)))
            else
                assertExpressionEqual(BigDecimal("0.5403023058681397"), CosFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.9663899781345132"), CosFunction(doubleLiteral(0.26)))
            assertExpressionEqual(BigDecimal("0.9663899781345132"), CosFunction(decimalLiteral(BigDecimal("0.26"))))
        }
    }

    @Test
    fun testCotFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0.6420926159343306"), CotFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("3.91631736464594"), CotFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("3.91631736464594"), CotFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testDegreesFunction() {
        withTable { testDb ->
            assertExpressionEqual(BigDecimal("0"), DegreesFunction(intLiteral(0)))
            if (testDb != TestDB.SQLSERVER) {
                assertExpressionEqual(BigDecimal("57.29577951308232"), DegreesFunction(intLiteral(1)))
                assertExpressionEqual(BigDecimal("14.3239448782705"), DegreesFunction(doubleLiteral(0.25)))
                assertExpressionEqual(BigDecimal("14.3239448782705"), DegreesFunction(decimalLiteral(BigDecimal("0.25"))))
            } else {
                assertExpressionEqual(BigDecimal("57"), DegreesFunction(intLiteral(1)))
                assertExpressionEqual(BigDecimal("14.3239448782706"), DegreesFunction(doubleLiteral(0.25)))
                assertExpressionEqual(BigDecimal("14.3239448782706"), DegreesFunction(decimalLiteral(BigDecimal("0.25"))))
            }
        }
    }

    @Test
    fun testPiFunction() {
        withTable { testDb ->
            when (testDb) {
                TestDB.MYSQL, TestDB.MARIADB -> assertExpressionEqual(BigDecimal("3.141593"), PiFunction)
                else -> assertExpressionEqual(BigDecimal("3.141592653589793"), PiFunction)
            }
        }
    }

    @Test
    fun testRadiansFunction() {
        withTable { testDb ->
            assertExpressionEqual(BigDecimal("0"), RadiansFunction(intLiteral(0)))
            if (testDb != TestDB.SQLSERVER)
                assertExpressionEqual(BigDecimal("3.141592653589793"), RadiansFunction(intLiteral(180)))
            else
                assertExpressionEqual(BigDecimal("3"), RadiansFunction(intLiteral(180)))
            assertExpressionEqual(BigDecimal("0.004363323129985824"), RadiansFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.004363323129985824"), RadiansFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testSinFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0"), SinFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("0.8414709848078965"), SinFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.24740395925452294"), SinFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.24740395925452294"), SinFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testTanFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0"), TanFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("1.5574077246549023"), TanFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.25534192122103627"), TanFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.25534192122103627"), TanFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }
}

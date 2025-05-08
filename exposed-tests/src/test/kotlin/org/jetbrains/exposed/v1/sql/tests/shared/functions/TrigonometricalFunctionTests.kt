package org.jetbrains.exposed.v1.sql.tests.shared.functions

import org.jetbrains.exposed.v1.sql.decimalLiteral
import org.jetbrains.exposed.v1.sql.doubleLiteral
import org.jetbrains.exposed.v1.sql.functions.math.*
import org.jetbrains.exposed.v1.sql.intLiteral
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.junit.Test
import java.math.BigDecimal

class TrigonometricalFunctionTests : FunctionsTestBase() {

    @Test
    fun testACosFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("1.5707963"), ACosFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("0"), ACosFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("1.3181161"), ACosFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("1.3181161"), ACosFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testASinFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0"), ASinFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("1.570796327"), ASinFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.252680255"), ASinFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.252680255"), ASinFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testATanFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0"), ATanFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("0.785398163"), ATanFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.244978663"), ATanFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.244978663"), ATanFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testCosFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("1"), CosFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("0.5403023"), CosFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.96638998"), CosFunction(doubleLiteral(0.26)))
            assertExpressionEqual(BigDecimal("0.96638998"), CosFunction(decimalLiteral(BigDecimal("0.26"))))
        }
    }

    @Test
    fun testCotFunction() {
        withTable(excludeDB = TestDB.ORACLE) {
            assertExpressionEqual(BigDecimal("0.642092616"), CotFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("3.916317365"), CotFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("3.916317365"), CotFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testDegreesFunction() {
        withTable(excludeDB = TestDB.ORACLE) { testDb ->
            assertExpressionEqual(BigDecimal("0"), DegreesFunction(intLiteral(0)))
            if (testDb != TestDB.SQLSERVER) {
                assertExpressionEqual(BigDecimal("57.29577951308232"), DegreesFunction(intLiteral(1)))
                assertExpressionEqual(BigDecimal("14.32394487827058"), DegreesFunction(doubleLiteral(0.25)))
                assertExpressionEqual(BigDecimal("14.32394487827058"), DegreesFunction(decimalLiteral(BigDecimal("0.25"))))
            } else {
                assertExpressionEqual(BigDecimal("57"), DegreesFunction(intLiteral(1)))
                assertExpressionEqual(BigDecimal("14.3239448782706"), DegreesFunction(doubleLiteral(0.25)))
                assertExpressionEqual(BigDecimal("14.3239448782706"), DegreesFunction(decimalLiteral(BigDecimal("0.25"))))
            }
        }
    }

    @Test
    fun testPiFunction() {
        withTable(excludeDB = TestDB.ORACLE) { testDb ->
            when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> assertExpressionEqual(BigDecimal("3.141593"), PiFunction)
                else -> assertExpressionEqual(BigDecimal("3.141592653589793"), PiFunction)
            }
        }
    }

    @Test
    fun testRadiansFunction() {
        withTable(excludeDB = TestDB.ORACLE) { testDb ->
            assertExpressionEqual(BigDecimal("0"), RadiansFunction(intLiteral(0)))
            if (testDb != TestDB.SQLSERVER) {
                assertExpressionEqual(BigDecimal("3.141592653589793"), RadiansFunction(intLiteral(180)))
            } else {
                assertExpressionEqual(BigDecimal("3"), RadiansFunction(intLiteral(180)))
            }
            assertExpressionEqual(BigDecimal("0.004363323129985824"), RadiansFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.004363323129985824"), RadiansFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testSinFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0"), SinFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("0.841470985"), SinFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.2474039593"), SinFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.2474039593"), SinFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }

    @Test
    fun testTanFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("0"), TanFunction(intLiteral(0)))
            assertExpressionEqual(BigDecimal("1.557407725"), TanFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("0.2553419212"), TanFunction(doubleLiteral(0.25)))
            assertExpressionEqual(BigDecimal("0.2553419212"), TanFunction(decimalLiteral(BigDecimal("0.25"))))
        }
    }
}

package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.functions

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.functions.math.*
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MathFunctionTests : FunctionsTestBase() {

    @Test
    fun testAbsFunction() {
        withTable {
            assertExpressionEqual(0, AbsFunction(intLiteral(0)))
            assertExpressionEqual(100, AbsFunction(intLiteral(100)))
            assertExpressionEqual(100, AbsFunction(intLiteral(-100)))
            assertExpressionEqual(100.0, AbsFunction(doubleLiteral(100.0)))
            assertExpressionEqual(100.0, AbsFunction(doubleLiteral(-100.0)))
        }
    }

    @Test
    fun testSignFunction() {
        withTable {
            assertExpressionEqual(0, SignFunction(intLiteral(0)))
            assertExpressionEqual(1, SignFunction(intLiteral(100)))
            assertExpressionEqual(-1, SignFunction(intLiteral(-100)))
            assertExpressionEqual(1, SignFunction(doubleLiteral(100.0)))
            assertExpressionEqual(-1, SignFunction(doubleLiteral(-100.0)))
        }
    }

    @Test
    fun testFloorFunction() {
        withTable {
            assertExpressionEqual(100, FloorFunction(intLiteral(100)))
            assertExpressionEqual(-100, FloorFunction(intLiteral(-100)))
            assertExpressionEqual(100, FloorFunction(doubleLiteral(100.0)))
            assertExpressionEqual(100, FloorFunction(doubleLiteral(100.30)))
            assertExpressionEqual(100, FloorFunction(doubleLiteral(100.70)))
            assertExpressionEqual(-100, FloorFunction(doubleLiteral(-100.0)))
            assertExpressionEqual(-101, FloorFunction(doubleLiteral(-100.30)))
            assertExpressionEqual(-101, FloorFunction(doubleLiteral(-100.70)))
        }
    }

    @Test
    fun testCeilFunction() {
        withTable {
            assertExpressionEqual(100, CeilingFunction(intLiteral(100)))
            assertExpressionEqual(-100, CeilingFunction(intLiteral(-100)))
            assertExpressionEqual(100, CeilingFunction(doubleLiteral(100.0)))
            assertExpressionEqual(101, CeilingFunction(doubleLiteral(100.30)))
            assertExpressionEqual(101, CeilingFunction(doubleLiteral(100.70)))
            assertExpressionEqual(-100, CeilingFunction(doubleLiteral(-100.0)))
            assertExpressionEqual(-100, CeilingFunction(doubleLiteral(-100.30)))
            assertExpressionEqual(-100, CeilingFunction(doubleLiteral(-100.70)))
        }
    }

    @Test
    fun testPowerFunction() {
        withTable { testDb ->
            assertExpressionEqual(BigDecimal(100), PowerFunction(intLiteral(10), intLiteral(2)))
            assertExpressionEqual(BigDecimal(100), PowerFunction(intLiteral(10), doubleLiteral(2.0)))
            if (testDb != TestDB.SQLSERVER) {
                assertExpressionEqual(BigDecimal("102.01"), PowerFunction(doubleLiteral(10.1), intLiteral(2)))
                assertExpressionEqual(
                    BigDecimal("102.01"),
                    PowerFunction(decimalLiteral(BigDecimal("10.1")), intLiteral(2))
                )
                assertExpressionEqual(BigDecimal("102.01"), PowerFunction(doubleLiteral(10.1), doubleLiteral(2.0)))
                assertExpressionEqual(
                    BigDecimal("102.01"),
                    PowerFunction(decimalLiteral(BigDecimal("10.1")), doubleLiteral(2.0))
                )
                assertExpressionEqual(
                    BigDecimal("324.1928515714"),
                    PowerFunction(doubleLiteral(10.1), doubleLiteral(2.5))
                )
                assertExpressionEqual(
                    BigDecimal("324.1928515714"),
                    PowerFunction(decimalLiteral(BigDecimal("10.1")), doubleLiteral(2.5))
                )
            } else {
                assertExpressionEqual(BigDecimal(102), PowerFunction(doubleLiteral(10.1), intLiteral(2)))
                assertExpressionEqual(BigDecimal(102), PowerFunction(decimalLiteral(BigDecimal("10.1")), intLiteral(2)))
                assertExpressionEqual(BigDecimal(102), PowerFunction(doubleLiteral(10.1), doubleLiteral(2.0)))
                assertExpressionEqual(
                    BigDecimal(102),
                    PowerFunction(decimalLiteral(BigDecimal("10.1")), doubleLiteral(2.0))
                )
                assertExpressionEqual(BigDecimal("324.2"), PowerFunction(doubleLiteral(10.1), doubleLiteral(2.5)))
                assertExpressionEqual(
                    BigDecimal("324.2"),
                    PowerFunction(decimalLiteral(BigDecimal("10.1")), doubleLiteral(2.5))
                )
            }
        }
    }

    @Test
    fun testRoundFunction() {
        withTable { testDb ->
            assertExpressionEqual(BigDecimal(10), RoundFunction(intLiteral(10), 0))
            assertExpressionEqual(BigDecimal("10.00"), RoundFunction(intLiteral(10), 2))
            assertExpressionEqual(BigDecimal(10), RoundFunction(doubleLiteral(10.455), 0))
            assertExpressionEqual(BigDecimal(11), RoundFunction(doubleLiteral(10.555), 0))

            assertExpressionEqual(BigDecimal("10.56"), RoundFunction(doubleLiteral(10.555), 2))
        }
    }

    @Test
    fun testSqrtFunction() {
        withTable { testDb ->
            assertExpressionEqual(BigDecimal(10), SqrtFunction(intLiteral(100)))
            assertExpressionEqual(BigDecimal(10), SqrtFunction(doubleLiteral(100.0)))
            assertExpressionEqual(BigDecimal("11.2"), SqrtFunction(doubleLiteral(125.44)))
            assertExpressionEqual(BigDecimal(10), SqrtFunction(decimalLiteral(BigDecimal(100))))
            assertExpressionEqual(BigDecimal("11.2"), SqrtFunction(decimalLiteral(BigDecimal("125.44"))))

            when (testDb) {
                in (TestDB.ALL_MYSQL_MARIADB) -> {
                    assertExpressionEqual(null, SqrtFunction(intLiteral(-100)))
                }
                in (TestDB.ALL_H2_V2) -> {
                    expectException<IllegalStateException> {
                        assertExpressionEqual(null, SqrtFunction(intLiteral(-100)))
                    }
                }
                else -> {
                    expectException<R2dbcException> {
                        assertExpressionEqual(null, SqrtFunction(intLiteral(-100)))
                    }
                }
            }
        }
    }

    @Test
    fun testExpFunction() {
        withTable {
            assertExpressionEqual(BigDecimal("2.7182818284590"), ExpFunction(intLiteral(1)))
            assertExpressionEqual(BigDecimal("12.182493960703473"), ExpFunction(doubleLiteral(2.5)))
            assertExpressionEqual(BigDecimal("12.182493960703473"), ExpFunction(decimalLiteral(BigDecimal("2.5"))))
        }
    }

    @Test
    fun testColumnReferenceInDefaultExpression() {
        val foo = object : IntIdTable("foo") {
            val integer = integer("integer")
            val double = double("double")
            val long = long("long")
            val defaultInt = integer("defaultInt").defaultExpression(AbsFunction(integer))
            val defaultInt2 = integer("defaultInt2").defaultExpression(defaultInt.div(100))
            val defaultDecimal = decimal("defaultDecimal", 14, 12).nullable().defaultExpression(ExpFunction(defaultInt2))
            val defaultLong = long("defaultLong").nullable().defaultExpression(FloorFunction(double))
            val defaultDecimal2 = decimal("defaultDecimal2", 3, 0).nullable().defaultExpression(PowerFunction(long, intLiteral(2)))
            val defaultDecimal3 = decimal("defaultDecimal3", 3, 0).nullable().defaultExpression(RoundFunction(double, 0))
            val defaultInt3 = integer("defaultInt3").nullable().defaultExpression(SignFunction(integer))
            val defaultDecimal4 = decimal("defaultDecimal4", 3, 0).nullable().defaultExpression(SqrtFunction(defaultDecimal2))
            val defaultInt4 = integer("defaultInt4").defaultExpression(defaultInt plus intLiteral(1))
            val defaultBoolean = bool("defaultBoolean").defaultExpression(defaultDecimal.isNull())
        }

        // MySQL and MariaDB are the only supported databases that allow referencing another column in a default expression
        // MySQL 5 does not support functions in default values.
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_MYSQL_MARIADB + TestDB.MYSQL_V5, foo) {
            val id = foo.insertAndGetId {
                it[foo.integer] = -100
                it[foo.double] = 100.70
                it[foo.long] = 10L
            }
            val result = foo.selectAll().where { foo.id eq id }.single()

            assertEquals(100, result[foo.defaultInt])
            assertEquals(1, result[foo.defaultInt2])
            assertEquals(BigDecimal("2.718281828459"), result[foo.defaultDecimal])
            assertEquals(100, result[foo.defaultLong])
            assertEquals(BigDecimal(100), result[foo.defaultDecimal2])
            assertEquals(BigDecimal(101), result[foo.defaultDecimal3])
            assertEquals(-1, result[foo.defaultInt3])
            assertEquals(BigDecimal(10), result[foo.defaultDecimal4])
            assertEquals(101, result[foo.defaultInt4])
            assertFalse(result[foo.defaultBoolean])
        }
    }

    @Test
    fun testConstantFunctionsInDefaultExpression() {
        val tester = object : IntIdTable("tester") {
            val absolute = integer("absolute").defaultExpression(AbsFunction(intLiteral(-100)))
            val length = integer("length").nullable().defaultExpression(stringLiteral("TEST").charLength())
            val conditional = integer("conditional").defaultExpression(
                Case().When(Op.TRUE, intLiteral(1) times intLiteral(10)).Else(intLiteral(0))
            )
        }

        // MySQL 5 does not support functions in default values.
        withTables(excludeSettings = listOf(TestDB.MYSQL_V5), tester) {
            val id = tester.insertAndGetId { }
            val result = tester.selectAll().where { tester.id eq id }.single()

            assertEquals(100, result[tester.absolute])
            assertEquals(4, result[tester.length])
            assertEquals(10, result[tester.conditional])
        }
    }

    @Test
    fun testArithmeticExpressionsInDefaultExpression() {
        val tester = object : IntIdTable("tester") {
            // CustomOperator example (extends Function)
            val addition = integer("addition").defaultExpression(intLiteral(2) plus intLiteral(3))

            // ComparisonOp example (extends Op)
            val comparison = bool("comparison").defaultExpression(intLiteral(2) less intLiteral(3))

            // Mixed Function example
            val extraLength = integer("extra_length").nullable().defaultExpression(
                stringLiteral("TEST").charLength() plus intLiteral(1)
            )
        }

        // SQL Server & Oracle only allow 3 column defaults: constants/literals, scalar/deterministic functions, system functions;
        // MySQL 5 does not support functions in default values;
        withTables(excludeSettings = TestDB.ALL_ORACLE_LIKE + TestDB.SQLSERVER + TestDB.MYSQL_V5, tester) {
            val id = tester.insertAndGetId { }
            val result = tester.selectAll().where { tester.id eq id }.single()

            assertEquals(5, result[tester.addition])
            assertTrue(result[tester.comparison])
            assertEquals(5, result[tester.extraLength])
        }
    }
}

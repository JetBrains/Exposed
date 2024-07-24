package org.jetbrains.exposed.sql.tests.shared.functions

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.decimalLiteral
import org.jetbrains.exposed.sql.doubleLiteral
import org.jetbrains.exposed.sql.functions.math.*
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import java.math.BigDecimal
import java.sql.SQLException

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
            if (testDb == TestDB.SQLITE) {
                // Change this when this issue is resolved https://www.sqlite.org/forum/forumpost/2801f84063
                assertExpressionEqual(BigDecimal("10.55"), RoundFunction(doubleLiteral(10.555), 2))
            } else {
                assertExpressionEqual(BigDecimal("10.56"), RoundFunction(doubleLiteral(10.555), 2))
            }
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
                in (TestDB.ALL_MYSQL_MARIADB + TestDB.SQLITE) -> {
                    assertExpressionEqual(null, SqrtFunction(intLiteral(-100)))
                }
                else -> {
                    expectException<SQLException> {
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
            assertEquals(BigDecimal("2.718281828459"), result[foo.defaultDecimal])
            assertEquals(100, result[foo.defaultLong])
            assertEquals(BigDecimal(100), result[foo.defaultDecimal2])
            assertEquals(BigDecimal(101), result[foo.defaultDecimal3])
            assertEquals(-1, result[foo.defaultInt3])
            assertEquals(BigDecimal(10), result[foo.defaultDecimal4])
        }
    }
}

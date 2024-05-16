package org.jetbrains.exposed.sql.tests.shared.types

import junit.framework.TestCase.assertNull
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLNGDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test

class UserDefinedTypesTests : DatabaseTestsBase() {
    enum class FooBar {
        FOO,
        BAR
    }

    val fooBarEnumType = pgEnumerationType<FooBar>("test_postgres_enumeration_foobar2")

    @Test
    fun testPostgresEnumeration() {
        val tester = object : IntIdTable() {
            val value = enumeration("value", fooBarEnumType)
        }

        withTables(excludeSettings = TestDB.entries - TestDB.POSTGRESQL, tester) {
            val id = tester.insertAndGetId {
                it[value] = FooBar.FOO
            }
            assertEquals(FooBar.FOO, tester.selectAll().first()[tester.value])

            assertEquals(id, tester.selectAll().where { tester.value eq FooBar.FOO }.first()[tester.id])

            assertEquals(id, tester.selectAll().where { tester.value eq pgEnumerationLiteral(FooBar.FOO, fooBarEnumType) }.first()[tester.id])
        }
    }

    @Test
    fun testPostgresEnumerationInMultipleTables() {
        val tester1 = object : IntIdTable() {
            val value = enumeration("value", fooBarEnumType)
        }
        val tester2 = object : IntIdTable() {
            val value = enumeration("value", fooBarEnumType)
        }

        withTables(excludeSettings = TestDB.entries - TestDB.POSTGRESQL, tester1, tester2) {
            addLogger(StdOutSqlLogger)

            tester1.insertAndGetId {
                it[tester1.value] = FooBar.FOO
            }

            tester2.insert(tester1.select(tester1.value), columns = listOf(tester2.value))
            assertEquals(FooBar.FOO, tester2.selectAll().first()[tester2.value])
        }
    }

    class Float8ColumnType : DoubleColumnType() {
        override fun sqlType(): String = "float8"

        override fun nonNullValueToString(value: Double): String {
            if (currentDialect is PostgreSQLDialect || currentDialect is PostgreSQLNGDialect) {
                return "$value::${sqlType()}"
            }
            return super.nonNullValueToString(value)
        }
    }

    private fun float8Literal(value: Double): LiteralOp<Double> = LiteralOp(Float8ColumnType(), value)

    @Test
    fun testPostgresRange() {
        val rangeType = PGRangeColumnType("test_postgres_range_float8", delegate = Float8ColumnType(), subtypeDiff = "float8mi")

        val tester = object : IntIdTable("test_range") {
            val range = range("range", rangeType)
        }

        withTables(excludeSettings = TestDB.entries - TestDB.POSTGRESQL, tester) {
            addLogger(StdOutSqlLogger)

            val id = tester.insertAndGetId {
                it[range] = 1.0 to 2.0
            }

            val id1 = tester.select(tester.id)
                .where { tester.range contains float8Literal(1.5) }
                .first()[tester.id]
            assertEquals(id, id1)

            val id2 = tester.select(tester.id).where { tester.range contains float8Literal(3.0) }.firstOrNull()
            assertNull(id2)

            val id3 = tester
                .select(tester.id)
                .where { tester.range overlap pgRangeLiteral(1.5 to 2.5, rangeType) }
                .first()[tester.id]
            assertEquals(id, id3)

            val range = tester.selectAll().first()[tester.range]
            assertEquals(1.0 to 2.0, range)
        }
    }

    @Test
    fun testOracleArray() {
        val arrayType = OracleArrayType(
            "CUSTOM1",
            length = 10,
            delegate = VarCharColumnType(20)
        )

        val tester = object : IntIdTable("test_array_1") {
            val array = array("array", arrayType)
        }

        withTables(TestDB.entries - TestDB.ORACLE, tester) {
            addLogger(StdOutSqlLogger)

            tester.insertAndGetId {
                it[array] = listOf("t1", "t2", "t3")
            }

            val queriedList = tester.selectAll().map { it[tester.array] }
            assertEquals(listOf("t1", "t2", "t3"), queriedList)
        }
    }
}

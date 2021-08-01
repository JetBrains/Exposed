package org.jetbrains.exposed.sql.tests.shared.functions

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import java.math.BigDecimal
import java.math.MathContext

abstract class FunctionsTestBase : DatabaseTestsBase() {

    private object FakeTestTable : IntIdTable("fakeTable")

    protected fun withTable(body: Transaction.(TestDB) -> Unit) {
        withTables(FakeTestTable) {
            FakeTestTable.insert { }
            body(it)
        }
    }

    protected fun <T> Transaction.assertExpressionEqual(expected: T, expression: Function<T>) {
        val result = FakeTestTable.slice(expression).selectAll().first()[expression]
        if (expected is BigDecimal && result is BigDecimal)
            assertEquals(expected, result.setScale(expected.scale(), MathContext.DECIMAL64.roundingMode))
        else
            assertEquals(expected, result)
    }
}

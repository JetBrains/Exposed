package org.jetbrains.exposed.sql.tests.shared.functions

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import java.math.BigDecimal
import java.math.RoundingMode

abstract class FunctionsTestBase : DatabaseTestsBase() {

    private object FakeTestTable : IntIdTable("fakeTable")

    protected fun withTable(excludeDB: TestDB? = null, body: JdbcTransaction.(TestDB) -> Unit) {
        withTables(excludeSettings = listOfNotNull(excludeDB), FakeTestTable) {
            FakeTestTable.insert { }
            body(it)
        }
    }

    protected fun <T> JdbcTransaction.assertExpressionEqual(expected: T, expression: Function<T>) {
        val result = FakeTestTable.select(expression).first()[expression]
        if (expected is BigDecimal && result is BigDecimal) {
            assertEquals(expected, result.setScale(expected.scale(), RoundingMode.HALF_UP))
        } else {
            assertEquals(expected, result)
        }
    }
}

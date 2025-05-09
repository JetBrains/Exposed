package org.jetbrains.exposed.v1.tests.shared.functions
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEquals
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

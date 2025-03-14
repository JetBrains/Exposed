package org.jetbrains.exposed.r2dbc.sql.tests.shared.functions

import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import java.math.BigDecimal

abstract class FunctionsTestBase : R2dbcDatabaseTestsBase() {

    private object FakeTestTable : IntIdTable("fakeTable")

    protected fun withTable(excludeDB: TestDB? = null, body: suspend R2dbcTransaction.(TestDB) -> Unit) {
        withTables(excludeSettings = listOfNotNull(excludeDB), FakeTestTable) {
            FakeTestTable.insert { }
            body(it)
        }
    }

    protected suspend fun <T> R2dbcTransaction.assertExpressionEqual(expected: T, expression: Function<T>) {
        val result = FakeTestTable.select(expression).first()[expression]
        if (expected is BigDecimal && result is BigDecimal) {
            assertEquals(expected, result.setScale(expected.scale(), java.math.RoundingMode.HALF_UP))
        } else {
            assertEquals(expected, result)
        }
    }
}

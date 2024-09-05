package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ComplexExpression
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import kotlin.test.Test

class StatementTest : DatabaseTestsBase() {
    @Test
    fun `query with double question`() {
        val table = object : IntIdTable("test_mod_on_pk") {
            val otherColumn = short("other")
        }

        open class SubQueryComplex<T>(
            private val operator: String,
            private val expr: Expression<T>,
            private val expr2: Expression<T>,
        ) : Op<Boolean>(), ComplexExpression {
            override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
                +expr
                +" "
                +operator
                +" ("
                +expr2
                +")"
            }
        }

        withTables(table) {
            val actual = table
                .select(table.otherColumn)
                .where {
                    SubQueryComplex(
                        "??",
                        table.id,
                        table.id
                    )
                }
                .prepareSQL(this, false)
            assertEquals(
                "SELECT TEST_MOD_ON_PK.OTHER FROM TEST_MOD_ON_PK WHERE TEST_MOD_ON_PK.ID ?? (TEST_MOD_ON_PK.ID)".lowercase(),
                actual.lowercase()
            )
        }
    }
}

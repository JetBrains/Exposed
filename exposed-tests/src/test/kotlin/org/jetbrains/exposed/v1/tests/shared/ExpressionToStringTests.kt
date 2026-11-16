package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private object RenderTesters : Table("render_testers") {
    val id = integer("id")
    val name = varchar("name", 42)
    val order = varchar("order", 42)
}

/**
 * Pure rendering tests: the SQL representation of an expression must be obtainable without a
 * database connection and without an open transaction, using ANSI-style fallback identity
 * rendering when no transaction is in context.
 */
class ExpressionToStringTests {
    @Test
    fun testColumnEqualityToStringWithoutConnection() {
        assertEquals("render_testers.id = 5", (RenderTesters.id eq 5).toString())
    }

    @Test
    fun testKeywordColumnIsQuotedWithoutConnection() {
        assertEquals("""render_testers."order" = 'a'""", (RenderTesters.order eq "a").toString())
    }

    @Test
    fun testIsNullToStringWithoutConnection() {
        assertEquals("""render_testers."name" IS NULL""", RenderTesters.name.isNull().toString())
    }

    @Test
    fun testAggregateFunctionToStringWithoutConnection() {
        assertEquals("SUM(render_testers.id)", RenderTesters.id.sum().toString())
    }

    @Test
    fun testStringFunctionToStringWithoutConnection() {
        assertEquals("""LOWER(render_testers."name")""", RenderTesters.name.lowerCase().toString())
    }

    @Test
    fun testDialectAwareFunctionToStringWithoutConnection() {
        assertEquals(
            """CONCAT(render_testers."name", render_testers."order")""",
            concat(RenderTesters.name, RenderTesters.order).toString()
        )
        assertEquals(
            """CHAR_LENGTH(render_testers."name")""",
            RenderTesters.name.charLength().toString()
        )
    }

    @Test
    fun testCaseExpressionToStringWithoutConnection() {
        val case = Case().When(RenderTesters.id eq 1, intLiteral(1)).Else(intLiteral(0))
        assertEquals(
            "CASE WHEN render_testers.id = 1 THEN 1 ELSE 0 END",
            case.toString()
        )
    }
}

/** Verifies rendering with a connected database but no open transaction, and the in-transaction rendering. */
class ExpressionToStringWithDatabaseTests : DatabaseTestsBase() {
    @Test
    fun testExpressionToStringWithConnectedDatabaseButNoTransaction() {
        withConnection { _, _ ->
            // A database is connected here, but no transaction is open on this thread.
            assertEquals("render_testers.id = 5", (RenderTesters.id eq 5).toString())
            assertEquals("SUM(render_testers.id)", RenderTesters.id.sum().toString())
        }
    }

    @Test
    fun testInTransactionRenderingIsUnchanged() {
        Assumptions.assumeTrue(dialect == TestDB.H2_V2)

        withDb {
            val alias = RenderTesters.alias("t")

            // Expected values captured from the pre-existing rendering (before the transaction-free
            // fallback was introduced) to guarantee the in-transaction output stays byte-identical.
            assertEquals("RENDER_TESTERS.ID = 5", (RenderTesters.id eq 5).toString())
            assertEquals("""RENDER_TESTERS."order" = 'a'""", (RenderTesters.order eq "a").toString())
            assertEquals("""RENDER_TESTERS."name" IS NULL""", RenderTesters.name.isNull().toString())
            assertEquals("SUM(RENDER_TESTERS.ID)", RenderTesters.id.sum().toString())
            assertEquals("""LOWER(RENDER_TESTERS."name")""", RenderTesters.name.lowerCase().toString())
            assertEquals(
                """CONCAT(RENDER_TESTERS."name", RENDER_TESTERS."order")""",
                concat(RenderTesters.name, RenderTesters.order).toString()
            )
            assertEquals("""CHAR_LENGTH(RENDER_TESTERS."name")""", RenderTesters.name.charLength().toString())
            assertEquals(
                "CASE WHEN RENDER_TESTERS.ID = 1 THEN 1 ELSE 0 END",
                Case().When(RenderTesters.id eq 1, intLiteral(1)).Else(intLiteral(0)).toString()
            )
            assertEquals("t.ID = 1", (alias[RenderTesters.id] eq 1).toString())
        }
    }
}

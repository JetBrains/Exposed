package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcPreparedStatementImpl
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that a JDBC statement is closed as soon as nothing can read from it anymore, instead of being kept
 * until the transaction ends (GitHub #2407).
 */
class StatementReleaseTests : DatabaseTestsBase() {
    object Items : Table("statement_release_items") {
        val id = integer("id")
        val name = varchar("name", 50)

        override val primaryKey = PrimaryKey(id)
    }

    /** Records the JDBC statement behind every execution, so that its state can be checked afterwards. */
    private class StatementCapture : StatementInterceptor {
        val statements = mutableListOf<PreparedStatement>()

        val last: PreparedStatement
            get() = statements.last()

        override fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {
            (executedStatement as? JdbcPreparedStatementImpl)?.let { statements += it.statement }
        }
    }

    private fun JdbcTransaction.captureStatements(): StatementCapture = StatementCapture().also { registerInterceptor(it) }

    @Test
    fun testStatementsWithoutResultSetAreClosedAfterExecution() {
        withTables(Items) {
            val capture = captureStatements()

            Items.insert {
                it[id] = 1
                it[name] = "one"
            }
            assertTrue(capture.last.isClosed, "insert")

            Items.update({ Items.id eq 1 }) { it[name] = "uno" }
            assertTrue(capture.last.isClosed, "update")

            Items.batchInsert(2..4) {
                this[Items.id] = it
                this[Items.name] = "n$it"
            }
            assertTrue(capture.last.isClosed, "batch insert")

            Items.deleteWhere { Items.id eq 1 }
            assertTrue(capture.last.isClosed, "delete")

            val count = exec("SELECT COUNT(*) FROM ${Items.nameInDatabaseCase()}") { rs ->
                rs.next()
                rs.getLong(1)
            }
            assertEquals(3, count)
            assertTrue(capture.last.isClosed, "exec with transform")
        }
    }

    @Test
    fun testQueryStatementIsClosedOnceItsResultIsConsumed() {
        withTables(Items) {
            Items.batchInsert(1..5) {
                this[Items.id] = it
                this[Items.name] = "n$it"
            }
            val capture = captureStatements()

            assertEquals(5, Items.selectAll().toList().size)
            assertTrue(capture.last.isClosed, "fully iterated")

            assertEquals(5, Items.selectAll().count())
            assertTrue(capture.last.isClosed, "count")

            assertFalse(Items.selectAll().empty())
            assertTrue(capture.last.isClosed, "empty")

            assertTrue(Items.selectAll().where { Items.id eq 99 }.empty())
            assertTrue(capture.last.isClosed, "empty result")
        }
    }

    @Test
    fun testPartiallyConsumedQueryStaysOpenUntilExhaustedOrReleased() {
        withTables(Items) {
            Items.batchInsert(1..5) {
                this[Items.id] = it
                this[Items.name] = "n$it"
            }
            val capture = captureStatements()

            val exhausted = Items.selectAll().iterator()
            val exhaustedStatement = capture.last
            exhausted.next()
            if (db.supportsMultipleResultSets) {
                assertFalse(exhaustedStatement.isClosed, "one row read")
            }
            while (exhausted.hasNext()) exhausted.next()
            assertTrue(exhaustedStatement.isClosed, "exhausted")

            val abandoned = Items.selectAll().iterator()
            val abandonedStatement = capture.last
            abandoned.next()
            closeExecutedStatements()
            assertTrue(abandonedStatement.isClosed, "abandoned then released by the transaction")
        }
    }

    @Test
    fun testFailedStatementIsClosed() {
        withTables(Items) {
            Items.insert {
                it[id] = 1
                it[name] = "one"
            }

            assertFailAndRollback("duplicate primary key") {
                Items.insert {
                    it[id] = 1
                    it[name] = "again"
                }
            }

            val failed = (currentStatement as JdbcPreparedStatementImpl).statement
            assertTrue(failed.isClosed, "failed statement")
        }
    }

    @Test
    fun testStatementsDoNotAccumulateInLongTransaction() {
        withTables(Items) {
            val capture = captureStatements()

            repeat(200) { i ->
                Items.insert {
                    it[id] = i
                    it[name] = "n$i"
                }
                assertEquals(1, Items.selectAll().where { Items.id eq i }.count())
                Items.update({ Items.id eq i }) { it[name] = "m$i" }
            }

            assertEquals(600, capture.statements.size)
            assertTrue(capture.statements.all { it.isClosed }, "every statement released before the transaction ended")
        }
    }
}

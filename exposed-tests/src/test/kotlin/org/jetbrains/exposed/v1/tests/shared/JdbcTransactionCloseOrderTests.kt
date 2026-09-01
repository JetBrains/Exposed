package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementResult
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.io.InputStream
import kotlin.test.assertEquals

class JdbcTransactionCloseOrderTests : DatabaseTestsBase() {
    @Test
    fun testExecutedStatementsAreClosedInReverseOrder() {
        withConnection { database, _ ->
            Assumptions.assumeTrue(
                database.supportsMultipleResultSets,
                "Statement accumulation requires support for multiple result sets"
            )

            val closeOrder = mutableListOf<Int>()

            transaction(db = database) {
                repeat(3) { index ->
                    exec(RecordingExecutable(index + 1, closeOrder))
                }

                closeExecutedStatements()
            }

            assertEquals(listOf(3, 2, 1), closeOrder)
        }
    }

    private class RecordingExecutable(
        id: Int,
        closeOrder: MutableList<Int>
    ) : Statement<Unit>(StatementType.SELECT, emptyList()), BlockingExecutable<Unit, Statement<Unit>> {
        override val statement: Statement<Unit>
            get() = this

        private val preparedStatement = RecordingPreparedStatement(id, closeOrder)

        override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = "SELECT 1"

        override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = emptyList()

        override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi = preparedStatement

        override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction) = Unit
    }

    private class RecordingPreparedStatement(
        private val id: Int,
        private val closeOrder: MutableList<Int>
    ) : JdbcPreparedStatementApi {
        override var fetchSize: Int? = null
        override var timeout: Int? = null
        override val resultSet: JdbcResult? = null

        override fun closeIfPossible() {
            closeOrder += id
        }

        override fun addBatch(): Unit = unexpectedCall()
        override fun executeQuery(): JdbcResult = unexpectedCall()
        override fun executeUpdate(): Int = unexpectedCall()
        override fun executeMultiple(): List<StatementResult> = unexpectedCall()
        override fun executeBatch(): List<Int> = unexpectedCall()
        override fun cancel(): Unit = unexpectedCall()
        override fun set(index: Int, value: Any, columnType: IColumnType<*>): Unit = unexpectedCall()
        override fun setNull(index: Int, columnType: IColumnType<*>): Unit = unexpectedCall()
        override fun setInputStream(index: Int, inputStream: InputStream, setAsBlobObject: Boolean): Unit =
            unexpectedCall()

        override fun setArray(index: Int, type: ArrayColumnType<*, *>, array: Array<*>): Unit = unexpectedCall()

        private fun unexpectedCall(): Nothing = error("The recording statement should only be closed")
    }
}

package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet

/**
 * Represents the SQL query that obtains information about a statement execution plan.
 *
 * @param analyze Whether the statement whose execution plan is being queried should actually be executed as well.
 * @param options String of comma-separated parameters to append after the `EXPLAIN` keyword.
 */
open class ExplainQuery(
    val analyze: Boolean,
    val options: String?,
    private val internalStatement: Statement<*>
) : Iterable<ExplainResultRow>, Statement<ResultSet>(StatementType.SHOW, emptyList()) {
    private val transaction
        get() = TransactionManager.current()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): ResultSet = executeQuery()

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = internalStatement.arguments()

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val internalSql = internalStatement.prepareSQL(transaction, prepared)
        return transaction.db.dialect.functionProvider.explain(analyze, options, internalSql, transaction)
    }

    override fun iterator(): Iterator<ExplainResultRow> {
        val resultIterator = ResultIterator(transaction.exec(this)!!)
        return Iterable { resultIterator }.iterator()
    }

    private inner class ResultIterator(private val rs: ResultSet) : Iterator<ExplainResultRow> {
        private val fieldIndex: Map<String, Int> = List(rs.metaData.columnCount) { i ->
            rs.metaData.getColumnName(i + 1) to i
        }.toMap()

        private var hasNext = false
            set(value) {
                field = value
                if (!field) {
                    rs.statement?.close()
                    transaction.openResultSetsCount--
                }
            }

        init {
            hasNext = rs.next()
        }

        override fun hasNext(): Boolean = hasNext

        override operator fun next(): ExplainResultRow {
            if (!hasNext) throw NoSuchElementException()
            val result = ExplainResultRow.create(rs, fieldIndex)
            hasNext = rs.next()
            return result
        }
    }
}

/**
 * A row of data representing a single record retrieved from a database result set about a statement execution plan.
 *
 * @param fieldIndex Mapping of the field names stored on this row to their index positions.
 */
class ExplainResultRow(
    val fieldIndex: Map<String, Int>,
    private val data: Array<Any?>
) {
    override fun toString(): String = fieldIndex.entries.joinToString { "${it.key}=${data[it.value]}" }

    companion object {
        /** Creates an [ExplainResultRow] storing all fields in [fieldIndex] with their values retrieved from a [ResultSet]. */
        fun create(rs: ResultSet, fieldIndex: Map<String, Int>): ExplainResultRow {
            val fieldValues = arrayOfNulls<Any?>(fieldIndex.size)
            fieldIndex.values.forEach { index ->
                fieldValues[index] = rs.getObject(index + 1)
            }
            return ExplainResultRow(fieldIndex, fieldValues)
        }
    }
}

/**
* Creates an [ExplainQuery] using the `EXPLAIN` keyword, which obtains information about a statement execution plan.
*
* **Note:** This operation is not supported by all vendors, please check the documentation.
*
* @param analyze (optional) Whether the statement whose execution plan is being queried should actually be executed as well.
* **Note:** The `ANALYZE` parameter is not supported by all vendors, please check the documentation.
* @param options (optional) String of comma-separated parameters to append after the `EXPLAIN` keyword.
* **Note:** Optional parameters are not supported by all vendors, please check the documentation.
* @param body The statement for which an execution plan should be queried. This can be a `SELECT`, `INSERT`,
 * `REPLACE`, `UPDATE` or `DELETE` statement.
* @sample org.jetbrains.exposed.sql.tests.shared.dml.ExplainTests.testExplainWithStatementsNotExecuted
*/
fun Transaction.explain(
    analyze: Boolean = false,
    options: String? = null,
    body: Transaction.() -> Any?
): ExplainQuery {
    val query = try {
        blockStatementExecution = true
        val internalStatement = body() as? Statement<*> ?: explainStatement
        checkNotNull(internalStatement) { "A valid query or statement must be provided to the EXPLAIN body." }
        ExplainQuery(analyze, options, internalStatement)
    } finally {
        explainStatement = null
        blockStatementExecution = false
    }

    return query
}

package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementIterator
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.api.ResultApi
import org.jetbrains.exposed.sql.transactions.TransactionManager

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
) : Iterable<ExplainResultRow>, Statement<ResultApi>(StatementType.SHOW, emptyList()) {
    private val transaction
        get() = TransactionManager.current()

    override suspend fun PreparedStatementApi.executeInternal(transaction: Transaction): ResultApi = executeQuery()

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = internalStatement.arguments()

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val internalSql = internalStatement.prepareSQL(transaction, prepared)
        return transaction.db.dialect.functionProvider.explain(analyze, options, internalSql, transaction)
    }

    override fun iterator(): Iterator<ExplainResultRow> {
        val resultIterator = ResultIterator(transaction.exec(this)!!)
        return Iterable { resultIterator }.iterator()
    }

    private inner class ResultIterator(
        rs: ResultApi
    ) : StatementIterator<ResultApi, String, ExplainResultRow>(rs) {
        override val fieldIndex = List(result.metadataColumnCount()) { i ->
            result.metadataColumnName(i + 1) to i
        }.toMap()

        init {
            hasNext = result.next()
        }

        override fun createResultRow(): ExplainResultRow = ExplainResultRow.create(result, fieldIndex)
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
        /** Creates an [ExplainResultRow] storing all fields in [fieldIndex] with their values retrieved from a [ResultApi] object. */
        fun create(rs: ResultApi, fieldIndex: Map<String, Int>): ExplainResultRow {
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

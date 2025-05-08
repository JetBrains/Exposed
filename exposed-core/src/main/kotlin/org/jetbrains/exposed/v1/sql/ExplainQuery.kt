package org.jetbrains.exposed.v1.sql

import org.jetbrains.exposed.v1.sql.statements.Statement
import org.jetbrains.exposed.v1.sql.statements.StatementType
import org.jetbrains.exposed.v1.sql.statements.api.ResultApi
import org.jetbrains.exposed.v1.sql.statements.api.RowApi

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
) : Statement<ResultApi>(StatementType.SHOW, emptyList()) {
    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = internalStatement.arguments()

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val internalSql = internalStatement.prepareSQL(transaction, prepared)
        return transaction.db.dialect.functionProvider.explain(analyze, options, internalSql, transaction)
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
        fun create(rs: RowApi, fieldIndex: Map<String, Int>): ExplainResultRow {
            val fieldValues = arrayOfNulls<Any?>(fieldIndex.size)
            fieldIndex.values.forEach { index ->
                fieldValues[index] = rs.getObject(index + 1)
            }
            return ExplainResultRow(fieldIndex, fieldValues)
        }
    }
}

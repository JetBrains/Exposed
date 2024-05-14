package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

/**
 * The base implementation of SQL merge command that is used by statements like [MergeSelectStatement], [MergeTableStatement]
 *
 * @property table The destination [Table] where records will be merged into.
 */
abstract class MergeStatement(val table: Table) : Statement<Int>(
    StatementType.MERGE, listOf(table)
) {
    protected val whenBranches = mutableListOf<WhenBranchData>()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int? {
        return executeUpdate()
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> {
        val result = QueryBuilder(true).run {
            whenBranches.flatMap { it.arguments }.forEach { (column, value) ->
                if (value !is Column<*> && value !is Expression<*>) {
                    registerArgument(column, value)
                }
            }
            if (args.isNotEmpty()) listOf(args) else emptyList()
        }
        return result
    }

    /**
     * Defines an insert operation to be performed when there is no matching record in the destination table.
     *
     * @param and An additional optional condition [Op<Boolean>] to refine when the insert should take place.
     * @param body A lambda to configure the [InsertStatement] in which the required columns and corresponding
     * values are specified for the insert operation.
     */
    fun whenNotMatchedInsert(and: Op<Boolean>? = null, body: (InsertStatement<Int>) -> Unit) {
        val insert = InsertStatement<Int>(table).apply(body)
        whenBranches.add(WhenBranchData(insert.arguments!!.first(), MergeWhenAction.INSERT, and))
    }

    /**
     * Defines an update operation to be executed when a matching record is found in the destination table.
     *
     * @param and An additional optional condition [Op<Boolean>] to specify when the update should occur.
     * @param deleteWhere Applicable only to Oracle SQL. A condition [Op<Boolean>] used to indicate condition of row deletion.
     * Oracle SQL does not support separate "delete" clause.
     * @param body A lambda to define the [UpdateStatement] which sets the values of columns in the matching
     * records.
     */
    fun whenMatchedUpdate(and: Op<Boolean>? = null, deleteWhere: Op<Boolean>? = null, body: (UpdateStatement) -> Unit) {
        val update = UpdateStatement(table, limit = 1).apply(body)
        whenBranches.add(WhenBranchData(update.firstDataSet, MergeWhenAction.UPDATE, and, deleteWhere))
    }

    /**
     * Specifies a delete operation to be executed when a matching record is found in the destination table.
     *
     * @param and An additional optional condition [Op<Boolean>] to determine when the delete operation
     * should be performed.
     */
    fun whenMatchedDelete(and: Op<Boolean>? = null) {
        whenBranches.add(WhenBranchData(emptyList(), MergeWhenAction.DELETE, and))
    }

    data class WhenBranchData(
        val arguments: List<Pair<Column<*>, Any?>>,
        val action: MergeWhenAction,
        val and: Op<Boolean>?,
        /**
         * deleteWhere is applicable only to Oracle SQL database which has no dedicated "when delete" clause
         */
        val deleteWhere: Op<Boolean>? = null
    )

    enum class MergeWhenAction {
        INSERT, UPDATE, DELETE
    }
}

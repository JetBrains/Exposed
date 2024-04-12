package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

abstract class MergeBaseStatement(val table: Table) : Statement<Int>(
    StatementType.MERGE, listOf(table)
) {
    protected val whenBranches = mutableListOf<WhenBranchData>()

    // Workaround due to losing defaultValueFun inside Alias
    private val originalTable = if (table is Alias<*>) table.delegate else table

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

    fun insertWhenNotMatched(and: Op<Boolean>? = null, body: (InsertStatement<Int>) -> Unit) {
        val insert = InsertStatement<Int>(originalTable).apply(body)
        whenBranches.add(WhenBranchData(insert.arguments!!.first(), MergeWhenAction.INSERT, and))
    }

    fun updateWhenMatched(and: Op<Boolean>? = null, deleteWhere: Op<Boolean>? = null, body: (UpdateStatement) -> Unit) {
        val update = UpdateStatement(originalTable, limit = 1).apply(body)
        whenBranches.add(WhenBranchData(update.firstDataSet, MergeWhenAction.UPDATE, and, deleteWhere))
    }

    fun deleteWhenMatched(and: Op<Boolean>? = null) {
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

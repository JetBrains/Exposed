package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.*

/**
 * The base implementation of SQL merge command that is used by statements like [MergeSelectStatement], [MergeTableStatement]
 *
 * @property table The destination [Table] where records will be merged into.
 */
abstract class MergeStatement(val table: Table) : Statement<Int>(
    StatementType.MERGE, listOf(table)
) {
    protected val clauses = mutableListOf<Clause>()

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> {
        val result = QueryBuilder(true).run {
            clauses.flatMap { it.arguments }.forEach { (column, value) ->
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
     * @param overridingUserValue Postgres only. Indicates whether to use the `OVERRIDING USER VALUE` clause for the insert.
     * @param overridingSystemValue Postgres only. Indicates whether to use the `OVERRIDING SYSTEM VALUE` clause for the insert.
     * @param and An additional optional condition [Op<Boolean>] to refine when the insert should take place.
     * @param body A lambda to configure the [InsertStatement] in which the required columns and corresponding
     * values are specified for the insert operation.
     */
    fun whenNotMatchedInsert(
        and: Op<Boolean>? = null,
        overridingUserValue: Boolean = false,
        overridingSystemValue: Boolean = false,
        body: (InsertStatement<Int>) -> Unit
    ) {
        val arguments = InsertStatement<Int>(table).apply(body).arguments!!.first()
        clauses.add(
            Clause(ClauseCondition.NOT_MATCHED, ClauseAction.INSERT, arguments, and, null, overridingSystemValue, overridingUserValue)
        )
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
        val arguments = UpdateStatement(table, limit = 1).apply(body).firstDataSet
        clauses.add(Clause(ClauseCondition.MATCHED, ClauseAction.UPDATE, arguments, and, deleteWhere))
    }

    /**
     * Specifies a delete operation to be executed when a matching record is found in the destination table.
     *
     * @param and An additional optional condition [Op<Boolean>] to determine when the delete operation
     * should be performed.
     */
    fun whenMatchedDelete(and: Op<Boolean>? = null) {
        clauses.add(Clause(ClauseCondition.MATCHED, ClauseAction.DELETE, emptyList(), and))
    }

    /**
     * Postgres only. Specifies that no operation should be performed when a matching record is found in the destination table.
     *
     * @param and An additional optional condition [Op<Boolean>] to determine when the do-nothing operation
     * should be performed.
     */
    fun whenMatchedDoNothing(and: Op<Boolean>? = null) {
        clauses.add(Clause(ClauseCondition.MATCHED, ClauseAction.DO_NOTHING, emptyList(), and))
    }

    /**
     * Postgres only. Specifies that no operation should be performed when no matching record is found in the destination table.
     *
     * @param and An additional optional condition [Op<Boolean>] to determine when the do-nothing operation
     * should be performed.
     */
    fun whenNotMatchedDoNothing(and: Op<Boolean>? = null) {
        clauses.add(Clause(ClauseCondition.NOT_MATCHED, ClauseAction.DO_NOTHING, emptyList(), and))
    }

    data class Clause(
        val type: ClauseCondition,
        val action: ClauseAction,
        val arguments: List<Pair<Column<*>, Any?>>,
        val and: Op<Boolean>?,
        /** deleteWhere is applicable only to Oracle SQL database which has no dedicated "when delete" clause */
        val deleteWhere: Op<Boolean>? = null,
        /** Postgres clause modifier to override the system value */
        val overridingSystemValue: Boolean = false,
        /** Postgres clause modifier to override the user value */
        val overridingUserValue: Boolean = false,
    )

    enum class ClauseCondition {
        MATCHED, NOT_MATCHED
    }

    enum class ClauseAction {
        INSERT, UPDATE, DELETE, DO_NOTHING
    }
}

/**
 * Represents an SQL MERGE statement. It encapsulates the logic to perform conditional updates, insertions,
 * or deletions.
 *
 * Here is only the part specific for the Table as a source implementation.
 * Look into [MergeStatement] to find the base implementation of that command.
 *
 * @param dest The destination [Table] where records will be merged into.
 * @property source The source [Table] from which records are taken to compare with `dest`.
 * @property on The join condition [Op<Boolean>] that specifies how to match records in both `source` and `dest`.
 */
open class MergeTableStatement(
    dest: Table,
    private val source: Table,
    private val on: Op<Boolean>?
) : MergeStatement(dest) {
    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        return transaction.db.dialect.functionProvider.merge(table, source, transaction, clauses, on)
    }
}

/**
 * Represents an SQL MERGE statement. It encapsulates the logic to perform conditional updates, insertions,
 * or deletions.
 *
 * Here is only the part specific for the Query as a source implementation.
 * Look into [MergeStatement] to find the base implementation of that command.
 *
 * @param dest The destination [Table] where records will be merged into.
 * @property selectQuery The source [QueryAlias] from which records are taken to compare with `dest`.
 * @property on The join condition [Op<Boolean>] that specifies how to match records in both `source` and `dest`.
 */
open class MergeSelectStatement(
    dest: Table,
    private val selectQuery: QueryAlias,
    val on: Op<Boolean>
) : MergeStatement(dest) {
    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> {
        val queryArguments = selectQuery.query.arguments().firstOrNull() ?: emptyList()
        val mergeStatementArguments = super.arguments().firstOrNull() ?: emptyList()
        return listOf(
            queryArguments + mergeStatementArguments
        )
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        return transaction.db.dialect.functionProvider.mergeSelect(table, selectQuery, transaction, clauses, on, prepared)
    }
}

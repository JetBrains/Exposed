package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.h2Mode

/**
 * Represents the SQL statement that deletes one or more rows of a table.
 *
 * @param targetsSet Column set to delete rows from. This may be a [Table] or a [Join] instance.
 * @param where Condition that determines which rows to delete.
 * @param isIgnore Whether to ignore errors or not.
 * **Note** [isIgnore] is not supported by all vendors. Please check the documentation.
 * @param limit Maximum number of rows to delete.
 * @param offset The number of rows to skip.
 * @param targetTables List of specific tables from [targetsSet] to delete rows from.
 */
open class DeleteStatement(
    val targetsSet: ColumnSet,
    val where: Op<Boolean>? = null,
    val isIgnore: Boolean = false,
    val limit: Int? = null,
    val offset: Long? = null,
    val targetTables: List<Table> = emptyList(),
) : Statement<Int>(StatementType.DELETE, targetsSet.targetTables()) {
    @Deprecated(
        "This constructor will be removed in future releases.",
        ReplaceWith("DeleteStatement(targetsSet = table, where, isIgnore, limit, offset, emptyList())"),
        DeprecationLevel.WARNING
    )
    constructor(
        table: Table,
        where: Op<Boolean>?,
        isIgnore: Boolean,
        limit: Int?,
        offset: Long?
    ) : this(table, where, isIgnore, limit, offset, emptyList())

    @Deprecated(
        "This property will be removed in future releases and replaced with a property that stores a `ColumnSet`," +
            "which may be a `Table` or a `Join`. To access the table(s) to which the columns belong, use `ColumnSet.targetTables()`",
        ReplaceWith("targetsSet"),
        DeprecationLevel.WARNING
    )
    val table: Table = targets.first()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        return executeUpdate()
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val dialect = transaction.db.dialect
        return when (targetsSet) {
            is Table -> dialect.functionProvider.delete(
                isIgnore, targetsSet, where?.let { QueryBuilder(prepared).append(it).toString() }, limit, transaction
            )
            is Join -> {
                val functionProvider = when (dialect.h2Mode) {
                    H2CompatibilityMode.PostgreSQL, H2CompatibilityMode.Oracle, H2CompatibilityMode.SQLServer -> H2FunctionProvider
                    else -> dialect.functionProvider
                }
                functionProvider.delete(isIgnore, targetsSet, targetTables, where, limit, transaction)
            }
            else -> transaction.throwUnsupportedException("DELETE with ${targetsSet::class.simpleName} is unsupported")
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = QueryBuilder(true).run {
        if (targetsSet is Join) {
            targetsSet.joinParts.forEach {
                (it.joinPart as? QueryAlias)?.query?.prepareSQL(this)
                it.additionalConstraint?.invoke(SqlExpressionBuilder)?.toQueryBuilder(this)
            }
        }
        where?.toQueryBuilder(this)
        listOf(args)
    }

    companion object {
        /**
         * Creates a [DeleteStatement] that deletes only rows in [table] that match the provided [op].
         *
         * @return Count of deleted rows.
         */
        fun where(transaction: Transaction, table: Table, op: Op<Boolean>, isIgnore: Boolean = false, limit: Int? = null, offset: Long? = null): Int =
            DeleteStatement(targetsSet = table, op, isIgnore, limit, offset).execute(transaction) ?: 0

        /**
         * Creates a [DeleteStatement] that deletes all rows in [table].
         *
         * @return Count of deleted rows.
         */
        fun all(transaction: Transaction, table: Table): Int = DeleteStatement(table).execute(transaction) ?: 0
    }
}

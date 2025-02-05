package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
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
 * @param targetTables List of specific tables from [targetsSet] to delete rows from.
 */
open class DeleteStatement(
    val targetsSet: ColumnSet,
    val where: Op<Boolean>? = null,
    val isIgnore: Boolean = false,
    val limit: Int? = null,
    val targetTables: List<Table> = emptyList(),
) : Statement<Int>(StatementType.DELETE, targetsSet.targetTables()) {
    @Deprecated(
        "This constructor will be removed in future releases.",
        ReplaceWith("DeleteStatement(targetsSet = table, where, isIgnore, limit, emptyList())"),
        DeprecationLevel.ERROR
    )
    @Suppress("UnusedPrivateProperty")
    constructor(
        table: Table,
        where: Op<Boolean>?,
        isIgnore: Boolean,
        limit: Int?,
        offset: Long?
    ) : this(table, where, isIgnore, limit, emptyList())

    @Deprecated(
        "This property will be removed in future releases and replaced with a property that stores a `ColumnSet`," +
            "which may be a `Table` or a `Join`. To access the table(s) to which the columns belong, use `ColumnSet.targetTables()`",
        ReplaceWith("targetsSet"),
        DeprecationLevel.ERROR
    )
    val table: Table = targets.first()

    @Deprecated(
        "This property is not being used and will be removed in future releases. Please leave a comment on " +
            "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-550/DeleteStatement-holds-unused-offset-property) " +
            "with a use-case if your database supports the OFFSET clause in a DELETE statement.",
        level = DeprecationLevel.ERROR
    )
    val offset: Long? = null

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
}

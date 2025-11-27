package org.jetbrains.exposed.v1.core.statements

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.v1.core.vendors.H2FunctionProvider
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.h2Mode
import org.jetbrains.exposed.v1.exceptions.throwUnsupportedException

/**
 * Represents the SQL statement that updates rows of a table.
 *
 * @param targetsSet Column set to update rows from. This may be a [Table] or a [Join] instance.
 * @param limit Maximum number of rows to update.
 * @param where Condition that determines which rows to update.
 */
open class UpdateStatement(val targetsSet: ColumnSet, val limit: Int?, val where: Op<Boolean>? = null) :
    UpdateBuilder<Int>(StatementType.UPDATE, targetsSet.targetTables()) {

    /** The initial list of columns to update with their updated values. */
    open val firstDataSet: List<Pair<Column<*>, Any?>>
        get() {
            @OptIn(InternalApi::class)
            return values.toList()
        }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        require(firstDataSet.isNotEmpty()) { "Can't prepare UPDATE statement without fields to update" }

        val dialect = transaction.db.dialect
        return when (targetsSet) {
            is Table -> dialect.functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)
            is Join -> {
                val functionProvider = when (dialect.h2Mode) {
                    H2CompatibilityMode.PostgreSQL, H2CompatibilityMode.Oracle, H2CompatibilityMode.SQLServer -> H2FunctionProvider
                    else -> dialect.functionProvider
                }
                functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)
            }
            else -> transaction.throwUnsupportedException("UPDATE with ${targetsSet::class.simpleName} unsupported")
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = QueryBuilder(true).run {
        val dialect = currentDialect
        when (targetsSet) {
            is Join if dialect is OracleDialect -> {
                registerAdditionalArgs(targetsSet)
                registerWhereArg()
                registerUpdateArgs()
            }
            is Join if (dialect is SQLServerDialect || dialect is PostgreSQLDialect) -> {
                registerUpdateArgs()
                registerAdditionalArgs(targetsSet)
                registerWhereArg()
            }
            is Join -> {
                registerAdditionalArgs(targetsSet)
                registerUpdateArgs()
                registerWhereArg()
            }
            else -> {
                registerUpdateArgs()
                registerWhereArg()
            }
        }
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }

    private fun QueryBuilder.registerWhereArg() {
        where?.toQueryBuilder(this)
    }

    private fun QueryBuilder.registerUpdateArgs() {
        @OptIn(InternalApi::class)
        values.forEach { registerArgument(it.key, it.value) }
    }

    private fun QueryBuilder.registerAdditionalArgs(join: Join) {
        join.joinParts.forEach {
            (it.joinPart as? QueryAlias)?.query?.prepareSQL(this)
            it.additionalConstraint?.invoke()?.toQueryBuilder(this)
        }
    }
}

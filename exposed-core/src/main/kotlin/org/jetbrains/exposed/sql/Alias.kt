package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable

/** Represents a temporary SQL identifier, [alias], for a [delegate] table. */
class Alias<out T : Table>(val delegate: T, val alias: String) : Table() {

    override val tableName: String get() = alias

    /** The table name along with its [alias]. */
    val tableNameWithAlias: String = "${delegate.tableName} $alias"

    private fun <T> Column<T>.clone() = Column<T>(this@Alias, name, columnType).also {
        it.defaultValueFun = defaultValueFun
        it.dbDefaultValue = dbDefaultValue
        it.isDatabaseGenerated = isDatabaseGenerated
        it.foreignKey = foreignKey
    }

    /**
     * Returns the original column from the [delegate] table, or `null` if the [column] is not associated
     * with this table alias.
     */
    fun <R> originalColumn(column: Column<R>): Column<R>? {
        @Suppress("UNCHECKED_CAST")
        return if (column.table == this) {
            delegate.columns.first { column.name == it.name } as Column<R>
        } else {
            null
        }
    }

    override val fields: List<Expression<*>> = delegate.fields.map { (it as? Column<*>)?.clone() ?: it }

    override val columns: List<Column<*>> = fields.filterIsInstance<Column<*>>()

    override fun createStatement() = throw UnsupportedOperationException("Unsupported for aliases")

    override fun dropStatement() = throw UnsupportedOperationException("Unsupported for aliases")

    override fun modifyStatement() = throw UnsupportedOperationException("Unsupported for aliases")

    override fun equals(other: Any?): Boolean {
        if (other !is Alias<*>) return false
        return this.tableNameWithAlias == other.tableNameWithAlias
    }

    override fun hashCode(): Int = tableNameWithAlias.hashCode()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any?> get(original: Column<T>): Column<T> =
        delegate.columns.find { it == original }?.let { it.clone() as? Column<T> }
            ?: error("Column not found in original table")
}

/** Represents a temporary SQL identifier, [alias], for a [delegate] expression. */
class ExpressionAlias<T>(val delegate: Expression<T>, val alias: String) : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        if (delegate is ComparisonOp && (currentDialectIfAvailable is SQLServerDialect || currentDialectIfAvailable is OracleDialect)) {
            +"(CASE WHEN "
            append(delegate)
            +" THEN 1 ELSE 0 END)"
        } else {
            append(delegate)
        }
        append(" $alias")
    }

    /** Returns an [Expression] containing only the string representation of this [alias]. */
    fun aliasOnlyExpression(): Expression<T> {
        return if (delegate is ExpressionWithColumnType<T>) {
            object : Function<T>(delegate.columnType) {
                override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(alias) }
            }
        } else {
            object : Expression<T>() {
                override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(alias) }
            }
        }
    }
}

/** Represents a temporary SQL identifier, [alias], for a [query]. */
class QueryAlias(val query: AbstractQuery<*>, val alias: String) : ColumnSet() {

    override fun describe(s: Transaction, queryBuilder: QueryBuilder) = queryBuilder {
        append("(")
        query.prepareSQL(queryBuilder)
        append(") ", alias)
    }

    override val fields: List<Expression<*>> = query.set.fields.map { expression ->
        (expression as? Column<*>)?.clone() ?: (expression as? ExpressionAlias<*>)?.aliasOnlyExpression() ?: expression
    }

    override val columns: List<Column<*>> = fields.filterIsInstance<Column<*>>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any?> get(original: Column<T>): Column<T> =
        query.set.source.columns.find { it == original }?.clone() as? Column<T>
            ?: error("Column not found in original table")

    operator fun <T : Any?> get(original: Expression<T>): Expression<T> {
        val aliases = query.set.fields.filterIsInstance<ExpressionAlias<T>>()
        return aliases.find { it == original }?.let {
            it.delegate.alias("$alias.${it.alias}").aliasOnlyExpression()
        } ?: aliases.find { it.delegate == original }?.aliasOnlyExpression()
            ?: error("Field not found in original table fields")
    }

    override fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        onColumn: Expression<*>?,
        otherColumn: Expression<*>?,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?
    ): Join =
        Join(this, otherTable, joinType, onColumn, otherColumn, additionalConstraint)

    override infix fun innerJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.LEFT)

    override infix fun rightJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.RIGHT)

    override infix fun fullJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.FULL)

    override infix fun crossJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.CROSS)

    private fun <T> Column<T>.clone() = Column<T>(table.alias(alias), name, columnType)
}

/**
 * Creates a temporary identifier, [alias], for [this] table.
 *
 * The alias will be used on the database-side if the alias object is used to generate an SQL statement,
 * instead of [this] table object.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.JoinTests.testJoinWithAlias01
 */
fun <T : Table> T.alias(alias: String) = Alias(this, alias)

/**
 * Creates a temporary identifier, [alias], for [this] query.
 *
 * The alias will be used on the database-side if the alias object is used to generate an SQL statement,
 * instead of [this] query object.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.AliasesTests.testJoinSubQuery01
 */
fun <T : AbstractQuery<*>> T.alias(alias: String) = QueryAlias(this, alias)

/**
 * Creates a temporary identifier, [alias], for [this] expression.
 *
 * The alias will be used on the database-side if the alias object is used to generate an SQL statement,
 * instead of [this] expression object.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.AliasesTests.testJoinSubQuery01
 */
fun <T> Expression<T>.alias(alias: String) = ExpressionAlias(this, alias)

/**
 * Creates a join relation with a query.
 *
 * @param on The condition to join that will be placed in the `ON` clause.
 * @param joinType The `JOIN` clause type used to combine rows. Defaults to [JoinType.INNER].
 * @param joinPart The query to join with.
 * @sample org.jetbrains.exposed.sql.tests.shared.AliasesTests.testJoinSubQuery02
 */
fun Join.joinQuery(on: (SqlExpressionBuilder.(QueryAlias) -> Op<Boolean>), joinType: JoinType = JoinType.INNER, joinPart: () -> AbstractQuery<*>): Join {
    val qAlias = joinPart().alias("q${joinParts.count { it.joinPart is QueryAlias }}")
    return join(qAlias, joinType, additionalConstraint = { on(qAlias) })
}

/**
 * Creates a join relation between [this] table and a query.
 *
 * @param on The condition to join that will be placed in the `ON` clause.
 * @param joinType The `JOIN` clause type used to combine rows. Defaults to [JoinType.INNER].
 * @param joinPart The query to join with.
 */
fun Table.joinQuery(on: (SqlExpressionBuilder.(QueryAlias) -> Op<Boolean>), joinType: JoinType = JoinType.INNER, joinPart: () -> AbstractQuery<*>) =
    Join(this).joinQuery(on, joinType, joinPart)

/**
 * Returns the most recent [QueryAlias] instance used to create this join relation, or `null` if a query was not joined.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.AliasesTests.testJoinSubQuery02
 */
val Join.lastQueryAlias: QueryAlias?
    get() = joinParts.mapNotNull { it.joinPart as? QueryAlias }.lastOrNull()

/**
 * Wraps a [query] as an [Expression] so that it can be used as part of an SQL statement or in another query clause.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.OrderByTests.testOrderByExpressions
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testInsertWithColumnExpression
 */
fun <T : Any> wrapAsExpression(query: AbstractQuery<*>) = object : Expression<T?>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("(")
        query.prepareSQL(this)
        append(")")
    }
}

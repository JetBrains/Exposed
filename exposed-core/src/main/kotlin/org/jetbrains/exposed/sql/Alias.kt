package org.jetbrains.exposed.sql


class Alias<out T:Table>(val delegate: T, val alias: String) : Table() {

    override val tableName: String get() = alias

    val tableNameWithAlias: String = "${delegate.tableName} AS $alias"

    private fun <T:Any?> Column<T>.clone() = Column<T>(this@Alias, name, columnType)

    fun <R> originalColumn(column: Column<R>) : Column<R>? {
        @Suppress("UNCHECKED_CAST")
        return if (column.table == this)
            delegate.columns.first { column.name == it.name } as Column<R>
        else
            null
    }

    override val columns: List<Column<*>> = delegate.columns.map { it.clone() }

    override val fields: List<Expression<*>> = columns

    override fun createStatement() = throw UnsupportedOperationException("Unsupported for aliases")

    override fun dropStatement() = throw UnsupportedOperationException("Unsupported for aliases")

    override fun modifyStatement() = throw UnsupportedOperationException("Unsupported for aliases")

    override fun equals(other: Any?): Boolean {
        if (other !is Alias<*>) return false
        return this.tableNameWithAlias == other.tableNameWithAlias
    }

    override fun hashCode(): Int = tableNameWithAlias.hashCode()

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Any?> get(original: Column<T>): Column<T> =
        delegate.columns.find { it == original }?.let { it.clone() as? Column<T> }
            ?: error("Column not found in original table")
}

class ExpressionAlias<T>(val delegate: Expression<T>, val alias: String) : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(delegate).append(" $alias") }

    fun aliasOnlyExpression() : Expression<T> {
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

class QueryAlias(val query: Query, val alias: String): ColumnSet() {

    override fun describe(s: Transaction, queryBuilder: QueryBuilder) = queryBuilder{
        append("(")
        query.prepareSQL(queryBuilder)
        append(") ", alias)
    }
    
    override val columns: List<Column<*>>
        get() = query.set.source.columns.filter { it in query.set.fields }.map { it.clone() }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any?> get(original: Column<T>): Column<T> =
        query.set.source.columns.find { it == original }?.clone() as? Column<T>
            ?: error("Column not found in original table")

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any?> get(original: Expression<T>): Expression<T> {
        val expressionAlias = query.set.fields.find { it == original } as? ExpressionAlias<T>
            ?: error("Field not found in original table fields")
        return expressionAlias.delegate.alias("$alias.${expressionAlias.alias}").aliasOnlyExpression()
    }

    override fun join(otherTable: ColumnSet, joinType: JoinType, onColumn: Expression<*>?, otherColumn: Expression<*>?, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? ) : Join =
            Join (this, otherTable, joinType, onColumn, otherColumn, additionalConstraint)

    override infix fun innerJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.LEFT)

    override infix fun rightJoin(otherTable: ColumnSet): Join  = Join (this, otherTable, JoinType.RIGHT)

    override infix fun fullJoin(otherTable: ColumnSet): Join = Join (this, otherTable, JoinType.FULL)

    override infix fun crossJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.CROSS)

    private fun <T:Any?> Column<T>.clone() = Column<T>(table.alias(alias), name, columnType)
}

fun <T:Table> T.alias(alias: String) = Alias(this, alias)
fun <T:Query> T.alias(alias: String) = QueryAlias(this, alias)
fun <T> Expression<T>.alias(alias: String) = ExpressionAlias(this, alias)

fun Join.joinQuery(on: (SqlExpressionBuilder.(QueryAlias)->Op<Boolean>), joinType: JoinType = JoinType.INNER, joinPart: () -> Query): Join {
    val qAlias = joinPart().alias("q${joinParts.count { it.joinPart is QueryAlias }}")
    return join (qAlias, joinType, additionalConstraint =  { on(qAlias) } )
}

fun Table.joinQuery(on: (SqlExpressionBuilder.(QueryAlias)->Op<Boolean>), joinType: JoinType = JoinType.INNER, joinPart: () -> Query)
    = Join(this).joinQuery(on, joinType, joinPart)

val Join.lastQueryAlias: QueryAlias? get() = joinParts.map { it.joinPart as? QueryAlias }.firstOrNull()

fun <T:Any> wrapAsExpression(query: Query) = object : Expression<T?>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("(")
        query.prepareSQL(this)
        append(")")
    }
}
package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.spring.data.mapping.EntityMetadata
import org.jetbrains.exposed.v1.spring.data.mapping.toDbValue
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.ParameterAccessor
import org.springframework.data.repository.query.parser.AbstractQueryCreator
import org.springframework.data.repository.query.parser.Part
import org.springframework.data.repository.query.parser.Part.Type.*
import org.springframework.data.repository.query.parser.PartTree

internal class ExposedQueryCreator(
    tree: PartTree,
    parameterAccessor: ParameterAccessor,
    private val metadata: EntityMetadata<*, *>,
    private val paths: PropertyPathResolver,
    private val columns: List<Column<*>>,
    private val selectSortColumns: Boolean = false,
    private val sortCustomizer: (Sort) -> Sort = { it },
) : AbstractQueryCreator<Query, Op<Boolean>>(tree, parameterAccessor) {
    companion object {
        val SUPPORTED = setOf(
            SIMPLE_PROPERTY,
            NEGATING_SIMPLE_PROPERTY,
            BETWEEN,
            IS_NULL,
            IS_NOT_NULL,
            LESS_THAN,
            LESS_THAN_EQUAL,
            GREATER_THAN,
            GREATER_THAN_EQUAL,
            BEFORE,
            AFTER,
            LIKE,
            NOT_LIKE,
            STARTING_WITH,
            ENDING_WITH,
            CONTAINING,
            NOT_CONTAINING,
            IN,
            NOT_IN,
            REGEX,
            TRUE,
            FALSE,
        )

        /** Part types whose argument is matched as a string and can therefore be compared case-insensitively. */
        private val STRING_TYPES = setOf(
            SIMPLE_PROPERTY,
            NEGATING_SIMPLE_PROPERTY,
            LIKE,
            NOT_LIKE,
            STARTING_WITH,
            ENDING_WITH,
            CONTAINING,
            NOT_CONTAINING,
        )

        private const val ESCAPE_CHAR = '\\'

        internal fun Column<*>.isStringColumn() = columnType is StringColumnType

        internal fun ignoreCase(part: Part, column: Column<*>): Boolean = when (part.shouldIgnoreCase()) {
            Part.IgnoreCaseType.NEVER -> false
            Part.IgnoreCaseType.ALWAYS -> true
            Part.IgnoreCaseType.WHEN_POSSIBLE -> part.type in STRING_TYPES && column.isStringColumn()
        }
    }

    /** The (aliased) column of each sort property of the created query, see [SortedSource]. */
    var sortColumns: Map<String, Column<Any?>> = emptyMap()
        private set

    override fun create(
        part: Part,
        iterator: Iterator<Any>
    ): Op<Boolean> = Conjunction(listOf(predicate(part, iterator)))

    override fun and(
        part: Part,
        base: Op<Boolean>,
        iterator: Iterator<Any>
    ): Op<Boolean> = Conjunction((base as Conjunction).parts + predicate(part, iterator))

    override fun or(
        base: Op<Boolean>,
        criteria: Op<Boolean>
    ): Op<Boolean> = base.combined() or criteria.combined()

    override fun complete(
        criteria: Op<Boolean>?,
        sort: Sort
    ): Query {
        val source = SortedSource(metadata.table, paths, sortCustomizer(sort))
        sortColumns = source.columns
        val selected = if (selectSortColumns) (columns + source.columns.values).distinct() else columns

        return source.select(selected).apply {
            criteria?.let { where(it.combined()) }
        }
    }

    private fun predicate(part: Part, values: Iterator<Any?>): Pair<List<Hop>, Op<Boolean>> {
        val path = paths.resolve(part.property)
        return path.hops to leafPredicate(part, path.leaf, values)
    }

    /**
     * The parts of one `Or` branch. They are combined when the branch is complete, so parts on the same path share one subquery
     * per hop: `findByItemsNameAndItemsQuantity` matches orders with one item that has both the name and the quantity.
     */
    private class Conjunction(val parts: List<Pair<List<Hop>, Op<Boolean>>>) : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = combine(parts).toQueryBuilder(queryBuilder)
    }

    private fun Op<Boolean>.combined(): Op<Boolean> = if (this is Conjunction) combine(parts) else this

    @Suppress("UNCHECKED_CAST", "CyclomaticComplexMethod")
    private fun leafPredicate(part: Part, column: Column<Any?>, values: Iterator<Any?>): Op<Boolean> {
        val ignoreCase = ignoreCase(part, column)
        val expr: ExpressionWithColumnType<Any?> =
            if (ignoreCase) (column as Column<String?>).lowerCase() as ExpressionWithColumnType<Any?> else column

        fun nextValue(): Any? = column.toDbValue(paths.parameterValue(column, values.next())).let { if (ignoreCase && it is String) it.lowercase() else it }
        fun nextString(): String = nextValue() as String
        fun nextCollection(): List<Any?> = when (val value = values.next()) {
            is Iterable<*> -> value.toList()
            is Array<*> -> value.toList()
            else -> listOf(value)
        }.map { column.toDbValue(paths.parameterValue(column, it)) }

        val stringExpr = expr as Expression<String?>
        return when (part.type) {
            SIMPLE_PROPERTY -> expr eq nextValue()
            NEGATING_SIMPLE_PROPERTY -> expr neq nextValue()
            BETWEEN -> Between(expr, column.wrap(nextValue()), column.wrap(nextValue()))
            IS_NULL -> column.isNull()
            IS_NOT_NULL -> column.isNotNull()
            LESS_THAN, BEFORE -> LessOp(expr, column.wrap(nextValue()))
            LESS_THAN_EQUAL -> LessEqOp(expr, column.wrap(nextValue()))
            GREATER_THAN, AFTER -> GreaterOp(expr, column.wrap(nextValue()))
            GREATER_THAN_EQUAL -> GreaterEqOp(expr, column.wrap(nextValue()))
            LIKE -> stringExpr like nextString()
            NOT_LIKE -> stringExpr notLike nextString()
            STARTING_WITH -> stringExpr like (LikePattern.ofLiteral(nextString(), ESCAPE_CHAR) + "%")
            ENDING_WITH -> stringExpr like (LikePattern("%", ESCAPE_CHAR) + LikePattern.ofLiteral(nextString(), ESCAPE_CHAR))
            CONTAINING -> stringExpr like containing(nextString())
            NOT_CONTAINING -> stringExpr notLike containing(nextString())
            IN -> column inList nextCollection()
            NOT_IN -> column notInList nextCollection()
            REGEX -> stringExpr.regexp(stringParam(values.next() as String), caseSensitive = !ignoreCase)
            TRUE -> column eq true
            FALSE -> column eq false
            else -> error("${part.type} is not supported")
        }
    }

    private fun containing(text: String): LikePattern =
        LikePattern("%", ESCAPE_CHAR) + LikePattern.ofLiteral(text, ESCAPE_CHAR) + "%"
}

/**
 * Combines the predicates of [parts] with `AND`, each one built on the last table of its hops: parts with the same first hop
 * are matched in one subquery `parentKey IN (SELECT childKey FROM childTable WHERE ...)`, recursively for the following hops.
 *
 * Subqueries instead of joins keep the query on the repository's table, so every root row is returned at most once,
 * and count, exists, delete and paging work unchanged.
 */
private fun combine(parts: List<Pair<List<Hop>, Op<Boolean>>>): Op<Boolean> {
    val groups = LinkedHashMap<Any, MutableList<Pair<List<Hop>, Op<Boolean>>>>()
    parts.forEach { part -> groups.getOrPut(part.first.firstOrNull() ?: Any()) { mutableListOf() } += part }
    return groups.values.map { group ->
        val hop = group.first().first.firstOrNull() ?: return@map group.single().second
        hop.parentKey inSubQuery hop.childTable.select(hop.childKey).where(combine(group.map { it.first.drop(1) to it.second }))
    }.reduce { acc, op -> acc and op }
}

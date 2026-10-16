package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.ParameterAccessor
import org.springframework.data.repository.query.parser.AbstractQueryCreator
import org.springframework.data.repository.query.parser.Part
import org.springframework.data.repository.query.parser.Part.Type.*
import org.springframework.data.repository.query.parser.PartTree

internal class ExposedQueryCreator(
    tree: PartTree,
    parameterAccessor: ParameterAccessor,
    private val table: IdTable<*>,
    private val mapper: ExposedEntityMapper<*>,
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

        internal fun Query.orderBy(sort: Sort, mapper: ExposedEntityMapper<*>, table: Table): Query = apply {
            sort.forEach { order ->
                val column = requireNotNull(mapper.columnFor(order.property)) {
                    "Unknown sort property '${order.property}' for table ${table.tableName}"
                }
                orderBy(column, if (order.isAscending) SortOrder.ASC else SortOrder.DESC)
            }
        }

        @Suppress("UNCHECKED_CAST")
        internal fun Column<*>.toDbValue(value: Any?): Any? {
            val idColumnType = columnType as? EntityIDColumnType<*> ?: return value
            if (value == null || value is EntityID<*>) return value
            return EntityID(value, idColumnType.idColumn.table as IdTable<Any>)
        }

        internal fun ignoreCase(part: Part, column: Column<*>): Boolean = when (part.shouldIgnoreCase()) {
            Part.IgnoreCaseType.NEVER -> false
            Part.IgnoreCaseType.ALWAYS -> true
            Part.IgnoreCaseType.WHEN_POSSIBLE -> part.type in STRING_TYPES && column.isStringColumn()
        }
    }

    override fun create(
        part: Part,
        iterator: Iterator<Any>
    ): Op<Boolean> = predicate(part, iterator)

    override fun and(
        part: Part,
        base: Op<Boolean>,
        iterator: Iterator<Any>
    ): Op<Boolean> = base and predicate(part, iterator)

    override fun or(
        base: Op<Boolean>,
        criteria: Op<Boolean>
    ): Op<Boolean> = base or criteria

    override fun complete(
        criteria: Op<Boolean>?,
        sort: Sort
    ): Query = table.selectAll().apply {
        criteria?.let { where(it) }
        orderBy(sortCustomizer(sort), mapper, table)
    }

    @Suppress("UNCHECKED_CAST", "CyclomaticComplexMethod")
    private fun predicate(part: Part, values: Iterator<Any?>): Op<Boolean> {
        val column = checkNotNull(mapper.columnFor(part.property.segment)) {
            "No column found for property ${part.property}"
        } as Column<Any?>
        val ignoreCase = ignoreCase(part, column)
        val expr: ExpressionWithColumnType<Any?> =
            if (ignoreCase) (column as Column<String?>).lowerCase() as ExpressionWithColumnType<Any?> else column

        fun nextValue(): Any? = column.toDbValue(values.next()).let { if (ignoreCase && it is String) it.lowercase() else it }
        fun nextString(): String = nextValue() as String
        fun nextCollection(): List<Any?> = when (val value = values.next()) {
            is Iterable<*> -> value.toList()
            is Array<*> -> value.toList()
            else -> listOf(value)
        }.map { column.toDbValue(it) }

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

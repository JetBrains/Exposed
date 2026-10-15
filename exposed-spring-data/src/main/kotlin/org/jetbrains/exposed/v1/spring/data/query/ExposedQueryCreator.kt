package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.ParameterAccessor
import org.springframework.data.repository.query.parser.AbstractQueryCreator
import org.springframework.data.repository.query.parser.Part
import org.springframework.data.repository.query.parser.Part.Type.AFTER
import org.springframework.data.repository.query.parser.Part.Type.CONTAINING
import org.springframework.data.repository.query.parser.Part.Type.GREATER_THAN
import org.springframework.data.repository.query.parser.Part.Type.IS_NULL
import org.springframework.data.repository.query.parser.Part.Type.SIMPLE_PROPERTY
import org.springframework.data.repository.query.parser.PartTree

class ExposedQueryCreator(
    tree: PartTree,
    parameterAccessor: ParameterAccessor? = null,
    private val table: IdTable<*>,
    private val mapper: ExposedEntityMapper<*>,
) : AbstractQueryCreator<Query, Op<Boolean>>(tree, parameterAccessor as ParameterAccessor) {
    companion object {
        val SUPPORTED = setOf(
            SIMPLE_PROPERTY,
            GREATER_THAN,
            AFTER,
            CONTAINING,
            IS_NULL
        )
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
        sort.forEach { order ->
            val column = requireNotNull(mapper.columnFor(order.property)) {
                "Unknown sort property '${order.property}'"
            }
            orderBy(column, if (order.isAscending) SortOrder.ASC else SortOrder.DESC)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun predicate(part: Part, values: Iterator<Any?>): Op<Boolean> {
        val column = checkNotNull(mapper.columnFor(part.property.segment)) {
            "No column found for property ${part.property}"
        }
        val ignoreCase = part.shouldIgnoreCase() != Part.IgnoreCaseType.NEVER
        return when (part.type) {
            SIMPLE_PROPERTY -> (column as Column<Any?>) eq values.next()
            GREATER_THAN, AFTER -> (column as Column<Comparable<Any>>) greater (values.next() as Comparable<Any>)
            CONTAINING -> {
                val raw = values.next() as String
                val text = if (ignoreCase) raw.lowercase() else raw
                val pattern = LikePattern("%", '\\') + LikePattern.ofLiteral(text) + "%"
                val expr = if (ignoreCase) (column as Column<String>).lowerCase() else column as Column<String>
                expr like pattern
            }
            IS_NULL -> column.isNull()
            else -> error("unreachable")
        }
    }
}

package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.ops.SingleValueInListOp
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import org.jetbrains.exposed.v1.spring.data.query.ExposedQueryCreator.Companion.isStringColumn
import org.jetbrains.exposed.v1.spring.data.query.ExposedQueryCreator.Companion.toDbValue
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.data.domain.*
import org.springframework.data.repository.query.ParametersParameterAccessor
import org.springframework.data.repository.query.QueryMethod
import org.springframework.data.repository.query.RepositoryQuery
import org.springframework.data.repository.query.parser.Part
import org.springframework.data.repository.query.parser.PartTree
import org.springframework.data.support.PageableExecutionUtils

@InternalApi
class PartTreeExposedQuery(
    private val method: QueryMethod,
    private val table: IdTable<Any>,
    private val mapper: ExposedEntityMapper<Any>,
) : RepositoryQuery {
    private val tree = PartTree(method.name, method.entityInformation.javaType)
    private val idProperty = checkNotNull(mapper.propertyFor(table.id)) { "No property found for the id column of ${table.tableName}" }

    init {
        tree.parts.forEach { part ->
            require(!part.property.hasNext()) { "Nested paths are not supported: ${part.property.toDotPath()} in $method" }
            val column = requireNotNull(mapper.columnFor(part.property.segment)) {
                "No column found for property ${part.property.segment} in ${table.tableName}"
            }
            require(part.type in ExposedQueryCreator.SUPPORTED) { "${part.type} is not supported yet ($method)" }
            require(part.shouldIgnoreCase() != Part.IgnoreCaseType.ALWAYS || column.isStringColumn()) {
                "IgnoreCase is only supported for string properties, but ${part.property.segment} is not ($method)"
            }
        }
    }

    override fun execute(parameters: Array<out Any?>): Any? {
        val accessor = ParametersParameterAccessor(method.parameters, parameters)
        if (method.isScrollQuery) return scroll(accessor)
        val query = ExposedQueryCreator(tree, accessor, table, mapper).createQuery()
        if (tree.isDistinct) query.withDistinct()

        return when {
            tree.isCountProjection -> query.count()
            tree.isExistsProjection -> !query.empty()
            tree.isDelete -> delete(query)
            method.isPageQuery -> {
                val pageable = accessor.pageable
                val countQuery = query.copy()
                val content = query.paged(pageable).map(mapper::toEntity)
                PageableExecutionUtils.getPage(content, pageable, countQuery::count)
            }
            method.isSliceQuery -> {
                val pageable = accessor.pageable
                if (pageable.isUnpaged) {
                    SliceImpl(query.map(mapper::toEntity), pageable, false)
                } else {
                    val rows = query.limit(pageable.pageSize + 1).offset(pageable.offset).map(mapper::toEntity)
                    SliceImpl(rows.take(pageable.pageSize), pageable, rows.size > pageable.pageSize)
                }
            }
            method.isCollectionQuery || method.isStreamQuery -> {
                val result = query.paged(accessor.pageable).limited(accessor).map(mapper::toEntity)
                if (method.isStreamQuery) result.stream() else result
            }
            else -> {
                val rows = query.limit(if (tree.isLimiting) tree.maxResults!! else 2).toList()
                if (rows.size > 1) throw IncorrectResultSizeDataAccessException(1, rows.size)
                rows.singleOrNull()?.let(mapper::toEntity)
            }
        }
    }

    override fun getQueryMethod(): QueryMethod = method

    /** Deletes the matching rows and returns them if the method returns a collection, otherwise their number. */
    private fun delete(query: Query): Any {
        val where = query.where ?: Op.TRUE
        if (!method.isCollectionQuery) {
            return table.deleteWhere { where }.toLong()
        }
        val rows = query.toList()
        val ids = rows.map { it[table.id] }
        if (ids.isNotEmpty()) table.deleteWhere { SingleValueInListOp(table.id, ids, isInList = true) }
        return rows.map(mapper::toEntity)
    }

    private fun Query.limited(accessor: ParametersParameterAccessor) = apply {
        maxResults(accessor)?.let { limit(it) }
    }

    private fun maxResults(accessor: ParametersParameterAccessor): Int? = listOfNotNull(
        tree.maxResults?.takeIf { tree.isLimiting },
        accessor.limit.takeIf { it.isLimited }?.max(),
    ).minOrNull()

    private fun scroll(accessor: ParametersParameterAccessor): Window<Any> {
        val limit = maxResults(accessor)
        return when (val position = accessor.scrollPosition ?: ScrollPosition.offset()) {
            is OffsetScrollPosition -> offsetScroll(accessor, position, limit)
            is KeysetScrollPosition -> keysetScroll(accessor, position, limit)
            else -> throw UnsupportedOperationException("Unsupported scroll position ${position::class.qualifiedName}")
        }
    }

    private fun offsetScroll(accessor: ParametersParameterAccessor, position: OffsetScrollPosition, limit: Int?): Window<Any> {
        val start = if (position.isInitial) 0L else position.offset + 1
        val query = ExposedQueryCreator(tree, accessor, table, mapper).createQuery()
        if (tree.isDistinct) query.withDistinct()
        if (start > 0) query.offset(start)
        limit?.let { query.limit(it + 1) }
        val rows = query.map(mapper::toEntity)
        val content = if (limit != null) rows.take(limit) else rows
        return Window.from(content, { index -> ScrollPosition.offset(start + index) }, limit != null && rows.size > limit)
    }

    private fun keysetScroll(accessor: ParametersParameterAccessor, position: KeysetScrollPosition, limit: Int?): Window<Any> {
        val backward = position.scrollsBackward()
        var sort = Sort.unsorted()
        val query = ExposedQueryCreator(tree, accessor, table, mapper) { declared ->
            sort = if (declared.getOrderFor(idProperty) == null) declared.and(Sort.by(idProperty)) else declared
            if (backward) Sort.by(sort.map { it.reverse() }.toList()) else sort
        }.createQuery()
        if (tree.isDistinct) query.withDistinct()
        if (!position.isInitial) query.andWhere { keysetPredicate(sort, position) }
        limit?.let { query.limit(it + 1) }

        val fetched = query.toList()
        val rows = (if (limit != null) fetched.take(limit) else fetched).let { if (backward) it.reversed() else it }
        val keys = rows.map { row -> keysOf(row, sort) }
        return Window.from(
            rows.map(mapper::toEntity),
            { index -> ScrollPosition.of(keys[index], position.direction) },
            limit != null && fetched.size > limit,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun keysetPredicate(sort: Sort, position: KeysetScrollPosition): Op<Boolean> {
        val orders = sort.toList()
        val keys = position.keys
        fun columnAndValue(order: Sort.Order): Pair<Column<Any?>, Any?> {
            require(keys.containsKey(order.property)) { "Keyset position $position has no key for sort property '${order.property}'" }
            val column = mapper.columnFor(order.property) as Column<Any?>
            return column to column.toDbValue(keys[order.property])
        }
        return orders.indices.map { i ->
            val equalities = orders.take(i).map { order ->
                val (column, value) = columnAndValue(order)
                column eq value
            }
            val (column, value) = columnAndValue(orders[i])
            checkNotNull(value) { "Keyset scrolling over null values is not supported (property '${orders[i].property}')" }
            val greater = orders[i].isAscending != position.scrollsBackward()
            val comparison: Op<Boolean> = if (greater) GreaterOp(column, column.wrap(value)) else LessOp(column, column.wrap(value))
            (equalities + comparison).reduce { acc, op -> acc and op }
        }.reduce { acc, op -> acc or op }
    }

    private fun keysOf(row: ResultRow, sort: Sort): Map<String, Any?> = sort.associate { order ->
        val value = row[mapper.columnFor(order.property)!!]
        order.property to if (value is EntityID<*>) value.value else value
    }

    private fun Query.paged(p: Pageable) = apply { if (p.isPaged) limit(p.pageSize).offset(p.offset) }
}

package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.ops.SingleValueInListOp
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.spring.data.mapping.ProjectionResolver
import org.jetbrains.exposed.v1.spring.data.mapping.RowConverter
import org.jetbrains.exposed.v1.spring.data.mapping.nullsSortHigh
import org.jetbrains.exposed.v1.spring.data.mapping.toDbValue
import org.jetbrains.exposed.v1.spring.data.mapping.unwrapEntityId
import org.jetbrains.exposed.v1.spring.data.query.ExposedQueryCreator.Companion.isStringColumn
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.data.domain.*
import org.springframework.data.repository.query.ParametersParameterAccessor
import org.springframework.data.repository.query.QueryMethod
import org.springframework.data.repository.query.RepositoryQuery
import org.springframework.data.repository.query.parser.Part
import org.springframework.data.repository.query.parser.PartTree
import org.springframework.data.support.PageableExecutionUtils

internal class PartTreeExposedQuery<T : Any, ID : Any>(
    private val method: QueryMethod,
    private val projectionResolver: ProjectionResolver<T, ID>,
) : RepositoryQuery {
    private val metadata = projectionResolver.metadata
    private val table = metadata.table
    private val tree = PartTree(method.name, method.entityInformation.javaType)

    init {
        tree.parts.forEach { part ->
            require(!part.property.hasNext()) { "Nested paths are not supported: ${part.property.toDotPath()} in $method" }
            val column = metadata.requireColumn(part.property.segment)
            require(part.type in ExposedQueryCreator.SUPPORTED) { "${part.type} is not supported yet ($method)" }
            require(part.shouldIgnoreCase() != Part.IgnoreCaseType.ALWAYS || column.isStringColumn()) {
                "IgnoreCase is only supported for string properties, but ${part.property.segment} is not ($method)"
            }
        }
    }

    private val staticConverter: RowConverter<*>? = when {
        tree.isCountProjection || tree.isExistsProjection || tree.isDelete -> projectionResolver.entityConverter
        method.parameters.hasDynamicProjection() -> null
        else -> projectionResolver.converterFor(method.returnedObjectType)
    }

    override fun execute(parameters: Array<out Any?>): Any? {
        val accessor = ParametersParameterAccessor(method.parameters, parameters)
        val converter = converter(accessor)
        if (method.isScrollQuery) return scroll(accessor, converter)
        val query = ExposedQueryCreator(tree, accessor, metadata, converter.columns).createQuery()
        if (tree.isDistinct) query.withDistinct()

        return when {
            tree.isCountProjection -> query.count()
            tree.isExistsProjection -> !query.empty()
            tree.isDelete -> delete(query, converter)
            method.isPageQuery -> {
                val pageable = accessor.pageable
                val countQuery = query.copy()
                val content = query.paged(pageable).map(converter::convert)
                PageableExecutionUtils.getPage(content, pageable, countQuery::count)
            }
            method.isSliceQuery -> {
                val pageable = accessor.pageable
                if (pageable.isUnpaged) {
                    SliceImpl(query.map(converter::convert), pageable, false)
                } else {
                    val rows = query.limit(pageable.pageSize + 1).offset(pageable.offset).map(converter::convert)
                    SliceImpl(rows.take(pageable.pageSize), pageable, rows.size > pageable.pageSize)
                }
            }
            method.isCollectionQuery || method.isStreamQuery -> {
                val result = query.paged(accessor.pageable).limited(accessor).map(converter::convert)
                if (method.isStreamQuery) result.stream() else result
            }
            else -> {
                val rows = query.limit(if (tree.isLimiting) tree.maxResults!! else 2).toList()
                if (rows.size > 1) throw IncorrectResultSizeDataAccessException(1, rows.size)
                rows.singleOrNull()?.let(converter::convert)
            }
        }
    }

    override fun getQueryMethod(): QueryMethod = method

    /** Deletes the matching rows and returns them if the method returns a collection, otherwise their number. */
    private fun delete(query: Query, converter: RowConverter<*>): Any {
        val where = query.where ?: Op.TRUE
        if (!method.isCollectionQuery) {
            return table.deleteWhere { where }.toLong()
        }
        val rows = query.toList()
        val ids = rows.map { it[table.id] }
        if (ids.isNotEmpty()) table.deleteWhere { SingleValueInListOp(table.id, ids, isInList = true) }
        return rows.map(converter::convert)
    }

    private fun Query.limited(accessor: ParametersParameterAccessor) = apply {
        maxResults(accessor)?.let { limit(it) }
    }

    private fun maxResults(accessor: ParametersParameterAccessor): Int? = listOfNotNull(
        tree.maxResults?.takeIf { tree.isLimiting },
        accessor.limit.takeIf { it.isLimited }?.max(),
    ).minOrNull()

    private fun scroll(accessor: ParametersParameterAccessor, converter: RowConverter<*>): Window<Any> {
        val limit = maxResults(accessor)
        return when (val position = accessor.scrollPosition ?: ScrollPosition.offset()) {
            is OffsetScrollPosition -> offsetScroll(accessor, converter, position, limit)
            is KeysetScrollPosition -> keysetScroll(accessor, converter, position, limit)
            else -> throw UnsupportedOperationException("Unsupported scroll position ${position::class.qualifiedName}")
        }
    }

    private fun offsetScroll(accessor: ParametersParameterAccessor, converter: RowConverter<*>, position: OffsetScrollPosition, limit: Int?): Window<Any> {
        val start = if (position.isInitial) 0L else position.offset + 1
        val query = ExposedQueryCreator(tree, accessor, metadata, converter.columns).createQuery()
        if (tree.isDistinct) query.withDistinct()
        if (start > 0) query.offset(start)
        limit?.let { query.limit(it + 1) }
        val rows = query.map(converter::convert)
        val content = if (limit != null) rows.take(limit) else rows
        return Window.from(content, { index -> ScrollPosition.offset(start + index) }, limit != null && rows.size > limit)
    }

    private fun keysetScroll(accessor: ParametersParameterAccessor, converter: RowConverter<*>, position: KeysetScrollPosition, limit: Int?): Window<Any> {
        val backward = position.scrollsBackward()
        val idProperty = metadata.idProperty
        val nullsHigh = nullsSortHigh()
        var sort = Sort.unsorted()
        var effectiveSort = Sort.unsorted()
        val query = ExposedQueryCreator(tree, accessor, metadata, converter.columns, selectSortColumns = true) { declared ->
            val withId = if (declared.getOrderFor(idProperty) == null) declared.and(Sort.by(idProperty)) else declared
            // NULL positions are made explicit, so the keyset predicate knows on which side of the other values they are
            sort = Sort.by(withId.map { it.withExplicitNullHandling(nullsHigh) }.toList())
            effectiveSort = if (backward) Sort.by(sort.map { it.reversedWithNulls() }.toList()) else sort
            effectiveSort
        }.createQuery()
        if (tree.isDistinct) query.withDistinct()
        if (!position.isInitial) query.andWhere { keysetPredicate(effectiveSort, position) }
        limit?.let { query.limit(it + 1) }

        val fetched = query.toList()
        val rows = (if (limit != null) fetched.take(limit) else fetched).let { if (backward) it.reversed() else it }
        val keys = rows.map { row -> keysOf(row, sort) }
        return Window.from(
            rows.map(converter::convert),
            { index -> ScrollPosition.of(keys[index], position.direction) },
            limit != null && fetched.size > limit,
        )
    }

    /** Pins the position of `NULL`s for nullable columns to what the database does natively, unless the order already sets it. */
    private fun Sort.Order.withExplicitNullHandling(nullsHigh: Boolean): Sort.Order {
        // unknown properties are left as they are, the ORDER BY reports them
        if (nullHandling != Sort.NullHandling.NATIVE || metadata.columnFor(property)?.columnType?.nullable != true) return this
        return if (isAscending != nullsHigh) nullsFirst() else nullsLast()
    }

    /** Reverses the direction and the position of `NULL`s, so the order is exactly the opposite. */
    private fun Sort.Order.reversedWithNulls(): Sort.Order = reverse().with(
        when (nullHandling) {
            Sort.NullHandling.NULLS_FIRST -> Sort.NullHandling.NULLS_LAST
            Sort.NullHandling.NULLS_LAST -> Sort.NullHandling.NULLS_FIRST
            else -> nullHandling
        }
    )

    /**
     * Matches the rows that come strictly after the position's keys in [sort], the order the query actually uses
     * (already reversed when scrolling backward): `(a > ?) OR (a = ? AND b > ?) OR ...`, with explicit handling of `NULL`.
     */
    private fun keysetPredicate(sort: Sort, position: KeysetScrollPosition): Op<Boolean> {
        val orders = sort.toList()
        val keys = position.keys
        fun columnAndValue(order: Sort.Order): Pair<Column<Any?>, Any?> {
            require(keys.containsKey(order.property)) { "Keyset position $position has no key for sort property '${order.property}'" }
            val column = metadata.requireColumn(order.property)
            return column to column.toDbValue(keys[order.property])
        }
        return orders.indices.map { i ->
            val equalities = orders.take(i).map { order ->
                val (column, value) = columnAndValue(order)
                if (value == null) column.isNull() else column eq value
            }
            val (column, value) = columnAndValue(orders[i])
            (equalities + after(orders[i], column, value)).reduce { acc, op -> acc and op }
        }.reduce { acc, op -> acc or op }
    }

    /** Matches the values of [column] that come strictly after [value] in [order]. */
    private fun after(order: Sort.Order, column: Column<Any?>, value: Any?): Op<Boolean> {
        val nullsFirst = order.nullHandling == Sort.NullHandling.NULLS_FIRST
        if (value == null) return if (nullsFirst) column.isNotNull() else Op.FALSE
        val comparison: Op<Boolean> = if (order.isAscending) GreaterOp(column, column.wrap(value)) else LessOp(column, column.wrap(value))
        // with NULLS LAST, the NULLs come after every value; SQL comparisons never match NULL, so add them explicitly
        return if (order.nullHandling == Sort.NullHandling.NULLS_LAST) comparison or column.isNull() else comparison
    }

    private fun keysOf(row: ResultRow, sort: Sort): Map<String, Any?> = sort.associate { order ->
        order.property to row[metadata.requireColumn(order.property)].unwrapEntityId()
    }

    private fun Query.paged(p: Pageable) = apply { if (p.isPaged) limit(p.pageSize).offset(p.offset) }

    private fun converter(accessor: ParametersParameterAccessor): RowConverter<*> =
        staticConverter ?: projectionResolver.converterFor(
            method.resultProcessor.withDynamicProjection(accessor).returnedType.returnedType
        )
}

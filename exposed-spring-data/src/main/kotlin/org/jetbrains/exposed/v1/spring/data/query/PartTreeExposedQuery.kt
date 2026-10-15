package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.ops.SingleValueInListOp
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import org.jetbrains.exposed.v1.spring.data.query.ExposedQueryCreator.Companion.isStringColumn
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.repository.query.ParametersParameterAccessor
import org.springframework.data.repository.query.QueryMethod
import org.springframework.data.repository.query.RepositoryQuery
import org.springframework.data.repository.query.parser.Part
import org.springframework.data.repository.query.parser.PartTree
import org.springframework.data.support.PageableExecutionUtils

class PartTreeExposedQuery(
    private val method: QueryMethod,
    private val table: IdTable<Any>,
    private val mapper: ExposedEntityMapper<Any>,
) : RepositoryQuery {
    private val tree = PartTree(method.name, method.entityInformation.javaType)

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
        val query = ExposedQueryCreator(tree, accessor, table, mapper).createQuery()
        if (tree.isDistinct) query.withDistinct()

        return when {
            tree.isCountProjection -> query.count()
            tree.isExistsProjection -> !query.empty()
            tree.isDelete -> delete(query)
            method.isPageQuery -> {
                val pageable = accessor.pageable
                val countQuery = query.copy() // copy before limit/offset, otherwise count() counts the page
                val content = query.paged(pageable).map(mapper::toEntity)
                PageableExecutionUtils.getPage(content, pageable, countQuery::count)
            }
            method.isSliceQuery -> {
                val pageable = accessor.pageable
                if (pageable.isUnpaged) {
                    SliceImpl(query.map(mapper::toEntity), pageable, false)
                } else {
                    // fetch one extra row to find out whether there is a next slice
                    val rows = query.limit(pageable.pageSize + 1).offset(pageable.offset).map(mapper::toEntity)
                    SliceImpl(rows.take(pageable.pageSize), pageable, rows.size > pageable.pageSize)
                }
            }
            method.isCollectionQuery || method.isStreamQuery -> {
                val result = query.paged(accessor.pageable).limited(accessor).map(mapper::toEntity)
                if (method.isStreamQuery) result.stream() else result
            }
            else -> {
                // limit(2) is enough to detect "more than one result"
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

    /** Applies the limit from `First`/`Top` and from a [org.springframework.data.domain.Limit] parameter. */
    private fun Query.limited(accessor: ParametersParameterAccessor) = apply {
        val limits = listOfNotNull(
            tree.maxResults?.takeIf { tree.isLimiting },
            accessor.limit.takeIf { it.isLimited }?.max(),
        )
        limits.minOrNull()?.let { limit(it) }
    }

    private fun Query.paged(p: Pageable) = apply { if (p.isPaged) limit(p.pageSize).offset(p.offset) }
}

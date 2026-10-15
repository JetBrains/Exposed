package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.ParametersParameterAccessor
import org.springframework.data.repository.query.QueryMethod
import org.springframework.data.repository.query.RepositoryQuery
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
            requireNotNull(mapper.columnFor(part.property.segment)) { "No column found for property ${part.property.segment} in ${table.tableName}" }
            require(part.type in ExposedQueryCreator.SUPPORTED) { "${part.type} is not supported yet ($method)" }
        }
    }

    override fun execute(parameters: Array<out Any?>): Any? {
        val accessor = ParametersParameterAccessor(method.parameters, parameters)
        val query = ExposedQueryCreator(tree, accessor, table, mapper).createQuery()
        if (tree.isDistinct) query.withDistinct()

        return when {
            tree.isCountProjection -> query.count()
            tree.isExistsProjection -> !query.empty()
            tree.isDelete -> table.deleteWhere { query.where ?: Op.TRUE }.toLong()
            method.isPageQuery -> {
                val pageable = accessor.pageable
                val countQuery = query.copy()
                val content = query.paged(pageable).map(mapper::toEntity)
                PageableExecutionUtils.getPage(content, pageable, countQuery::count)
            }
            method.isCollectionQuery -> query.paged(accessor.pageable).limited().map(mapper::toEntity)
            else -> query.limit(2).toList().let { rows ->
                if (rows.size > 1) throw IncorrectResultSizeDataAccessException(1, rows.size)
                rows.singleOrNull()?.let(mapper::toEntity)
            }
        }
    }

    override fun getQueryMethod(): QueryMethod = method

    private fun Query.limited() = apply { if (tree.isLimiting) limit(tree.maxResults!!) }
    private fun Query.paged(p: Pageable) = apply { if (p.isPaged) limit(p.pageSize).offset(p.offset) }
}

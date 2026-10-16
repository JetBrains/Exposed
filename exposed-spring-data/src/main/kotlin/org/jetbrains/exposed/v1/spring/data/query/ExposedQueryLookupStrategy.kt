package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import org.springframework.data.projection.ProjectionFactory
import org.springframework.data.repository.core.NamedQueries
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.query.DefaultParameters
import org.springframework.data.repository.query.QueryLookupStrategy
import org.springframework.data.repository.query.QueryMethod
import org.springframework.data.repository.query.RepositoryQuery
import java.lang.reflect.Method

internal class ExposedQueryLookupStrategy(
    private val tableFor: (RepositoryMetadata) -> IdTable<Any>,
    private val mapperFor: (RepositoryMetadata) -> ExposedEntityMapper<Any>,
) : QueryLookupStrategy {
    override fun resolveQuery(
        method: Method,
        metadata: RepositoryMetadata,
        factory: ProjectionFactory,
        namedQueries: NamedQueries
    ): RepositoryQuery {
        val queryMethod = QueryMethod(method, metadata, factory) { DefaultParameters(it) }
        return PartTreeExposedQuery(queryMethod, tableFor(metadata), mapperFor(metadata))
    }
}

package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.jetbrains.exposed.v1.spring.data.mapping.EntityMetadata
import org.jetbrains.exposed.v1.spring.data.mapping.ProjectionResolver
import org.jetbrains.exposed.v1.spring.data.query.ExposedQueryLookupStrategy
import org.jetbrains.exposed.v1.spring.data.query.QueryContextInterceptor
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.core.EntityInformation
import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.query.QueryLookupStrategy
import org.springframework.data.repository.query.ValueExpressionDelegate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

internal class ExposedRepositoryFactory : RepositoryFactorySupport() {
    private val projectionResolvers = ConcurrentHashMap<Class<*>, ProjectionResolver<Any, Any>>()

    init {
        addRepositoryProxyPostProcessor { proxyFactory, information ->
            if (information.repositoryInterface.methods.any { it.isDefault }) {
                proxyFactory.addAdvice(QueryContextInterceptor(projectionResolverFor(information)))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getTargetRepository(metadata: RepositoryInformation): Any = SimpleExposedRepository(
        entityInformation = getEntityInformation(metadata) as EntityInformation<Any, Any>,
        projectionResolver = projectionResolverFor(metadata),
    )

    override fun getRepositoryBaseClass(metadata: RepositoryMetadata): Class<*> = SimpleExposedRepository::class.java

    override fun getEntityInformation(metadata: RepositoryMetadata): EntityInformation<*, *> =
        if (Persistable::class.java.isAssignableFrom(metadata.domainType)) {
            PersistableEntityInformation<Persistable<Any>, Any>(metadata)
        } else {
            ExposedEntityInformation(metadata, projectionResolverFor(metadata).metadata)
        }

    override fun getQueryLookupStrategy(key: QueryLookupStrategy.Key?, valueExpressionDelegate: ValueExpressionDelegate): Optional<QueryLookupStrategy> =
        Optional.of(ExposedQueryLookupStrategy(::projectionResolverFor))

    /** One resolver per repository interface, shared by the base repository, derived queries and `query { }`. */
    @Suppress("UNCHECKED_CAST")
    private fun projectionResolverFor(metadata: RepositoryMetadata): ProjectionResolver<Any, Any> =
        projectionResolvers.getOrPut(metadata.repositoryInterface) {
            ProjectionResolver(
                metadata = EntityMetadata(tableFor(metadata), metadata.domainType.kotlin as KClass<Any>),
                factory = projectionFactory,
            )
        }

    @Suppress("UNCHECKED_CAST")
    private fun tableFor(metadata: RepositoryMetadata): IdTable<Any> {
        val annotation = checkNotNull(metadata.repositoryInterface.getAnnotation(ExposedRepository::class.java)) {
            "@ExposedRepository not found on ${metadata.repositoryInterface.name}"
        }
        return checkNotNull(annotation.table.objectInstance) { "${annotation.table} must be an object" } as IdTable<Any>
    }
}

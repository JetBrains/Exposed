package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.jetbrains.exposed.v1.spring.data.mapping.EntityMetadata
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedPersistentEntity
import org.jetbrains.exposed.v1.spring.data.mapping.ProjectionResolver
import org.jetbrains.exposed.v1.spring.data.query.ExposedQueryLookupStrategy
import org.jetbrains.exposed.v1.spring.data.query.QueryContextInterceptor
import org.springframework.data.repository.core.EntityInformation
import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.PersistentEntityInformation
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.query.QueryLookupStrategy
import org.springframework.data.repository.query.ValueExpressionDelegate
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class ExposedRepositoryFactory(
    private val mappingContext: ExposedMappingContext = ExposedMappingContext(),
) : RepositoryFactorySupport() {
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
        PersistentEntityInformation<Any, Any>(projectionResolverFor(metadata).metadata.persistentEntity)

    override fun getQueryLookupStrategy(key: QueryLookupStrategy.Key?, valueExpressionDelegate: ValueExpressionDelegate): Optional<QueryLookupStrategy> =
        Optional.of(ExposedQueryLookupStrategy(::projectionResolverFor))

    @Suppress("UNCHECKED_CAST")
    private fun projectionResolverFor(metadata: RepositoryMetadata): ProjectionResolver<Any, Any> =
        projectionResolvers.getOrPut(metadata.repositoryInterface) {
            ProjectionResolver(
                metadata = EntityMetadata(
                    table = tableFor(metadata),
                    persistentEntity = mappingContext.getRequiredPersistentEntity(metadata.domainType) as ExposedPersistentEntity<Any>,
                ),
                factory = projectionFactory,
                instantiators = mappingContext.instantiators,
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

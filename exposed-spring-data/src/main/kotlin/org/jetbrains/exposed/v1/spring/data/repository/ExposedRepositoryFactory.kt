package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.jetbrains.exposed.v1.spring.data.mapping.ReflectionEntityMapper
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

@InternalApi
class ExposedRepositoryFactory : RepositoryFactorySupport() {
    private val mappers = ConcurrentHashMap<Class<*>, ReflectionEntityMapper<Any, Any>>()

    init {
        addRepositoryProxyPostProcessor { proxyFactory, information ->
            if (information.repositoryInterface.methods.any { it.isDefault }) {
                proxyFactory.addAdvice(QueryContextInterceptor(tableFor(information), mapperFor(information)))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getTargetRepository(metadata: RepositoryInformation): Any {
        val table = tableFor(metadata)
        val entityInformation = getEntityInformation(metadata) as ExposedEntityInformation<Any, Any>
        val entityMapper = mapperFor(metadata)

        return SimpleExposedRepository(
            table = table,
            entityInformation = entityInformation,
            entityMapper = entityMapper,
        )
    }

    override fun getRepositoryBaseClass(metadata: RepositoryMetadata): Class<*> {
        return SimpleExposedRepository::class.java
    }

    override fun getEntityInformation(metadata: RepositoryMetadata): EntityInformation<*, *> {
        if (Persistable::class.java.isAssignableFrom(metadata.domainType)) {
            TODO("Persistable Support not implemented")
        }
        return ExposedEntityInformation<Any, Any>(metadata)
    }

    override fun getQueryLookupStrategy(key: QueryLookupStrategy.Key?, valueExpressionDelegate: ValueExpressionDelegate): Optional<QueryLookupStrategy> {
        return Optional.of(ExposedQueryLookupStrategy(::tableFor, ::mapperFor))
    }

    @Suppress("UNCHECKED_CAST")
    private fun tableFor(metadata: RepositoryMetadata): IdTable<Any> {
        val annotation = checkNotNull(metadata.repositoryInterface.getAnnotation(ExposedRepository::class.java)) {
            "@ExposedRepository not found on ${metadata.repositoryInterface.name}"
        }
        return checkNotNull(annotation.table.objectInstance) { "${annotation.table} must be an object" } as IdTable<Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapperFor(metadata: RepositoryMetadata) = mappers.getOrPut(metadata.repositoryInterface) {
        ReflectionEntityMapper(tableFor(metadata), metadata.domainType.kotlin as KClass<Any>)
    }
}


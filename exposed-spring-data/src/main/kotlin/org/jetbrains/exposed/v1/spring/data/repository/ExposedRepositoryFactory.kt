package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.jetbrains.exposed.v1.spring.data.mapping.ReflectionEntityMapper
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.core.EntityInformation
import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.query.QueryLookupStrategy
import org.springframework.data.repository.query.ValueExpressionDelegate
import java.util.*
import kotlin.reflect.KClass

class ExposedRepositoryFactory : RepositoryFactorySupport() {
    @Suppress("UNCHECKED_CAST")
    override fun getTargetRepository(metadata: RepositoryInformation): Any {
        val exposedRepositoryAnnotation = checkNotNull(metadata.repositoryInterface.annotations.singleOrNull { it.annotationClass == ExposedRepository::class }) {
            "ExposedRepository annotation not found on domain type ${metadata.repositoryName}"
        } as ExposedRepository

        val table = exposedRepositoryAnnotation.table.objectInstance as IdTable<Any>
        val entityInformation = getEntityInformation(metadata) as ExposedEntityInformation<Any, Any>
        val entityMapper = ReflectionEntityMapper(table, metadata.domainType.kotlin as KClass<Any>)

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
        // TODO
        return Optional.empty()
    }
}


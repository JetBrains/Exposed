package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.springframework.data.mapping.model.EntityInstantiators
import org.springframework.data.projection.ProjectionFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * The single entry point for mapping between rows and objects of one repository.
 *
 * - [entityConverter] reads rows as the entity, [fill] writes an entity into an insert or update statement.
 * - [converterFor] reads rows as any supported type: the entity, a DTO, a closed interface projection or [ResultRow].
 * - [metadata] gives the property to column mapping, for building queries.
 */
internal class ProjectionResolver<T : Any, ID : Any>(
    val metadata: EntityMetadata<T, ID>,
    private val factory: ProjectionFactory,
    instantiators: EntityInstantiators,
) {
    val entityConverter: RowConverter<T> = EntityConverter(metadata, instantiators)

    private val writtenProperties = metadata.mappedProperties.filterValues { it != metadata.table.id && it != metadata.versionColumn }

    private val converters = ConcurrentHashMap<Class<*>, RowConverter<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> converterFor(type: Class<R>): RowConverter<R> = converters.getOrPut(type) { create(type) } as RowConverter<R>

    fun fill(statement: UpdateBuilder<*>, entity: T) {
        writtenProperties.forEach { (property, column) -> statement[column] = metadata.valueOf(entity, property) }
    }

    private fun <R : Any> create(type: Class<R>): RowConverter<*> = when {
        type == ResultRow::class.java -> RawRowConverter(metadata.table.columns)
        type.isAssignableFrom(metadata.domainType.java) -> entityConverter
        type.isPrimitive || type.isArray || type.name.startsWith("java.") || type.name.startsWith("kotlin.") ->
            error("Rows can't be converted to ${type.name}, map the rows yourself (e.g. `.map { it[column] }`)")
        type.isInterface -> InterfaceConverter(type, factory, metadata)
        else -> ConstructorConverter(type.kotlin, metadata)
    }
}

package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.spring.data.event.EntityLifecycle
import org.jetbrains.exposed.v1.spring.data.query.PropertyPathResolver
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.mapping.model.EntityInstantiators
import org.springframework.data.projection.ProjectionFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * The single entry point for mapping between rows and objects of one repository.
 *
 * - [entityConverter] reads rows as the entity and runs the `AfterConvert` step of the [lifecycle] for each of them,
 *   [fillInsert] writes an entity into an insert statement, [fillUpdate] into an update statement.
 * - with [keepCreatedOnUpdate] (auditing is enabled), updates don't write the `@CreatedDate` and `@CreatedBy` columns,
 *   so the stored created values are kept, also if the passed entity doesn't carry them.
 * - [converterFor] reads rows as any supported type: the entity, a DTO, a closed interface projection or [ResultRow].
 *   Referenced rows and to-many rows are only loaded if the type has a property for them, see [readerFor].
 * - [writer] saves referenced entities and owned to-many rows, [paths] resolves property paths of queries and sorts.
 * - [metadata] gives the property to column mapping, for building queries.
 */
internal class ProjectionResolver<T : Any, ID : Any>(
    val metadata: EntityMetadata<T, ID>,
    private val factory: ProjectionFactory,
    instantiators: EntityInstantiators,
    val lifecycle: EntityLifecycle<T> = EntityLifecycle(metadata.table),
    keepCreatedOnUpdate: Boolean = false,
    val mappings: ExposedMappings = ExposedMappings(projectionFactory = factory),
) {
    private val source = EntitySource(metadata, mappings)

    val entityConverter: RowConverter<T> = EntityConverter(metadata, instantiators, mappings, lifecycle::afterConvert)

    val writer: AggregateWriter by lazy { mappings.writer(metadata.table, metadata.domainType.java) }

    val paths = PropertyPathResolver(metadata, mappings)

    private val writtenProperties = metadata.mappedProperties.filterValues { it != metadata.table.id && it != metadata.versionColumn }

    private val updatedProperties = if (keepCreatedOnUpdate) {
        writtenProperties.filterKeys { !metadata.isCreatedAuditProperty(it) }
    } else {
        writtenProperties
    }

    private val converters = ConcurrentHashMap<Class<*>, RowConverter<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> converterFor(type: Class<R>): RowConverter<R> = converters.getOrPut(type) { create(type) } as RowConverter<R>

    // reference columns hold EntityIDs, the entity property the plain id value (or the referenced object)
    fun fillInsert(statement: UpdateBuilder<*>, entity: T) {
        writtenProperties.forEach { (property, column) -> statement[column] = column.toDbValue(writer.dbValue(property, metadata.valueOf(entity, property))) }
    }

    fun fillUpdate(statement: UpdateBuilder<*>, entity: T) {
        updatedProperties.forEach { (property, column) -> statement[column] = column.toDbValue(writer.dbValue(property, metadata.valueOf(entity, property))) }
    }

    private fun <R : Any> create(type: Class<R>): RowConverter<*> = when {
        type == ResultRow::class.java -> RawRowConverter(metadata.table.columns)
        type.isAssignableFrom(metadata.domainType.java) -> entityConverter
        type.isPrimitive || type.isArray || type.name.startsWith("java.") || type.name.startsWith("kotlin.") ->
            error("Rows can't be converted to ${type.name}, map the rows yourself (e.g. `.map { it[column] }`)")
        type.isInterface -> InterfaceConverter(type, factory, source)
        else -> ConstructorConverter(type.kotlin, source)
    }
}

private fun EntityMetadata<*, *>.isCreatedAuditProperty(property: String): Boolean =
    persistentEntity.getPersistentProperty(property)
        ?.let { it.isAnnotationPresent(CreatedDate::class.java) || it.isAnnotationPresent(CreatedBy::class.java) } == true

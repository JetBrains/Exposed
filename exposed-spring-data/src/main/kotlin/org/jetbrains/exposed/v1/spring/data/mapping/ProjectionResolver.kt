package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.springframework.data.projection.ProjectionFactory
import java.util.concurrent.ConcurrentHashMap

internal class ProjectionResolver(
    private val table: IdTable<*>,
    private val mapper: ExposedEntityMapper<Any>,
    private val entityType: Class<*>,
    private val factory: ProjectionFactory,
) {
    private val cache = ConcurrentHashMap<Class<*>, RowConverter>()

    fun converterFor(type: Class<*>): RowConverter = cache.getOrPut(type) { create(type) }

    private fun create(type: Class<*>): RowConverter = when {
        type == ResultRow::class.java -> RawRowConverter(table.columns)
        type.isAssignableFrom(entityType) -> EntityConverter(mapper)
        type.isPrimitive || type.isArray || type.name.startsWith("java.") || type.name.startsWith("kotlin.") ->
            error("Rows can't be converted to ${type.name}, map the rows yourself (e.g. `.map { it[column] }`)")
        type.isInterface -> InterfaceConverter(type, factory, mapper)
        else -> DtoConverter(type.kotlin, mapper)
    }
}

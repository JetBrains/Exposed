package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible

internal class ReflectionEntityMapper<T : Any, ID : Any>(
    private val table: IdTable<ID>,
    domainType: KClass<T>,
) : ExposedEntityMapper<T> {
    private val columnsByName: Map<String, Column<*>> = table::class.memberProperties
        .filter { it.returnType.isSubtypeOf(Column::class.starProjectedType) }
        .associate { it.isAccessible = true; it.name to it.getter.call(table) as Column<*> }

    private val constructor = checkNotNull(domainType.primaryConstructor) { "${domainType.qualifiedName} needs a primary constructor" }
    private val properties = domainType.memberProperties.associateBy { it.name }

    private val parameterColumns = constructor.parameters.associateWith { p ->
        checkNotNull(columnsByName[p.name]) { "No column for ${domainType.simpleName}.${p.name} in ${table.tableName}" }
    }

    override fun toEntity(row: ResultRow): T =
        constructor.callBy(parameterColumns.mapValues { (_, column) -> row[column].unwrapEntityId() })

    @Suppress("UNCHECKED_CAST")
    override fun fill(statement: UpdateBuilder<*>, entity: T) {
        parameterColumns.forEach { (p, column) ->
            if (column != table.id) statement[column as Column<Any?>] = properties.getValue(p.name!!).get(entity)
        }
    }

    override fun columnFor(property: String): Column<*>? = columnsByName[property]

    override fun propertyFor(column: Column<*>): String? = columnsByName.entries.firstOrNull { it.value == column }?.key

    private fun Any?.unwrapEntityId() = if (this is EntityID<*>) value else this
}

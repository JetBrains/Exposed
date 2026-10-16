package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible

internal class ReflectionEntityMapper<T : Any, ID : Any>(
    private val table: IdTable<ID>,
    domainType: KClass<T>,
) : ExposedEntityMapper<T> {
    @Suppress("UNCHECKED_CAST")
    private val columnsByName: Map<String, Column<Any>> = table::class.memberProperties
        .filter { it.returnType.isSubtypeOf(Column::class.starProjectedType) }
        .associate {
            it.isAccessible = true
            it.name to it.getter.call(table) as Column<Any>
        }

    private val constructor = checkNotNull(domainType.primaryConstructor) { "${domainType.qualifiedName} needs a primary constructor" }
    private val properties = domainType.memberProperties.associateBy { it.name }

    private val parameterColumns = constructor.parameters.associateWith { p ->
        checkNotNull(columnsByName[p.name]) { "No column for ${domainType.simpleName}.${p.name} in ${table.tableName}" }
    }

    override val columns: List<Column<*>> = parameterColumns.values.toList()

    override fun toEntity(row: ResultRow): T =
        constructor.callBy(parameterColumns.mapValues { (_, column) -> row[column].unwrapEntityId() })

    @Suppress("UNCHECKED_CAST")
    override fun fill(statement: UpdateBuilder<*>, entity: T) {
        parameterColumns.forEach { (p, column) ->
            if (column != table.id) statement[column as Column<Any?>] = properties.getValue(p.name!!).get(entity)
        }
    }

    override fun columnFor(property: String): Column<Any>? = columnsByName[property]

    override fun propertyFor(column: Column<*>): String? = columnsByName.entries.firstOrNull { it.value == column }?.key

    override fun propertyType(name: String): KType? = properties[name]?.returnType
}

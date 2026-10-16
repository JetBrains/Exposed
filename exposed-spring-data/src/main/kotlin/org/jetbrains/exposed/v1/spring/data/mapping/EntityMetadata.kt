package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible

/**
 * What is known about an entity and its table: which entity property is stored in which column.
 *
 * A property is mapped if the entity declares it and the table object has a [Column] property with the same Kotlin name.
 * [Column.name] can't be used for this, because it is the SQL name (e.g. `created_at`).
 */
internal class EntityMetadata<T : Any, ID : Any>(
    val table: IdTable<ID>,
    val domainType: KClass<T>,
) {
    private val properties: Map<String, KProperty1<T, *>> = domainType.memberProperties.associateBy { it.name }

    @Suppress("UNCHECKED_CAST")
    private val columnsByProperty: Map<String, Column<Any?>> = table::class.memberProperties
        .filter { it.name in properties && it.returnType.isSubtypeOf(Column::class.starProjectedType) }
        .associate {
            it.isAccessible = true
            it.name to it.getter.call(table) as Column<Any?>
        }

    private val propertiesByColumn: Map<Column<*>, String> = columnsByProperty.entries.associate { (property, column) -> column to property }

    /** The mapped properties and their columns. */
    val mappedProperties: Map<String, Column<Any?>> get() = columnsByProperty

    /** The name of the entity property stored in the id column. */
    val idProperty: String = checkNotNull(propertiesByColumn[table.id]) {
        "${domainType.simpleName} has no property for the id column of table ${table.tableName}"
    }

    fun columnFor(property: String): Column<Any?>? = columnsByProperty[property]

    fun requireColumn(property: String): Column<Any?> = requireNotNull(columnFor(property)) {
        "No column found for property '$property' of ${domainType.simpleName} in table ${table.tableName}"
    }

    fun propertyType(property: String): KType? = properties[property]?.returnType

    /** Reads [property] from [entity], also if the property isn't public (e.g. the id of a `Persistable`). */
    fun valueOf(entity: T, property: String): Any? = properties.getValue(property)
        .also { it.isAccessible = true }
        .get(entity)
}

package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

/**
 * Connects an entity class, as described by the [ExposedMappingContext], to its table: which property is stored in which column.
 *
 * A property is mapped if it is a persistent property of [persistentEntity] and the table object has a [Column] property
 * with the same Kotlin name (see [TableColumns]).
 * Everything about the class itself (id and version property, reading and writing values) comes from [persistentEntity].
 */
internal class EntityMetadata<T : Any, ID : Any>(
    val table: IdTable<ID>,
    val persistentEntity: ExposedPersistentEntity<T>,
) {
    val domainType: KClass<T> = persistentEntity.type.kotlin

    private val properties: Map<String, ExposedPersistentProperty> = persistentEntity.associateBy { it.name }

    private val columnsByProperty: Map<String, Column<Any?>> = TableColumns.of(table).columns.filterKeys { it in properties }

    private val propertiesByColumn: Map<Column<*>, String> = columnsByProperty.entries.associate { (property, column) -> column to property }

    private val propertyTypes: Map<String, KType> = domainType.memberProperties
        .filter { it.name in properties }
        .associate { it.name to it.returnType }

    val mappedProperties: Map<String, Column<Any?>> get() = columnsByProperty

    val idProperty: String = run {
        val byColumn = checkNotNull(propertiesByColumn[table.id]) {
            "${domainType.simpleName} has no property for the id column of table ${table.tableName}"
        }
        val declared = persistentEntity.idProperty?.name
        check(declared == null || declared == byColumn) {
            "The id property of ${domainType.simpleName} is '$declared', but the id column of table ${table.tableName} is stored in '$byColumn'"
        }
        byColumn
    }

    private val version: ExposedPersistentProperty? = persistentEntity.versionProperty?.also {
        check(it.type.kotlin in VERSION_TYPES) { "@Version property ${domainType.simpleName}.${it.name} must be a Long, Int or Short, but is ${it.type}" }
    }
    val versionProperty: String? = version?.name
    val versionColumn: Column<Any?>? = versionProperty?.let(::requireColumn)
    private val versionIsPrimitive: Boolean = version?.type?.isPrimitive == true

    fun versionOf(entity: T): Number? = versionProperty?.let { valueOf(entity, it) as Number? }

    fun initialVersion(): Number = toVersionType(if (versionIsPrimitive) 1L else 0L)

    fun nextVersion(current: Number): Number = toVersionType(current.toLong() + 1)

    private fun toVersionType(value: Long): Number = when (version?.type?.kotlin) {
        Int::class -> value.toInt()
        Short::class -> value.toShort()
        else -> value
    }

    fun columnFor(property: String): Column<Any?>? = columnsByProperty[property]

    fun requireColumn(property: String): Column<Any?> = requireNotNull(columnFor(property)) {
        "No column found for property '$property' of ${domainType.simpleName} in table ${table.tableName}"
    }

    fun propertyType(property: String): KType? = propertyTypes[property]

    /** Reads [property] from [entity] through the mapping context, also if the property isn't public (e.g. the id of a `Persistable`). */
    fun valueOf(entity: T, property: String): Any? = persistentEntity.getPropertyAccessor(entity).getProperty(properties.getValue(property))

    /**
     * Returns [entity] with [property] set to [value]. Immutable entities aren't changed: a new instance is created
     * (with Kotlin's `copy` for data classes), so always use the returned instance.
     */
    fun withValue(entity: T, property: String, value: Any?): T = persistentEntity.getPropertyAccessor(entity)
        .apply { setProperty(properties.getValue(property), value) }
        .bean

    private companion object {
        val VERSION_TYPES = setOf(Long::class, Int::class, Short::class)
    }
}

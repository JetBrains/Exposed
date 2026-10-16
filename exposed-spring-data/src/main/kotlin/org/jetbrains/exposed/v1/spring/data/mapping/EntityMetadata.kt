package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.springframework.data.annotation.Version
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.jvmErasure

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

    /**
     * The property annotated with Spring Data's [Version], if any, used for optimistic locking.
     *
     * `@Version` has a Java target, so in Kotlin it ends up on the backing field (or the getter), not on the property.
     */
    val versionProperty: String? = run {
        val annotated = properties.values.filter {
            it.javaField?.isAnnotationPresent(Version::class.java) == true || it.javaGetter?.isAnnotationPresent(Version::class.java) == true
        }
        check(annotated.size <= 1) { "${domainType.simpleName} has more than one @Version property: ${annotated.map { it.name }}" }
        annotated.singleOrNull()?.name
    }

    /** The column of [versionProperty]. */
    val versionColumn: Column<Any?>? = versionProperty?.let(::requireColumn)

    // a primitive version (e.g. a non-null `Int`) can't be null, so `0` means new and the first stored version is `1`
    private val versionIsPrimitive: Boolean = versionProperty?.let { name ->
        val property = properties.getValue(name)
        (property.javaField?.type ?: property.javaGetter?.returnType)?.isPrimitive
    } == true

    private val versionType: KClass<*>? = versionProperty?.let { name ->
        properties.getValue(name).returnType.jvmErasure.also {
            check(it == Long::class || it == Int::class || it == Short::class) {
                "@Version property ${domainType.simpleName}.$name must be a Long, Int or Short, but is $it"
            }
        }
    }

    /** Whether [entity] is new according to its version: `null` for wrapper types, `0` for primitive types. */
    fun isNewByVersion(entity: T): Boolean {
        val version = versionOf(entity)
        return if (versionIsPrimitive) version == null || version.toLong() == 0L else version == null
    }

    /** The current version of [entity], or `null` if it has none. */
    fun versionOf(entity: T): Number? = versionProperty?.let { valueOf(entity, it) as Number? }

    /** The version stored on insert: `0` for wrapper types, `1` for primitive types (`0` already means "new"). */
    fun initialVersion(): Number = toVersionType(if (versionIsPrimitive) 1L else 0L)

    /** The version stored on update. */
    fun nextVersion(current: Number): Number = toVersionType(current.toLong() + 1)

    private fun toVersionType(value: Long): Number = when (versionType) {
        Int::class -> value.toInt()
        Short::class -> value.toShort()
        else -> value
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

package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.springframework.data.projection.ProjectionFactory
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

/**
 * Turns a [ResultRow] into [R]. Instances are created and cached by [ProjectionResolver].
 */
internal sealed class RowConverter<out R : Any>(private val targetType: Class<*>) {
    /** The columns to select so that rows can be converted. */
    abstract val columns: List<Column<*>>

    /** Whether a plain `selectAll()` may be narrowed to [columns], which is only the case for projections. */
    abstract val narrowsSelect: Boolean

    /** The columns a row must contain to be converted. */
    protected open val requiredColumns: List<Column<*>> get() = columns

    fun convert(row: ResultRow): R {
        val missing = requiredColumns.filterNot { row.fieldIndex.containsKey(it) }
        check(missing.isEmpty()) {
            "Can't convert the row to ${targetType.simpleName}, these columns are not selected: ${missing.joinToString { it.name }}"
        }
        return read(row)
    }

    protected abstract fun read(row: ResultRow): R
}

/** Returns the row itself. Selects every column of the table, but accepts rows with any columns. */
internal class RawRowConverter(override val columns: List<Column<*>>) : RowConverter<ResultRow>(ResultRow::class.java) {
    override val narrowsSelect: Boolean = false
    override val requiredColumns: List<Column<*>> = emptyList()

    override fun read(row: ResultRow): ResultRow = row
}

/**
 * Calls the primary constructor of [type] with the values of the columns named like its parameters.
 * Used for the entity itself and for DTO projections, so both follow the same rules:
 * optional parameters without a column are left out, and every value must fit the entity property it comes from.
 */
internal class ConstructorConverter<R : Any>(
    type: KClass<R>,
    metadata: EntityMetadata<*, *>,
    override val narrowsSelect: Boolean,
) : RowConverter<R>(type.java) {
    private val constructor = checkNotNull(type.primaryConstructor) { "${type.qualifiedName} needs a primary constructor" }.also {
        require(it.visibility == KVisibility.PUBLIC) { "The primary constructor of ${type.qualifiedName} must be public" }
    }

    private val parameterColumns: Map<KParameter, Column<*>> = constructor.parameters.mapNotNull { parameter ->
        val name = checkNotNull(parameter.name) { "${type.simpleName} has an unnamed constructor parameter" }
        val column = metadata.columnFor(name)
        when {
            column != null -> {
                metadata.requireAssignable(type.simpleName, name, parameter.type)
                parameter to column
            }
            parameter.isOptional -> null
            else -> error("No column found for ${type.simpleName}.$name in table ${metadata.table.tableName}")
        }
    }.toMap()

    override val columns: List<Column<*>> = parameterColumns.values.distinct()

    override fun read(row: ResultRow): R =
        constructor.callBy(parameterColumns.mapValues { (_, column) -> row[column].unwrapEntityId() })
}

/** Creates a closed interface projection backed by a map of the projected property values. */
internal class InterfaceConverter<R : Any>(
    private val type: Class<R>,
    private val factory: ProjectionFactory,
    metadata: EntityMetadata<*, *>,
) : RowConverter<R>(type) {
    private val propertyColumns: Map<String, Column<*>> = factory.getProjectionInformation(type)
        .also { require(it.isClosed) { "Open projections are not supported: ${type.name}" } }
        .inputProperties
        .associate { descriptor ->
            val column = checkNotNull(metadata.columnFor(descriptor.name)) {
                "No column found for ${type.simpleName}.${descriptor.name} in table ${metadata.table.tableName}"
            }
            val getter = checkNotNull(descriptor.readMethod) { "${type.simpleName}.${descriptor.name} has no getter" }
            getter.kotlinFunction?.returnType?.let { metadata.requireAssignable(type.simpleName, descriptor.name, it) }
            descriptor.name to column
        }

    override val columns: List<Column<*>> = propertyColumns.values.distinct()
    override val narrowsSelect: Boolean = true

    override fun read(row: ResultRow): R =
        factory.createProjection(type, propertyColumns.mapValues { (_, column) -> row[column].unwrapEntityId() })
}

/** Fails unless a value of the entity property [name] can be assigned to [target]. */
private fun EntityMetadata<*, *>.requireAssignable(owner: String?, name: String, target: KType) {
    val source = checkNotNull(propertyType(name)) { "No entity property '$name' for $owner.$name" }
    require(source.jvmErasure.isSubclassOf(target.jvmErasure)) {
        "$owner.$name has type $target, but the entity property has type $source"
    }
    // TODO: Discuss if we want to enfore strict nullability
//    require(target.isMarkedNullable || !source.isMarkedNullable) {
//        "$owner.$name is not nullable, but the entity property $name is ($source)"
//    }
}

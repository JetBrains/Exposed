@file:Suppress("MatchingDeclarationName")

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

internal class EntityConverter(
    private val mapper: ExposedEntityMapper<Any>
) : RowConverter {
    override val columns: List<Column<*>> = mapper.columns

    override fun convert(row: ResultRow): Any = mapper.toEntity(row)
}

internal class RawRowConverter(
    override val columns: List<Column<*>>
) : RowConverter {
    override fun convert(row: ResultRow): Any = row
}

internal class DtoConverter(
    type: KClass<*>,
    private val mapper: ExposedEntityMapper<*>
) : RowConverter {
    private val ctor = checkNotNull(type.primaryConstructor) { "$type needs a primary constructor" }.also {
        require(it.visibility == KVisibility.PUBLIC) { "$type primary constructor must be public" }
    }
    private val params: Map<KParameter, Column<*>> = ctor.parameters.mapNotNull { parameter ->
        val column = mapper.columnFor(parameter.name!!)
        when {
            column != null -> {
                mapper.requireAssignable(type.simpleName!!, parameter.name!!, parameter.type)
                parameter to column
            }
            parameter.isOptional -> null
            else -> error("No column found for ${type.simpleName}.${parameter.name}")
        }
    }.toMap()

    override val columns: List<Column<*>> = params.values.distinct()

    override fun convert(row: ResultRow): Any = ctor
        .callBy(params.mapValues { (_, column) -> row[column].unwrapEntityId() })
}

internal class InterfaceConverter(
    private val type: Class<*>,
    private val factory: ProjectionFactory,
    mapper: ExposedEntityMapper<Any>,
) : RowConverter {
    private val info = factory.getProjectionInformation(type).also {
        require(it.isClosed) { "Open projections are not supported: ${type.name}" }
    }
    private val byProperty: Map<String, Column<*>> = info.inputProperties.associate { d ->
        val column = checkNotNull(mapper.columnFor(d.name)) { "No column found for ${type.simpleName}.${d.name}" }
        val getter = checkNotNull(d.readMethod) { "${type.simpleName}.${d.name} has no getter" }
        getter.kotlinFunction?.returnType?.let { mapper.requireAssignable(type.simpleName, d.name, it) }

        d.name to column
    }

    override val columns: List<Column<*>> = byProperty.values.distinct()

    override fun convert(row: ResultRow): Any = factory
        .createProjection(
            type,
            byProperty.mapValues { (_, column) -> row[column].unwrapEntityId() }
        )
}

private fun ExposedEntityMapper<*>.requireAssignable(owner: String, name: String, target: KType) {
    val source = checkNotNull(propertyType(name)) { "No entity property '$name' for $owner.$name" }
    require(source.jvmErasure.isSubclassOf(target.jvmErasure)) {
        "$owner.$name has type $target, but the entity property has type $source"
    }
    // TODO: Discuss if we want to enfore strict nullability
//    require(target.isMarkedNullable || !source.isMarkedNullable) {
//        "$owner.$name is not nullable, but the entity property $name is ($source)"
//    }
}

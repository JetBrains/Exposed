package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import kotlin.reflect.KType

internal interface ExposedEntityMapper<T : Any> {
    val columns: List<Column<*>>

    fun toEntity(row: ResultRow): T
    fun fill(statement: UpdateBuilder<*>, entity: T)
    fun columnFor(property: String): Column<Any>?
    fun propertyFor(column: Column<*>): String?
    fun propertyType(name: String): KType?
}

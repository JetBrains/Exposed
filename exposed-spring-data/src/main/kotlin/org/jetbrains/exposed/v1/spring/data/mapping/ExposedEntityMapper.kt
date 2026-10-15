package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

interface ExposedEntityMapper<T : Any> {
    fun toEntity(row: ResultRow): T
    fun fill(statement: UpdateBuilder<*>, entity: T)
    fun columnFor(property: String): Column<*>?
}

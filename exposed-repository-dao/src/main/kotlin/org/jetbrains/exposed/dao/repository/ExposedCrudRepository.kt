package org.jetbrains.exposed.dao.repository

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder

interface ExposedCrudRepository<T:Any>/*<ID:Any, T:ExposedEntity<ID, T>>*/ {
//    fun fromRow(row: ResultRow) : T
//    fun toRow(entity: T, row: ResultRow)
    fun save(entity: T)
    fun find(condition: SqlExpressionBuilder.() -> Op<Boolean>) : List<T>
    fun delete(entity: T)
}
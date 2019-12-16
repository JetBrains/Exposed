package org.jetbrains.exposed.dao.repository

import org.jetbrains.exposed.sql.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

open class ReflectionBasedCrudRepository<T:Any>(val clazz: KClass<T>) : ExposedCrudRepository<T> {

    override fun save(entity: T) {
        val exposedTable = clazz.exposedTable()
        exposedTable.insert { insert ->
            EntityTableRegistry {
                clazz.getters.forEach { getter ->
                    val column = exposedTable[getter]
                    insert[column] = getter.get(entity)
                }
            }
        }
    }

    fun fromRow(resultRow: ResultRow) : T {
        val table = clazz.exposedTable()
        return EntityTableRegistry {
            val params = clazz.params.associateWith { resultRow.getOrNull(table[it]) }
            clazz.primaryConstructor!!.callBy(params)
        }
    }

    override fun find(condition: SqlExpressionBuilder.() -> Op<Boolean>): List<T> {
        return clazz.exposedTable().select(condition).map { fromRow(it) }
    }

    override fun delete(entity: T) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
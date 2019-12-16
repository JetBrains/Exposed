package org.jetbrains.exposed.dao.repository

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

fun <T:Any> KClass<T>.exposedTable() : Table = EntityTableRegistry.forClass(this)

inline fun <reified T:Any, R> KProperty1<T, R>.exposedColumn() : Column<R> = EntityTableRegistry {
    T::class.exposedTable()[this@exposedColumn] as Column<R>
}

object EntityTableRegistry {

    operator fun <T> invoke(body: EntityTableRegistry.() -> T) = this.body()
    private val entityToTable = ConcurrentHashMap<KClass<*>, Table>()

    private val entityToParams = ConcurrentHashMap<KClass<*>, List<KParameter>>()
    private val entityToGetters = ConcurrentHashMap<KClass<*>, List<KProperty1<*,*>>>()

    val KClass<*>.params get() = entityToParams.getOrPut(this) { primaryConstructor!!.valueParameters }

    val <T:Any> KClass<T>.getters: List<KProperty1<T, *>> get() = entityToGetters.getOrPut(this) {
        params.mapNotNull { param ->
            this.declaredMemberProperties.find { it.name == param.name }
        }
    } as List<KProperty1<T, *>>

    operator fun Table.get(property: KParameter) : Column<Any?> = columns.single { it.name == property.name } as Column<Any?>
    operator fun <R> Table.get(property: KProperty1<*,R>) : Column<R> = columns.single { it.name == property.name } as Column<R>

    fun forClass(clazz: KClass<*>) = entityToTable.getOrPut(clazz) {
        requireNotNull(clazz.primaryConstructor)
        object : Table(clazz.simpleName!!) {
            init {
                clazz.params.forEach { param ->
                    val name = param.name!!
                    val column = when(param.type.classifier) {
                        Int::class -> integer(name)
                        Long::class -> long(name)
                        Boolean::class -> bool(name)
                        Double::class -> double(name)
                        Float::class -> float(name)
                        Short::class -> short(name)
                        ByteArray::class -> binary(name, 128)
                        BigDecimal::class -> decimal(name, 100, 10)
                        UUID::class -> uuid(name)
                        String::class -> text(name)
                        else -> error("")
                    }

                    if (param.type.isMarkedNullable) {
                        column.nullable()
                    }
                }
            }
        }
    }
}
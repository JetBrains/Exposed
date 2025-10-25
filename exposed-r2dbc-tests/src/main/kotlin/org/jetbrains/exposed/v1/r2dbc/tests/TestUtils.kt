package org.jetbrains.exposed.v1.r2dbc.tests

import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.vendors.DatabaseDialectMetadata
import java.util.*

val currentDialectTest: DatabaseDialect get() = TransactionManager.current().db.dialect

val currentDialectMetadataTest: DatabaseDialectMetadata
    get() = TransactionManager.current().db.dialectMetadata

val currentDialectIfAvailableTest: DatabaseDialect?
    get() =
        if (TransactionManager.currentOrNull() != null) {
            currentDialectTest
        } else {
            null
        }

inline fun <reified E : Enum<E>> enumSetOf(vararg elements: E): EnumSet<E> =
    elements.toCollection(EnumSet.noneOf(E::class.java))

fun <T> Column<T>.constraintNamePart() = (currentDialectTest as? SQLServerDialect)?.let {
    " CONSTRAINT DF_${table.tableName}_$name"
} ?: ""

suspend fun Table.insertAndWait(duration: Long) {
    this.insert { }
    TransactionManager.current().commit()
    Thread.sleep(duration)
}

/** Retrieves the value of the designated column as a `String`, with column index starting at 1. **/
internal fun Row.getString(index: Int): String? = get(index - 1, java.lang.String::class.java)?.toString()

/** Retrieves the value of the named column as a `String`. **/
internal fun Row.getString(label: String): String? = get(label, java.lang.String::class.java)?.toString()

/**
 * Retrieves the value of the designated column as a `Boolean`, with column index starting at 1.
 *
 * If the value is SQL `NULL`, the value returned is `false`.
 */
internal fun Row.getBoolean(index: Int): Boolean = get(index - 1, java.lang.Boolean::class.java)?.booleanValue() ?: false

/**
 * Retrieves the value of the named column as a `Boolean`.
 *
 * If the value is SQL `NULL`, the value returned is `false`.
 */
internal fun Row.getBoolean(label: String): Boolean = get(label, java.lang.Boolean::class.java)?.booleanValue() ?: false

/**
 * Retrieves the value of the designated column as an `Int`, with column index starting at 1.
 *
 * If the value is SQL `NULL`, the value returned is 0.
 */
internal fun Row.getInt(index: Int): Int = get(index - 1, java.lang.Integer::class.java)?.toInt() ?: 0

/**
 * Retrieves the value of the named column as an `Int`.
 *
 * If the value is SQL `NULL`, the value returned is 0.
 */
internal fun Row.getInt(label: String): Int = get(label, java.lang.Integer::class.java)?.toInt() ?: 0

internal suspend fun Query.forEach(block: (ResultRow) -> Unit) {
    this.collect { block(it) }
}

internal suspend fun Query.forEachIndexed(block: (Int, ResultRow) -> Unit) {
    var index = 0
    forEach { block(index++, it) }
}

internal suspend fun <T> Flow<T>.any(): Boolean {
    return try {
        this.first()
        true
    } catch (_: NoSuchElementException) {
        false
    }
}

internal suspend fun <T : Comparable<T>> Flow<T>.sorted(): List<T> {
    return toList().sorted()
}

internal suspend fun <T> Flow<T>.distinct(): List<T> = this.distinctUntilChanged().toList()

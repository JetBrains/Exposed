package org.jetbrains.exposed.sql.tests

import io.r2dbc.spi.Row
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.DatabaseDialectMetadata
import java.util.*

fun String.inProperCase(): String = TransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this) ?: this

val currentDialectTest: DatabaseDialect get() = TransactionManager.current().db.dialect

val currentDialectMetadataTest: DatabaseDialectMetadata
    get() = TransactionManager.current().db.dialectMetadata

val currentDialectIfAvailableTest: DatabaseDialect?
    get() =
        if (TransactionManager.isInitialized() && TransactionManager.currentOrNull() != null) {
            currentDialectTest
        } else {
            null
        }

inline fun <reified E : Enum<E>> enumSetOf(vararg elements: E): EnumSet<E> =
    elements.toCollection(EnumSet.noneOf(E::class.java))

internal fun Row.getString(index: Int): String? = get(index, java.lang.String::class.java)?.toString()

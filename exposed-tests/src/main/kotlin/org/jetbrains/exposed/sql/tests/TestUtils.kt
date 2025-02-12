package org.jetbrains.exposed.sql.tests

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.DatabaseDialectMetadata
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
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

fun <T> Column<T>.constraintNamePart() = (currentDialectTest as? SQLServerDialect)?.let {
    " CONSTRAINT DF_${table.tableName}_$name"
} ?: ""

fun Table.insertAndWait(duration: Long) {
    this.insert { }
    TransactionManager.current().commit()
    Thread.sleep(duration)
}

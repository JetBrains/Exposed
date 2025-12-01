package org.jetbrains.exposed.v1.tests

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.vendors.DatabaseDialectMetadata
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

fun Table.insertAndWait(duration: Long) {
    this.insert { }
    TransactionManager.current().commit()
    Thread.sleep(duration)
}

/** Tag for JDBC tests that will require creating a matching R2DBC test, when possible. */
const val MISSING_R2DBC_TEST = "Test has no R2DBC version"

/** Tag for JDBC tests that already have a matching R2DBC test, but that are still missing some of the original logic. */
const val INCOMPLETE_R2DBC_TEST = "Test has incomplete R2DBC version"

/** Tag for JDBC tests that will most likely never require a matching R2DBC test, unless the driver's R2DBC SPI changes. */
const val NO_R2DBC_SUPPORT = "Test subject not supported by R2DBC"

/** Tag for JDBC tests that will most likely never require a matching R2DBC test. */
const val NOT_APPLICABLE_TO_R2DBC = "Test subject is not relevant to R2DBC"

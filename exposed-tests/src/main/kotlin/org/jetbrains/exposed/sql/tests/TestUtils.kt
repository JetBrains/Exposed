package org.jetbrains.exposed.sql.tests

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.VendorDialect

fun String.inProperCase(): String = TransactionManager.currentOrNull()?.let { tm ->
    (currentDialectTest as? VendorDialect)?.run {
        this@inProperCase.inProperCase
    }
} ?: this

val currentDialectTest: DatabaseDialect get() = TransactionManager.current().db.dialect

val currentDialectIfAvailableTest : DatabaseDialect? get() =
    if (TransactionManager.isInitialized() && TransactionManager.currentOrNull() != null) {
        currentDialectTest
    } else null


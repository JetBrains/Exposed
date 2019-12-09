package org.jetbrains.exposed.sql.tests

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.DatabaseDialect

fun String.inProperCase(): String = TransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this) ?: this

val currentDialectTest: DatabaseDialect get() = TransactionManager.current().db.dialect

val currentDialectIfAvailableTest : DatabaseDialect? get() =
    if (TransactionManager.isInitialized() && TransactionManager.currentOrNull() != null) {
        currentDialectTest
    } else null


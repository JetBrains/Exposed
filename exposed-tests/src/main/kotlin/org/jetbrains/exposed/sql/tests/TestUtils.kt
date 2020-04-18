package org.jetbrains.exposed.sql.tests

import org.jetbrains.exposed.sql.transactions.ITransactionManager
import org.jetbrains.exposed.sql.vendors.DatabaseDialect

fun String.inProperCase(): String = ITransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this) ?: this

val currentDialectTest: DatabaseDialect get() = ITransactionManager.current().db.dialect

val currentDialectIfAvailableTest : DatabaseDialect? get() =
    if (ITransactionManager.isInitialized() && ITransactionManager.currentOrNull() != null) {
        currentDialectTest
    } else null


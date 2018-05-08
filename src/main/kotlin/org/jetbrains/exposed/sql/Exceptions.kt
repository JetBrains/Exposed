@file:Suppress("PackageDirectoryMismatch")
package org.jetbrains.exposed.exceptions

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.VendorDialect
import java.sql.SQLException

class EntityNotFoundException(val id: EntityID<*>, val entity: EntityClass<*, *>): Exception("Entity ${entity.klass.simpleName}, id=$id not found in database")

class ExposedSQLException(cause: Throwable?, val contexts: List<StatementContext>) : SQLException(cause) {
    fun causedByQueries() : List<String> {
        return TransactionManager.currentOrNull()?.let { transaction ->
            contexts.map {
                try {
                    it.expandArgs(transaction)
                } catch (e: Exception) {
                    "Failed on expanding args for ${it.statement}"
                }
            }
        } ?: listOf("No transaction in context to process queries")
    }
}

class UnsupportedByDialectException(baseMessage: String, dialect: VendorDialect) : UnsupportedOperationException(baseMessage + ", dialect: ${dialect.name}.")
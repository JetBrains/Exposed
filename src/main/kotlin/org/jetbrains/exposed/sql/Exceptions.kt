@file:Suppress("PackageDirectoryMismatch")
package org.jetbrains.exposed.exceptions

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import java.sql.SQLException

class EntityNotFoundException(val id: EntityID<*>, val entity: EntityClass<*, *>): Exception("Entity ${entity.klass.simpleName}, id=$id not found in database")

class ExposedSQLException(cause: Throwable?, val contexts: List<StatementContext>, private val transaction: Transaction) : SQLException(cause) {
    fun causedByQueries() : List<String> = contexts.map {
        try {
            it.expandArgs(transaction)
        } catch (e: Exception) {
            "Failed on expanding args for ${it.statement}"
        }
    }

    override fun toString() = "${super.toString()}\nSQL: ${causedByQueries()}"
}

class UnsupportedByDialectException(baseMessage: String, dialect: DatabaseDialect) : UnsupportedOperationException(baseMessage + ", dialect: ${dialect.name}.")

internal fun Transaction.throwUnsupportedException(message: String): Nothing = throw UnsupportedByDialectException(message, db.dialect)

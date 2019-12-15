@file:Suppress("PackageDirectoryMismatch")
package org.jetbrains.exposed.exceptions

import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import java.sql.SQLException

class ExposedSQLException(cause: Throwable?, val contexts: List<StatementContext>, private val transaction: Transaction) : SQLException(cause) {
    fun causedByQueries() : List<String> = contexts.map {
        try {
            if (transaction.debug) {
                it.expandArgs(transaction)
            } else {
                it.sql(transaction)
            }
        } catch (e: Throwable) {
            try {
                (it.statement as? Query)?.prepareSQL(QueryBuilder(!transaction.debug))
            } catch (e: Throwable) {
                null
            } ?: "Failed on expanding args for ${it.statement.type}: ${it.statement}"
        }
    }

    private val originalSQLException = cause as? SQLException

    override fun getSQLState(): String  = originalSQLException?.sqlState.orEmpty()

    override fun getErrorCode(): Int = originalSQLException?.errorCode ?: 0

    override fun toString() = "${super.toString()}\nSQL: ${causedByQueries()}"
}

class UnsupportedByDialectException(baseMessage: String, val dialect: DatabaseDialect) : UnsupportedOperationException(baseMessage + ", dialect: ${dialect.name}.")

/**
 * DuplicateColumnException is thrown :
 *
 * When you attempt to create a table with multiple columns having the same name.
 * When you replace a column of a table so that you define multiple columns having the same name.
 *
 * @param columnName the duplicated column name
 */
class DuplicateColumnException(columnName: String, tableName: String) : ExceptionInInitializerError("Duplicate column name \"$columnName\" in table \"$tableName\"")

internal fun Transaction.throwUnsupportedException(message: String): Nothing = throw UnsupportedByDialectException(message, db.dialect)

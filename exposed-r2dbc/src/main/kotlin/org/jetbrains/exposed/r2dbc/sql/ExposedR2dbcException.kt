@file:Suppress("PackageDirectoryMismatch", "InvalidPackageDeclaration")

package org.jetbrains.exposed.r2dbc.exceptions

import io.r2dbc.spi.R2dbcException
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.AbstractQuery
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs

// TODO Discuss need for package mismatch (cf. core module ExposedSQLException)
/**
 * An exception that provides information about a database access error,
 * within the [contexts] of the executed statements that caused the exception.
 */
class ExposedR2dbcException(
    cause: Throwable?,
    val contexts: List<StatementContext>,
    private val transaction: R2dbcTransaction
) : R2dbcException(cause) {
    private var plainSql: List<String>? = null

    internal constructor(
        cause: Throwable?,
        sqlString: String,
        transaction: R2dbcTransaction
    ) : this(cause, emptyList<StatementContext>(), transaction) {
        plainSql = listOf(sqlString)
    }

    fun causedByQueries(): List<String> = plainSql ?: contexts.map {
        try {
            if (transaction.debug) {
                it.expandArgs(transaction)
            } else {
                it.sql(transaction)
            }
        } catch (_: Throwable) {
            try {
                (it.statement as? AbstractQuery<*>)?.prepareSQL(QueryBuilder(!transaction.debug))
            } catch (_: Throwable) {
                null
            } ?: "Failed on expanding args for ${it.statement.type}: ${it.statement}"
        }
    }

    private val originalR2dbcException: R2dbcException? = cause as? R2dbcException

    // ExposedSQLException methods are also not used in project
    fun getSQLState(): String = originalR2dbcException?.sqlState.orEmpty()

    fun getSpecificErrorCode(): Int = originalR2dbcException?.errorCode ?: 0

    override fun toString() = "${super.toString()}\nSQL: ${causedByQueries()}"
}

// identical logic to SuspendExecutable.executeInternal()
// Exceptions happen less in latter and more when collecting from result
internal fun Statement<*>.getContexts(): List<StatementContext> {
    val args = arguments()
    return if (args.any()) {
        args.map { StatementContext(this, it) }
    } else {
        listOf(StatementContext(this, emptyList()))
    }
}

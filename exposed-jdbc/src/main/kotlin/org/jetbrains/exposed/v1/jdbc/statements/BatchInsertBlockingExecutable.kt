package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.core.statements.MultiRowValuesInsertStatement
import org.jetbrains.exposed.v1.core.statements.SQLServerBatchInsertStatement
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import java.sql.ResultSet

/**
 * Represents the execution logic for an SQL statement that batch inserts new rows into a table.
 */
open class BatchInsertBlockingExecutable<S : BatchInsertStatement>(
    override val statement: S
) : InsertBlockingExecutable<List<ResultRow>, S>(statement) {
    override val isAlwaysBatch = true

    override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi {
        return if (!statement.shouldReturnGeneratedValues) {
            transaction.connection.prepareStatement(sql, false)
        } else {
            super.prepared(transaction, sql)
        }
    }
}

/**
 * Represents the execution logic for an SQL statement that batch inserts new rows into a table,
 * specifically for the SQL Server database.
 */
open class SQLServerBatchInsertBlockingExecutable(
    override val statement: SQLServerBatchInsertStatement
) : BatchInsertBlockingExecutable<SQLServerBatchInsertStatement>(statement) {
    override val isAlwaysBatch: Boolean = false

    override fun JdbcPreparedStatementApi.execInsertFunction(): Pair<Int, ResultSet?> {
        @OptIn(InternalApi::class)
        val rs = if (statement.columnToReturnValue != null) {
            executeQuery()
        } else {
            executeUpdate()
            null
        }
        return statement.arguments!!.size to rs?.result
    }
}

/**
 * Represents the execution logic for an SQL statement that batch inserts new rows into a table,
 * specifically using a single multi-row value INSERT statement.
 */
open class MultiRowValuesInsertBlockingExecutable(
    override val statement: MultiRowValuesInsertStatement
) : BatchInsertBlockingExecutable<MultiRowValuesInsertStatement>(statement) {
    override val isAlwaysBatch: Boolean = false

    override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi {
        // [MariaDB] jdbc returnGeneratedValues() does not support adding RETURNING clause automatically
        val needsManualReturning = statement.shouldReturnGeneratedValues &&
            autoIncColumns.isNotEmpty() &&
            currentDialect is MariaDBDialect
        return if (needsManualReturning) {
            @OptIn(InternalApi::class)
            val generatedColumns = autoIncColumns.map { it.name.inProperCase() }.toTypedArray()
            val replaceReturning = "$sql RETURNING ${generatedColumns.joinToString()}"
            transaction.connection.prepareStatement(replaceReturning, false)
        } else {
            super.prepared(transaction, sql)
        }
    }
}

@Suppress("Unchecked_Cast")
internal fun <S : BatchInsertStatement> S.executable(): BatchInsertBlockingExecutable<S> {
    return when (this) {
        is SQLServerBatchInsertStatement -> SQLServerBatchInsertBlockingExecutable(this)
        is MultiRowValuesInsertStatement -> MultiRowValuesInsertBlockingExecutable(this)
        else -> BatchInsertBlockingExecutable(this)
    } as BatchInsertBlockingExecutable<S>
}

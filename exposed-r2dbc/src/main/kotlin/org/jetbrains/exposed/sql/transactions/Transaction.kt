package org.jetbrains.exposed.sql.transactions

import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcPreparedStatementImpl

fun <T : Any> Transaction.execQuery(
    @Language("sql") stmt: String,
    args: Iterable<Pair<IColumnType<*>, Any?>> = emptyList(),
    explicitStatementType: StatementType? = null,
    transform: (Row) -> T?
): Flow<T?>? {
    if (stmt.isEmpty()) return emptyFlow()

    val type = explicitStatementType
        ?: StatementType.entries.find { stmt.trim().startsWith(it.name, true) }
        ?: StatementType.OTHER

    return exec(object : Statement<Flow<T?>>(type, emptyList()) {
        override fun PreparedStatementApi.executeInternal(transaction: Transaction): Flow<T?> {
            return when (type) {
                StatementType.SELECT, StatementType.EXEC, StatementType.SHOW, StatementType.PRAGMA -> flow {
                    (this@executeInternal as R2dbcPreparedStatementImpl).statement
                        .execute()
                        .collect { result ->
                            result
                                .map { row, _ -> transform(row) }
                                .collect { emit(it) }
                        }
                }
                StatementType.MULTI -> error("Executing statement with multiple results is unsupported")
                else -> flow {
                    (this@executeInternal as R2dbcPreparedStatementImpl).statement
                        .execute()
                        .collect { result ->
                            result.rowsUpdated.awaitFirstOrNull()
                        }
                }
            }
        }

        override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = stmt

        override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = listOf(
            args.map { (columnType, value) ->
                columnType.apply { nullable = true } to value
            }
        )
    })
}

suspend fun Transaction.execQuery(
    @Language("sql") stmt: String,
    args: Iterable<Pair<IColumnType<*>, Any?>> = emptyList(),
    explicitStatementType: StatementType? = null
) {
    execQuery(stmt, args, explicitStatementType) { }?.collect()
}

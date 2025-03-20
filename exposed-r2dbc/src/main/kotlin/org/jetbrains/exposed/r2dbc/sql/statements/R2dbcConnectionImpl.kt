package org.jetbrains.exposed.r2dbc.sql.statements

import io.r2dbc.spi.Connection
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import io.r2dbc.spi.Statement
import io.r2dbc.spi.ValidationDepth
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.r2dbc.sql.R2dbcScope
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcDatabaseMetadataImpl
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcExposedDatabaseMetadata
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcSavepoint
import org.jetbrains.exposed.r2dbc.sql.statements.api.getBoolean
import org.jetbrains.exposed.r2dbc.sql.statements.api.getString
import org.jetbrains.exposed.r2dbc.sql.vendors.metadata.MetadataProvider
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.reactivestreams.Publisher

/**
 * Class representing a wrapped R2DBC database [Connection].
 */
@Suppress("UnusedPrivateMember", "SpreadOperator")
class R2dbcConnectionImpl(
    override val connection: Publisher<out Connection>,
    private val vendorDialect: String,
    private val scope: R2dbcScope
) : R2dbcExposedConnection<Publisher<out Connection>> {
    private val metadataProvider: MetadataProvider = MetadataProvider.getProvider(vendorDialect)

    override suspend fun getCatalog(): String = withConnection {
        getCurrentCatalog(metadataProvider)
            ?: executeSQL(metadataProvider.getUsername()) { row, _ ->
                row.getString("USER_NAME")
            }?.singleOrNull()
            ?: ""
    }

    override suspend fun setCatalog(value: String) {
        withConnection { executeSQL(metadataProvider.getCatalog()) }
    }

    override suspend fun getSchema(): String = withConnection {
        getCurrentSchema(metadataProvider) ?: ""
    }

    override suspend fun getAutoCommit(): Boolean = withConnection { isAutoCommit }

    override suspend fun setAutoCommit(value: Boolean) {
        withConnection { setAutoCommit(value).awaitFirstOrNull() }
    }

    override suspend fun getReadOnly(): Boolean = withConnection {
        executeSQL(metadataProvider.getReadOnlyMode()) { row, _ ->
            row.getBoolean("READ_ONLY")
        }?.singleOrNull() == true
    }

    override suspend fun setReadOnly(value: Boolean) {
        withConnection { executeSQL(metadataProvider.setReadOnlyMode(value)) }
    }

    override suspend fun getTransactionIsolation(): Int = withConnection { transactionIsolationLevel.asInt() }

    override suspend fun setTransactionIsolation(value: Int) {
        withConnection { setTransactionIsolationLevel(value.asIsolationLevel()).awaitFirstOrNull() }
    }

    override suspend fun commit() {
        withConnection { commitTransaction().awaitFirstOrNull() }
    }

    override suspend fun rollback() {
        withConnection { rollbackTransaction().awaitFirstOrNull() }
    }

    override suspend fun isClosed(): Boolean = withConnection {
        !validate(ValidationDepth.LOCAL).awaitSingle() || !validate(ValidationDepth.REMOTE).awaitSingle()
    }

    override suspend fun close() {
        withConnection { close().awaitFirstOrNull() }
    }

    override suspend fun prepareStatement(
        sql: String,
        returnKeys: Boolean
    ): R2dbcPreparedStatementImpl = withConnection {
        val preparedSql = r2dbcPreparedSql(sql)
        val r2dbcStatement: Statement = if (returnKeys) {
            createStatement(preparedSql).returnGeneratedValues()
        } else {
            createStatement(preparedSql)
        }
//        TODO
//        val r2dbcQuery = if (returnKeys) "$preparedSql RETURNING *" else preparedSql
//        R2dbcPreparedStatementImpl(createStatement(r2dbcQuery), this, returnKeys, isInsert = r2dbcQuery.startsWith("INSERT"))
        R2dbcPreparedStatementImpl(r2dbcStatement, this, returnKeys)
    }

    override suspend fun prepareStatement(
        sql: String,
        columns: Array<String>
    ): R2dbcPreparedStatementImpl = withConnection {
        val preparedSql = r2dbcPreparedSql(sql)
        val r2dbcStatement = createStatement(preparedSql).returnGeneratedValues(*columns)
        R2dbcPreparedStatementImpl(r2dbcStatement, this, true)
    }

    private fun r2dbcPreparedSql(sql: String): String {
        val dialect = currentDialect
        val standardParametersSupported = dialect is MysqlDialect || dialect is OracleDialect
        if (standardParametersSupported) return sql

        val paramCount = sql.count { it == '?' }
        if (paramCount == 0) return sql

        val useCharParameter = currentDialect is SQLServerDialect
        var preparedSQL = sql
        for (i in 1..paramCount) {
            val newValue = if (useCharParameter) "@${'@' + i}" else "$$i"
            preparedSQL = preparedSQL.replaceFirst("?", newValue)
        }
        return preparedSQL
    }

    override suspend fun executeInBatch(sqls: List<String>) {
        val types = sqls.map { stmt ->
            StatementType.entries.find {
                stmt.startsWith(it.name, true)
            } ?: StatementType.OTHER
        }
        check(types.none { it == StatementType.SELECT }) {
            "SELECT statements are unsupported in batch execution"
        }

        withConnection {
            val batch = createBatch()
            sqls.forEach { sql -> batch.add(r2dbcPreparedSql(sql)) }
            batch.execute().awaitFirstOrNull()
        }
    }

    override suspend fun setSavepoint(name: String): ExposedSavepoint = withConnection {
        createSavepoint(name).awaitFirstOrNull()
        R2dbcSavepoint(name)
    }

    override suspend fun releaseSavepoint(savepoint: ExposedSavepoint) {
        withConnection {
            releaseSavepoint(savepoint.name).awaitFirstOrNull()
        }
    }

    override suspend fun rollback(savepoint: ExposedSavepoint) {
        withConnection {
            rollbackTransactionToSavepoint(savepoint.name).awaitFirstOrNull()
        }
    }

    private var metadataImpl: R2dbcDatabaseMetadataImpl? = null

    override suspend fun <T> metadata(body: suspend R2dbcExposedDatabaseMetadata.() -> T): T = withConnection {
        if (metadataImpl == null) {
            metadataImpl = R2dbcDatabaseMetadataImpl(getCatalog(), this, vendorDialect, scope)
        }
        metadataImpl!!.body()
    }

    private var localConnection: Connection? = null

    private suspend fun <T> withConnection(body: suspend Connection.() -> T): T = withContext(scope.coroutineContext) {
        if (localConnection == null) {
            localConnection = connection.awaitFirst().also {
                it.beginTransaction().awaitFirstOrNull()
            }
        }
        localConnection!!.body()
    }
}

@Suppress("MagicNumber")
internal val isolationLevelMapping: Map<IsolationLevel, Int> by lazy {
    mapOf(
        IsolationLevel.READ_UNCOMMITTED to 1,
        IsolationLevel.READ_COMMITTED to 2,
        IsolationLevel.REPEATABLE_READ to 4,
        IsolationLevel.SERIALIZABLE to 8
    )
}

internal fun IsolationLevel.asInt(): Int = isolationLevelMapping.getOrElse(this) {
    error("Unsupported IsolationLevel as Int: ${this.asSql()}")
}

internal fun Int.asIsolationLevel(): IsolationLevel = isolationLevelMapping.entries
    .firstOrNull { it.value == this }?.key
    ?: error("Unsupported Int as IsolationLevel: $this")

internal suspend fun Connection.executeSQL(sqlQuery: String) {
    if (sqlQuery.isEmpty()) return

    createStatement(sqlQuery).execute().awaitFirstOrNull()
}

internal suspend fun <T> Connection.executeSQL(
    sqlQuery: String,
    transform: (Row, RowMetadata) -> T
): List<T>? {
    if (sqlQuery.isEmpty()) return null

    return flow {
        createStatement(sqlQuery)
            .execute()
            .collect { row ->
                row.map { row, metadata ->
                    transform(row, metadata)
                }.collect { emit(it) }
            }
    }.toList()
}

internal suspend fun Connection.getCurrentCatalog(
    provider: MetadataProvider
): String? = executeSQL(provider.getCatalog()) { row, _ -> row.getString("TABLE_CAT") }?.singleOrNull()

internal suspend fun Connection.getCurrentSchema(
    provider: MetadataProvider
): String? = executeSQL(provider.getSchema()) { row, _ -> row.getString("TABLE_SCHEM") }?.singleOrNull()

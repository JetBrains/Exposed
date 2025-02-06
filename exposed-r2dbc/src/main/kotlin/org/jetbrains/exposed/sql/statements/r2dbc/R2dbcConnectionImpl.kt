package org.jetbrains.exposed.sql.statements.r2dbc

import io.r2dbc.spi.Connection
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Statement
import io.r2dbc.spi.ValidationDepth
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import org.jetbrains.exposed.sql.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.sql.statements.api.R2dbcExposedDatabaseMetadata
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.metadata.*
import org.reactivestreams.Publisher

/**
 * Class representing a wrapped R2DBC database [Connection].
 */
@Suppress("UnusedPrivateMember", "SpreadOperator")
class R2dbcConnectionImpl(
    vendorDialect: String,
    override val connection: Publisher<out Connection>,
    private val scope: R2dbcScope
) : R2dbcExposedConnection<Publisher<out Connection>> {
    private val metadataProvider: MetadataProvider = when (vendorDialect) {
        "Postgresql" -> PostgreSQLMetadata()
        "Mysql" -> MySQLMetadata()
        "Mariadb" -> MariaDBMetadata()
        "Oracle" -> OracleMetadata()
        "Mssql" -> SQLServerMetadata()
        else -> H2Metadata()
    }

    override suspend fun getCatalog(): String {
        return try { metadataProvider.getCatalog() } catch (_: Exception) { null } ?: metadataProvider.getUsername() ?: ""
    }

    override suspend fun setCatalog(value: String) {
        try { metadataProvider.setCatalog(value) } catch (_: Exception) {}
    }

    override suspend fun getSchema(): String {
        return try { metadataProvider.getSchema() } catch (_: Exception) { "" }
    }

    override suspend fun setSchema(value: String) {
        try { metadataProvider.setSchema(value) } catch (_: Exception) {}
    }

    override suspend fun getAutoCommit(): Boolean = withConnection { it.isAutoCommit }

    override suspend fun setAutoCommit(value: Boolean) {
        withConnection { it.setAutoCommit(value).awaitFirstOrNull() }
    }

    override suspend fun setReadOnly(value: Boolean) {
        metadataProvider.setReadOnlyMode(value)
    }

    override suspend fun getTransactionIsolation(): Int = withConnection { it.transactionIsolationLevel.asInt() }

    override suspend fun setTransactionIsolation(value: Int) {
        withConnection { it.setTransactionIsolationLevel(value.asIsolationLevel()).awaitFirstOrNull() }
    }

    override suspend fun commit() {
        withConnection { it.commitTransaction().awaitFirstOrNull() }
    }

    override suspend fun rollback() {
        withConnection { it.rollbackTransaction().awaitFirstOrNull() }
    }

    override suspend fun isClosed(): Boolean = withConnection {
        !it.validate(ValidationDepth.LOCAL).awaitSingle() || !it.validate(ValidationDepth.REMOTE).awaitSingle()
    }

    override suspend fun close() {
        withConnection { it.close().awaitFirstOrNull() }
    }

    override suspend fun prepareStatement(
        sql: String,
        returnKeys: Boolean
    ): R2dbcPreparedStatementImpl = withConnection {
        val preparedSql = r2dbcPreparedSql(sql)
        val r2dbcStatement: Statement = if (returnKeys) {
            it.createStatement(preparedSql).returnGeneratedValues()
        } else {
            it.createStatement(preparedSql)
        }
        R2dbcPreparedStatementImpl(r2dbcStatement, it, returnKeys)
    }

    override suspend fun prepareStatement(
        sql: String,
        columns: Array<String>
    ): R2dbcPreparedStatementImpl = withConnection {
        val preparedSql = r2dbcPreparedSql(sql)
        val r2dbcStatement = it.createStatement(preparedSql).returnGeneratedValues(*columns)
        R2dbcPreparedStatementImpl(r2dbcStatement, it, true)
    }

    private fun r2dbcPreparedSql(sql: String): String {
        if (currentDialect is MysqlDialect) return sql

        val paramCount = sql.count { it == '?' }
        if (paramCount == 0) return sql
        var preparedSQL = sql
        for (i in 1..paramCount) {
            preparedSQL = preparedSQL.replaceFirst("?", "\$$i")
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
            val batch = it.createBatch()
            sqls.forEach { sql -> batch.add(r2dbcPreparedSql(sql)) }
            batch.execute().awaitSingle()
        }
    }

    private val metadata: R2dbcDatabaseMetadataImpl by lazy {
        // is it really necessary to pass in the database name; should this be stored from user-defined parameters
//        R2dbcDatabaseMetadataImpl(getCatalog(), metadataProvider, it, scope)
        R2dbcDatabaseMetadataImpl("", metadataProvider, localConnection!!, scope)
    }

    // COMPARE this to use & return value of HDBC connection.metadata
    // if connection is provided here, is it needed in REDM constructor?
    override suspend fun <T> metadata(body: suspend R2dbcExposedDatabaseMetadata.() -> T): T = withConnection {
        metadata.body()
    }

    override suspend fun setSavepoint(name: String): ExposedSavepoint = withConnection {
        it.createSavepoint(name).awaitFirstOrNull()
        R2dbcSavepoint(name)
    }

    override suspend fun releaseSavepoint(savepoint: ExposedSavepoint) {
        withConnection {
            it.releaseSavepoint(savepoint.name).awaitFirstOrNull()
        }
    }

    override suspend fun rollback(savepoint: ExposedSavepoint) {
        withConnection {
            it.rollbackTransactionToSavepoint(savepoint.name).awaitFirstOrNull()
        }
    }

    private var localConnection: Connection? = null

    private suspend fun <T> withConnection(body: suspend (Connection) -> T): T = withContext(scope.coroutineContext) {
        if (localConnection == null) {
            localConnection = connection.awaitFirst().also {
                it.beginTransaction().awaitFirstOrNull()
            }
        }
        body(localConnection!!)
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

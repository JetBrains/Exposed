package org.jetbrains.exposed.sql.statements.r2dbc

import io.r2dbc.spi.Connection
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Statement
import io.r2dbc.spi.ValidationDepth
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.*
import org.reactivestreams.Publisher

/**
 * Class representing a wrapped R2DBC database [connection].
 */
@Suppress("UnusedPrivateMember", "SpreadOperator")
class R2dbcConnectionImpl(
    vendorDialect: String,
    override val connection: Publisher<out Connection>,
    private val scope: R2dbcScope
) : ExposedConnection<Publisher<out Connection>> {
    private val metadataProvider: MetadataProvider = when (vendorDialect) {
        "Postgresql" -> PostgreSQLMetadata()
        "Mysql" -> MySQLMetadata()
        "Mariadb" -> MariaDBMetadata()
        "Oracle" -> OracleMetadata()
        "Mssql" -> SQLServerMetadata()
        else -> H2Metadata()
    }

    override var catalog: String
        get() = try { metadataProvider.getCatalog() } catch (_: Exception) { null } ?: metadataProvider.getUsername() ?: ""
        set(value) { try { metadataProvider.setCatalog(value) } catch (_: Exception) {} }

    override var schema: String
        get() = try { metadataProvider.getSchema() } catch (_: Exception) { "" }
        set(value) { try { metadataProvider.setSchema(value) } catch (_: Exception) {} }

    override fun commit() {
        withConnection { it.commitTransaction().awaitFirstOrNull() }
    }

    override fun rollback() {
        withConnection { it.rollbackTransaction().awaitFirstOrNull() }
    }

    override val isClosed: Boolean
        get() = withConnection {
            !it.validate(ValidationDepth.LOCAL).awaitSingle() || !it.validate(ValidationDepth.REMOTE).awaitSingle()
        }

    override fun close() {
        withConnection { it.close().awaitFirstOrNull() }
    }

    override var autoCommit: Boolean
        get() = withConnection { it.isAutoCommit }
        set(value) { withConnection { it.setAutoCommit(value).awaitFirstOrNull() } }

    // is this only possible to set in beginTransaction()?
    override var readOnly: Boolean
        get() = TODO("Not yet implemented")
        set(value) { metadataProvider.setReadOnlyMode(value) }

    override var transactionIsolation: Int = -1
        get() {
            if (field == -1) {
                field = withConnection { it.transactionIsolationLevel.asInt() }
            }
            return field
        }
        set(value) {
            if (field != value) {
                withConnection {
                    it.setTransactionIsolationLevel(value.asIsolationLevel()).awaitFirstOrNull()
                }
                field = value
            }
        }

    private val metadata: R2dbcDatabaseMetadataImpl by lazy {
        withConnection { R2dbcDatabaseMetadataImpl(catalog, metadataProvider, it) }
    }

    override fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T = metadata.body()

    override fun prepareStatement(sql: String, returnKeys: Boolean): PreparedStatementApi = withConnection {
        val preparedSql = r2dbcPreparedSql(sql)
        val r2dbcStatement: Statement = if (returnKeys) {
            it.createStatement(preparedSql).returnGeneratedValues()
        } else {
            it.createStatement(preparedSql)
        }
        R2dbcPreparedStatementImpl(r2dbcStatement, it, returnKeys)
    }

    override fun prepareStatement(sql: String, columns: Array<String>): PreparedStatementApi = withConnection {
        val preparedSql = r2dbcPreparedSql(sql)
        val r2dbcStatement = it.createStatement(preparedSql).returnGeneratedValues(*columns)
        R2dbcPreparedStatementImpl(r2dbcStatement, it, true)
    }

    private fun r2dbcPreparedSql(sql: String): String {
        val paramCount = sql.count { it == '?' }
        if (paramCount == 0) return sql

        var preparedSQL = sql
        for (i in 1..paramCount) {
            preparedSQL = preparedSQL.replaceFirst("?", "\$$i")
        }
        return preparedSQL
    }

    override fun executeInBatch(sqls: List<String>) {
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

    override fun setSavepoint(name: String): ExposedSavepoint = withConnection {
        it.createSavepoint(name).awaitFirstOrNull()
        R2dbcSavepoint(name)
    }

    override fun releaseSavepoint(savepoint: ExposedSavepoint) {
        withConnection {
            it.releaseSavepoint(savepoint.name).awaitFirstOrNull()
        }
    }

    override fun rollback(savepoint: ExposedSavepoint) {
        withConnection {
            it.rollbackTransactionToSavepoint(savepoint.name).awaitFirstOrNull()
        }
    }

    private var localConnection: Connection? = null

    private fun <T> withConnection(body: suspend (Connection) -> T): T = runBlocking {
        withContext(scope.coroutineContext) {
            if (localConnection == null) {
                localConnection = connection.awaitFirst().also {
                    it.beginTransaction().awaitFirstOrNull()
                }
            }
            body(localConnection!!)
        }
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

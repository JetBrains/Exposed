package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import java.sql.PreparedStatement

class JdbcConnectionImpl(override val connection: Connection) : ExposedConnection<Connection> {

    // Oracle driver could throw exception on catalog
    override var catalog: String
        get() = try { connection.catalog } catch (_: Exception) { null } ?: connection.metaData.userName ?: ""
        set(value) { try { connection.catalog = value } catch (_: Exception) {} }

    override var schema: String
        get() = try { connection.schema } catch (_: Exception) { "" }
        set(value) { try { connection.schema = value } catch (_: Exception) {} }

    override fun commit() {
        connection.commit()
    }

    override fun rollback() {
        connection.rollback()
    }

    override val isClosed get() = connection.isClosed
    override fun close() {
        connection.close()
    }

    override var autoCommit: Boolean
        get() = connection.autoCommit
        set(value) { connection.autoCommit = value }

    override var readOnly: Boolean
        get() = connection.isReadOnly
        set(value) {
            connection.isReadOnly = value
        }

    override var transactionIsolation: Int = -1
        get() {
            if (field == -1) {
                synchronized(this) {
                    field = connection.transactionIsolation
                }
            }
            return field
        }
        set(value) {
            if (field != value) {
                connection.transactionIsolation = value
                field = value
            }
        }

    private val metadata by lazy {
        JdbcDatabaseMetadataImpl(catalog, connection.metaData)
    }

    override fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T = metadata.body()

    override fun prepareStatement(sql: String, returnKeys: Boolean): PreparedStatementApi {
        val generated = if (returnKeys) {
            PreparedStatement.RETURN_GENERATED_KEYS
        } else {
            PreparedStatement.NO_GENERATED_KEYS
        }
        return JdbcPreparedStatementImpl(connection.prepareStatement(sql, generated), returnKeys)
    }

    override fun prepareStatement(sql: String, columns: Array<String>): PreparedStatementApi {
        return JdbcPreparedStatementImpl(connection.prepareStatement(sql, columns), true)
    }

    override fun executeInBatch(sqls: List<String>) {
        val types = sqls.map { stmt ->
            StatementType.values().find {
                stmt.startsWith(it.name, true)
            } ?: StatementType.OTHER
        }

        check(types.none { it == StatementType.SELECT }) {
            "SELECT statements are unsupported in batch execution"
        }

        val type = types.distinct().singleOrNull() ?: StatementType.OTHER
        val prepStatement = object : Statement<Unit>(type, emptyList()) {

            override fun prepared(transaction: Transaction, sql: String): PreparedStatementApi {
                val originalStatement = super.prepared(transaction, sql.substringBefore('\n'))
                val batchStatement = connection.createStatement().apply {
                    sqls.forEach {
                        addBatch(it)
                    }
                }
                return object : PreparedStatementApi by originalStatement {
                    override fun closeIfPossible() {
                        batchStatement.close()
                        originalStatement.closeIfPossible()
                    }

                    override fun executeUpdate(): Int {
                        batchStatement.executeBatch()
                        return 0
                    }
                }
            }

            override fun PreparedStatementApi.executeInternal(transaction: Transaction) {
                executeUpdate()
            }

            override fun prepareSQL(transaction: Transaction): String = sqls.joinToString("\n")

            override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = emptyList()
        }

        prepStatement.execute(TransactionManager.current())
    }

    override fun setSavepoint(name: String): ExposedSavepoint {
        return JdbcSavepoint(name, connection.setSavepoint(name))
    }

    override fun releaseSavepoint(savepoint: ExposedSavepoint) {
        connection.releaseSavepoint((savepoint as JdbcSavepoint).savepoint)
    }

    override fun rollback(savepoint: ExposedSavepoint) {
        connection.rollback((savepoint as JdbcSavepoint).savepoint)
    }
}

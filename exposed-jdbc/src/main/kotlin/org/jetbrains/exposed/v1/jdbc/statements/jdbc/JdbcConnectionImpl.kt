package org.jetbrains.exposed.v1.jdbc.statements.jdbc

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.api.ExposedSavepoint
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcExposedDatabaseMetadata
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * Class representing a wrapped JDBC database [Connection].
 */
class JdbcConnectionImpl(override val connection: Connection) : ExposedConnection<Connection> {

    // Oracle driver could throw exception on catalog
    override var catalog: String
        get() = try {
            connection.catalog
        } catch (_: Exception) {
            null
        } ?: connection.metaData.userName ?: ""
        set(value) {
            try {
                connection.catalog = value
            } catch (_: Exception) {
            }
        }

    override var schema: String
        get() = try {
            connection.schema
        } catch (_: Exception) {
            ""
        }
        set(value) {
            try {
                connection.schema = value
            } catch (_: Exception) {
            }
        }

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
        set(value) {
            connection.autoCommit = value
        }

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

    override fun <T> metadata(body: JdbcExposedDatabaseMetadata.() -> T): T = metadata.body()

    override fun prepareStatement(sql: String, returnKeys: Boolean): JdbcPreparedStatementImpl {
        val generated = if (returnKeys) {
            PreparedStatement.RETURN_GENERATED_KEYS
        } else {
            PreparedStatement.NO_GENERATED_KEYS
        }
        return JdbcPreparedStatementImpl(connection.prepareStatement(sql, generated), returnKeys)
    }

    override fun prepareStatement(sql: String, columns: Array<String>): JdbcPreparedStatementImpl {
        return JdbcPreparedStatementImpl(connection.prepareStatement(sql, columns), true)
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

        val type = types.distinct().singleOrNull() ?: StatementType.OTHER
        val prepStatement = object : Statement<Unit>(type, emptyList()), BlockingExecutable<Unit, Statement<Unit>> {

            override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi {
                val originalStatement = super.prepared(transaction, sql.substringBefore('\n'))
                val batchStatement = connection.createStatement().apply {
                    sqls.forEach {
                        addBatch(it)
                    }
                }
                return object : JdbcPreparedStatementApi by originalStatement {
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

            override val statement: Statement<Unit>
                get() = this

            override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction) {
                executeUpdate()
            }

            override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = sqls.joinToString("\n")

            override fun arguments(): Iterable<Iterable<Pair<ColumnType<*>, Any?>>> = emptyList()
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

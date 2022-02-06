package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.jdbc.JdbcPreparedStatementImpl
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.ds.PGSimpleDataSource
import java.util.concurrent.CopyOnWriteArrayList

open class BasePostgresTest {

    val database = createDatabaseConnection()

    private fun createDatabaseConnection(): Database {
        val datasource = PGSimpleDataSource()
        datasource.user = PostgresSingletonContainer.username
        datasource.password = PostgresSingletonContainer.password
        datasource.setURL(PostgresSingletonContainer.jdbcUrl)
//        datasource.currentSchema = schema

        return Database.connect(
            datasource =  datasource,
            databaseConfig = DatabaseConfig {
                defaultRepetitionAttempts = 0
            }
        )
    }

    fun <T> withTransaction(block: Transaction.() -> T): ExecutionResult<T> {
        val interceptor = PostgresTestStatementInterceptor()
        val res = transaction {
            registerInterceptor(interceptor)
            block()
        }

        return ExecutionResult(
            result = res,
            interception = interceptor
        )
    }

    companion object {
        const val schema = "exposed"
    }
}

data class ExecutionResult<T>(
    val result: T,
    val interception: PostgresTestStatementInterceptor
)

class PostgresTestStatementInterceptor : StatementInterceptor {

    val executedPreparedStatements = CopyOnWriteArrayList<JdbcPreparedStatementImpl>()

    override fun afterExecution(
        transaction: Transaction,
        contexts: List<StatementContext>,
        executedStatement: PreparedStatementApi
    ) {
        //interested in statement which is in JdbcPreparedStatementImpl
        executedPreparedStatements.add(executedStatement as JdbcPreparedStatementImpl)
    }

    val executedStatements: List<String>
        get() = executedPreparedStatements.map { it.statement }
}

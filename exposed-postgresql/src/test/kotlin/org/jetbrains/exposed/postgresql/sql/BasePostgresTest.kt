package org.jetbrains.exposed.postgresql.sql

import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.ds.PGSimpleDataSource
import java.util.concurrent.CopyOnWriteArrayList

open class BasePostgresTest {

    fun <T> withTransaction(block: Transaction.() -> T): ExecutionResult<T> {
        val interceptor = PostgresTestStatementInterceptor()
        val res = transaction {
            addLogger(interceptor)
            block()
        }

        return ExecutionResult(
            result = res,
            interception = interceptor
        )
    }

    fun normalizeSQl(@Language("SQL") sql: String): String {
        return sql.trimIndent().replace("\n", "")
    }

    companion object {
        val datasource = PGSimpleDataSource().apply {
            user = PostgresSingletonContainer.username
            password = PostgresSingletonContainer.password
            setURL(PostgresSingletonContainer.jdbcUrl)
            currentSchema = "exposed"
        }

        val database = Database.connect(
            datasource = datasource,
            databaseConfig = DatabaseConfig {
                defaultRepetitionAttempts = 0
                defaultSchema = Schema(name = datasource.currentSchema!!)
            }
        )
    }
}

data class ExecutionResult<T>(
    val result: T,
    val interception: PostgresTestStatementInterceptor
)

class PostgresTestStatementInterceptor : StatementInterceptor, SqlLogger {

    val executedStatements = CopyOnWriteArrayList<String>()

    override fun log(context: StatementContext, transaction: Transaction) {
        val executedStatement = context.expandArgs(transaction)
        executedStatements.add(executedStatement)
    }
}

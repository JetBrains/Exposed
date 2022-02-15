package org.jetbrains.exposed.postgresql.sql

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
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

    fun selectByFullName(fullName: String): AnimeCharacterEntity? {
        return transaction {
            table.select { table.fullName.eq(fullName) }.singleOrNull()?.toEntity()
        }
    }

    fun countByFullName(fullName: String): Long {
        return transaction { table.select { table.fullName.eq(fullName) }.count() }
    }

    fun countByAnime(anime: String?): Long {
        return transaction { table.select { table.anime.eq(anime) }.count() }
    }

    fun insert(fullName: String, anime: String? = null): AnimeCharacterEntity {
        return withTransaction {
            table.insertReturning {
                values { insertStatement ->
                    insertStatement[table.fullName] = fullName
                    insertStatement[table.anime] = anime
                }
            }.toEntity()
        }.result
    }

    fun insert(startFullName: String, anime: String?, times: Int): List<AnimeCharacterEntity> {
        return (0 until times).mapIndexed { index, _ ->
            insert("$startFullName $index", anime)
        }
    }

    companion object {
        val table = AnimeCharacterPostgresTable
        val tableName = table.tableName
        val schema = "exposed"

        val datasource = PGSimpleDataSource().apply {
            user = PostgresSingletonContainer.username
            password = PostgresSingletonContainer.password
            setURL(PostgresSingletonContainer.jdbcUrl)
            currentSchema = schema
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
        val executedStatement = normalizeSQL(context.expandArgs(transaction))
        executedStatements.add(executedStatement)
    }

    fun exactlyOneStatement(): String  {
        assertThat(executedStatements).hasSize(1)

        return executedStatements.first()
    }
}

//strip multiple spaces, newlines etc
fun normalizeSQL(@Language("SQL") sql: String): String {
    return sql.trimIndent().trim().replace("\n", "").replace(" +".toRegex(), " ")
}
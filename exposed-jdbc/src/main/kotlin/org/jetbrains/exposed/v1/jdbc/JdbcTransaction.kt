package org.jetbrains.exposed.v1.jdbc

import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.v1.core.CompositeSqlLogger
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementResult
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.exceptions.LongQueryException
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.statements.executeIn
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.JdbcTransactionInterface
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.TimeUnit

/** Class representing a unit block of work that is performed on a database using a JDBC driver. */
open class JdbcTransaction(
    private val transactionImpl: JdbcTransactionInterface
) : Transaction(), JdbcTransactionInterface by transactionImpl {
    final override val db: Database = transactionImpl.db

    /**
     * The maximum amount of attempts that will be made to perform this `transaction` block.
     *
     * If this value is set to 1 and an SQLException happens, the exception will be thrown without performing a retry.
     *
     * @throws IllegalArgumentException If the amount of attempts is set to a value less than 1.
     */
    var maxAttempts: Int = db.transactionManager.defaultMaxAttempts
        set(value) {
            require(value > 0) { "maxAttempts must be set to perform at least 1 attempt." }
            field = value
        }

    /** The minimum number of milliseconds to wait before retrying this `transaction` if an SQLException happens. */
    var minRetryDelay: Long = db.transactionManager.defaultMinRetryDelay

    /** The maximum number of milliseconds to wait before retrying this `transaction` if an SQLException happens. */
    var maxRetryDelay: Long = db.transactionManager.defaultMaxRetryDelay

    /** The currently executing statement. */
    var currentStatement: JdbcPreparedStatementApi? = null

    /** The current statement for which an execution plan should be queried, but which should never itself be executed. */
    internal var explainStatement: Statement<*>? = null

    /** Whether this [JdbcTransaction] should prevent any statement execution from proceeding. */
    internal var blockStatementExecution: Boolean = false

    internal val executedStatements: MutableList<JdbcPreparedStatementApi> = arrayListOf()

    internal var openResultSetsCount: Int = 0

    internal val interceptors = arrayListOf<StatementInterceptor>()

    init {
        addLogger(db.config.sqlLogger)
        globalInterceptors // init interceptors
    }

    override fun commit() {
        val dataToStore = HashMap<Key<*>, Any?>()
        globalInterceptors.forEach {
            dataToStore.putAll(it.keepUserDataInTransactionStoreOnCommit(userdata))
            it.beforeCommit(this)
        }
        interceptors.forEach {
            dataToStore.putAll(it.keepUserDataInTransactionStoreOnCommit(userdata))
            it.beforeCommit(this)
        }
        transactionImpl.commit()
        userdata.clear()
        globalInterceptors.forEach { it.afterCommit(this) }
        interceptors.forEach { it.afterCommit(this) }
        userdata.putAll(dataToStore)
    }

    override fun rollback() {
        globalInterceptors.forEach { it.beforeRollback(this) }
        interceptors.forEach { it.beforeRollback(this) }
        transactionImpl.rollback()
        globalInterceptors.forEach { it.afterRollback(this) }
        interceptors.forEach { it.afterRollback(this) }
        userdata.clear()
    }

    /** Adds the specified [StatementInterceptor] to act on this transaction. */
    fun registerInterceptor(interceptor: StatementInterceptor): Boolean = interceptors.add(interceptor)

    /** Removes the specified [StatementInterceptor] from acting on this transaction. */
    fun unregisterInterceptor(interceptor: StatementInterceptor): Boolean = interceptors.remove(interceptor)

    @Suppress("MagicNumber")
    private fun describeStatement(delta: Long, stmt: String): String = "[${delta}ms] ${stmt.take(1024)}\n\n"

    /**
     * Executes the provided statement exactly, using the supplied [args] to set values to question mark
     * placeholders (if applicable).
     *
     * The [explicitStatementType] can be manually set to avoid iterating over [StatementType] values for the best match.
     *
     * @sample org.jetbrains.exposed.v1.sql.tests.shared.ParameterizationTests.testInsertWithQuotesAndGetItBack
     */
    fun exec(
        @Language("sql") stmt: String,
        args: Iterable<Pair<IColumnType<*>, Any?>> = emptyList(),
        explicitStatementType: StatementType? = null
    ) = exec(stmt, args, explicitStatementType) { }

    /**
     * Executes the provided statement exactly, using the supplied [args] to set values to question mark
     * placeholders (if applicable).
     *
     * The [explicitStatementType] can be manually set to avoid iterating over [StatementType] values for the best match.
     *
     * **Note** `StatementType.MULTI` can be set to enable execution of multiple concatenated statements.
     * However, if more than one [ResultSet] is generated, only the first will be used in the [transform] block.
     * Multiple statements in a single execute is not supported by all databases and some may require setting
     * a JDBC driver flag, like MySQL with `allowMultiQueries`. Please check the specific database documentation.
     *
     * @return The result of [transform] on the [ResultSet] generated by the statement execution,
     * or `null` if no [ResultSet] was returned by the database.
     * @sample org.jetbrains.exposed.v1.sql.tests.shared.ParameterizationTests.testInsertWithQuotesAndGetItBack
     * @sample org.jetbrains.exposed.v1.sql.tests.shared.TransactionExecTests.testExecWithSingleStatementQuery
     */
    fun <T : Any> exec(
        @Language("sql") stmt: String,
        args: Iterable<Pair<IColumnType<*>, Any?>> = emptyList(),
        explicitStatementType: StatementType? = null,
        transform: (ResultSet) -> T?
    ): T? {
        if (stmt.isEmpty()) return null

        val type = explicitStatementType
            ?: StatementType.entries.find { stmt.trim().startsWith(it.name, true) }
            ?: StatementType.OTHER

        return exec(object : Statement<T>(type, emptyList()), BlockingExecutable<T, Statement<T>> {
            override val statement: Statement<T>
                get() = this

            override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): T? {
                val result = when (type) {
                    StatementType.SELECT, StatementType.EXEC, StatementType.SHOW, StatementType.PRAGMA -> {
                        executeQuery()
                    }
                    StatementType.MULTI -> {
                        val result = executeMultiple().firstOrNull { it is StatementResult.Object }
                        (result as? StatementResult.Object)?.resultSet
                    }
                    else -> {
                        executeUpdate()
                        resultSet
                    }
                }
                return (result as? JdbcResult)?.result?.use { transform(it) }
            }

            override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = stmt

            override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = listOf(
                args.map { (columnType, value) ->
                    columnType.apply { nullable = true } to value
                }
            )
        })
    }

    /**
     * Executes the provided [Statement] object and returns the generated value.
     *
     * This function also updates its calling [JdbcTransaction] instance's statement count and overall duration,
     * as well as whether the execution time for [stmt] exceeds the threshold set by
     * `DatabaseConfig.warnLongQueriesDuration`. If [Transaction.debug] is set to `true`, these tracked values
     * are stored for each call in [Transaction.statementStats].
     */
    fun <T> exec(stmt: BlockingExecutable<T, *>): T? = exec(stmt) { it }

    /**
     * Provided statements will be executed in a batch.
     * Select statements are not supported as it's impossible to return multiple results.
     */
    fun execInBatch(stmts: List<String>) {
        connection.executeInBatch(stmts)
    }

    /**
     * Executes the provided [Statement] object, retrieves the generated value, then calls the specified
     * function [body] with this generated value as its argument and returns its result.
     *
     * This function also updates its calling [JdbcTransaction] instance's statement count and overall duration,
     * as well as whether the execution time for [stmt] exceeds the threshold set by
     * `DatabaseConfig.warnLongQueriesDuration`. If [Transaction.debug] is set to `true`, these tracked values
     * are stored for each call in [Transaction.statementStats].
     */
    fun <T, R> exec(stmt: BlockingExecutable<T, *>, body: Statement<T>.(T) -> R): R? {
        statementCount++

        val start = System.nanoTime()
        val answer = stmt.executeIn(this)
        val delta = (System.nanoTime() - start).let { TimeUnit.NANOSECONDS.toMillis(it) }

        val lazySQL = lazy(LazyThreadSafetyMode.NONE) {
            answer.second.map { it.sql(this) }.distinct().joinToString()
        }

        duration += delta

        if (debug) {
            statements.append(describeStatement(delta, lazySQL.value))
            statementStats.getOrPut(lazySQL.value) { 0 to 0L }.let { (count, time) ->
                statementStats[lazySQL.value] = (count + 1) to (time + delta)
            }
        }

        if (delta > (warnLongQueriesDuration ?: Long.MAX_VALUE)) {
            exposedLogger.warn("Long query: ${describeStatement(delta, lazySQL.value)}", LongQueryException())
        }

        return answer.first?.let { stmt.statement.body(it) }
    }

    /** Closes all previously executed statements and resets or releases any used database and/or driver resources. */
    fun closeExecutedStatements() {
        executedStatements.forEach {
            it.closeIfPossible()
        }
        openResultSetsCount = 0
        executedStatements.clear()
    }

    internal fun getRetryInterval(): Long = if (maxAttempts > 0) {
        maxOf((maxRetryDelay - minRetryDelay) / (maxAttempts + 1), 1)
    } else {
        0
    }

    companion object {
        val globalInterceptors = arrayListOf<GlobalStatementInterceptor>()

        init {
            ServiceLoader.load(
                GlobalStatementInterceptor::class.java,
                GlobalStatementInterceptor::class.java.classLoader
            ).forEach {
                globalInterceptors.add(it)
            }
        }
    }
}

/** Adds one or more [SqlLogger]s to this [JdbcTransaction]. */
fun JdbcTransaction.addLogger(vararg logger: SqlLogger): CompositeSqlLogger {
    return CompositeSqlLogger().apply {
        logger.forEach { this.addLogger(it) }
        registerInterceptor(this)
    }
}

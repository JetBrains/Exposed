package org.jetbrains.exposed.v1.r2dbc

import io.r2dbc.spi.R2dbcException
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
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
import org.jetbrains.exposed.v1.core.statements.api.ResultApi
import org.jetbrains.exposed.v1.exceptions.LongQueryException
import org.jetbrains.exposed.v1.r2dbc.statements.GlobalStatementInterceptorWrapper
import org.jetbrains.exposed.v1.r2dbc.statements.GlobalSuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.StatementInterceptorWrapper
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendExecutable
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult
import org.jetbrains.exposed.v1.r2dbc.statements.api.origin
import org.jetbrains.exposed.v1.r2dbc.statements.executeIn
import org.jetbrains.exposed.v1.r2dbc.transactions.R2dbcTransactionInterface
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import java.util.*
import java.util.concurrent.TimeUnit

/** Class representing a unit block of work that is performed on a database using an R2DBC driver. */
open class R2dbcTransaction(
    private val transactionImpl: R2dbcTransactionInterface
) : Transaction(), R2dbcTransactionInterface by transactionImpl {
    final override val db: R2dbcDatabase = transactionImpl.db

    override val transactionManager: TransactionManager
        get() = db.transactionManager

    /**
     * The maximum amount of attempts that will be made to perform this `transaction` block.
     *
     * If this value is set to 1 and an R2dbcException happens, the exception will be thrown without performing a retry.
     *
     * @throws IllegalArgumentException If the amount of attempts is set to a value less than 1.
     */
    var maxAttempts: Int = db.transactionManager.defaultMaxAttempts
        set(value) {
            require(value > 0) { "maxAttempts must be set to perform at least 1 attempt." }
            field = value
        }

    /** The minimum number of milliseconds to wait before retrying this `transaction` if an R2dbcException happens. */
    var minRetryDelay: Long = db.transactionManager.defaultMinRetryDelay

    /** The maximum number of milliseconds to wait before retrying this `transaction` if an R2dbcException happens. */
    var maxRetryDelay: Long = db.transactionManager.defaultMaxRetryDelay

    /** The currently executing statement. */
    var currentStatement: R2dbcPreparedStatementApi? = null

    /** The current statement for which an execution plan should be queried, but which should never itself be executed. */
    internal var explainStatement: Statement<*>? = null

    /** Whether this [R2dbcTransaction] should prevent any statement execution from proceeding. */
    internal var blockStatementExecution: Boolean = false

    internal val executedStatements: MutableList<R2dbcPreparedStatementApi> = arrayListOf()

    internal var openResultRowsCount: Int = 0

    internal val interceptors = arrayListOf<SuspendStatementInterceptor>()

    init {
        addLogger(db.config.sqlLogger)
        globalInterceptors // init interceptors
    }

    override suspend fun commit() {
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

    override suspend fun rollback() {
        globalInterceptors.forEach { it.beforeRollback(this) }
        interceptors.forEach { it.beforeRollback(this) }
        transactionImpl.rollback()
        globalInterceptors.forEach { it.afterRollback(this) }
        interceptors.forEach { it.afterRollback(this) }
        userdata.clear()
    }

    /** Adds the specified [StatementInterceptor] to act on this transaction. */
    fun registerInterceptor(interceptor: StatementInterceptor): Boolean {
        val suspendInterceptor = StatementInterceptorWrapper(interceptor)
        return interceptors.add(suspendInterceptor)
    }

    /** Adds the specified [SuspendStatementInterceptor] to act on this transaction. */
    fun registerInterceptor(interceptor: SuspendStatementInterceptor): Boolean = interceptors.add(interceptor)

    /** Removes the specified [StatementInterceptor] from acting on this transaction. */
    fun unregisterInterceptor(interceptor: StatementInterceptor): Boolean {
        val foundInterceptor = interceptors.firstOrNull {
            it is StatementInterceptorWrapper && it.originalInterceptor == interceptor
        }
        return foundInterceptor?.let { interceptors.remove(it) } ?: false
    }

    /** Removes the specified [SuspendStatementInterceptor] from acting on this transaction. */
    fun unregisterInterceptor(interceptor: SuspendStatementInterceptor): Boolean = interceptors.remove(interceptor)

    @Suppress("MagicNumber")
    private fun describeStatement(delta: Long, stmt: String): String = "[${delta}ms] ${stmt.take(1024)}\n\n"

    /**
     * Executes the provided statement exactly, using the supplied [args] to set values to question mark
     * placeholders (if applicable).
     *
     * The [explicitStatementType] can be manually set to avoid iterating over [StatementType] values for the best match.
     */
    suspend fun exec(
        @Language("sql") stmt: String,
        args: Iterable<Pair<IColumnType<*>, Any?>> = emptyList(),
        explicitStatementType: StatementType? = null
    ) {
        try {
            exec(stmt, args, explicitStatementType) { }?.collect()
        } catch (cause: R2dbcException) {
            throw ExposedR2dbcException(cause, stmt, this)
        }
    }

    internal fun asContext() = transactionManager.createTransactionContext(this)

    /**
     * Executes the provided statement exactly, using the supplied [args] to set values to question mark
     * placeholders (if applicable).
     *
     * The [explicitStatementType] can be manually set to avoid iterating over [StatementType] values for the best match.
     *
     * @return The result of [transform] on the [Row] generated by the statement execution,
     * or `null` if no [Row] was returned by the database.
     */
    suspend fun <T : Any> exec(
        @Language("sql") stmt: String,
        args: Iterable<Pair<IColumnType<*>, Any?>> = emptyList(),
        explicitStatementType: StatementType? = null,
        transform: (Row) -> T?
    ): Flow<T?>? {
        if (stmt.isEmpty()) return emptyFlow()

        val type = explicitStatementType ?: StatementType.entries.find { stmt.trim().startsWith(it.name, true) } ?: StatementType.OTHER

        return exec(object : Statement<Flow<T?>>(type, emptyList()), SuspendExecutable<Flow<T?>, Statement<Flow<T?>>> {
            override val statement: Statement<Flow<T?>>
                get() = this

            override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Flow<T?>? {
                val result: R2dbcResult = when (type) {
                    StatementType.SELECT, StatementType.EXEC, StatementType.SHOW, StatementType.PRAGMA -> {
                        executeQuery()
                    }
                    StatementType.MULTI -> {
                        val result = executeMultiple().first() as StatementResult.Object
                        result.resultSet as R2dbcResult
                    }
                    else -> {
                        executeUpdate()
                        getResultRow()
                    }
                } ?: return null

                return result.mapRows { transform(it.origin) }
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
     * This function also updates its calling [R2dbcTransaction] instance's statement count and overall duration,
     * as well as whether the execution time for [stmt] exceeds the threshold set by
     * `R2dbcDatabaseConfig.warnLongQueriesDuration`. If [debug] is set to `true`, these tracked values
     * are stored for each call in [statementStats].
     */
    suspend fun <T> exec(stmt: SuspendExecutable<T, *>): T? = exec(stmt) { it }

    /**
     * Provided statements will be executed in a batch.
     * Select statements are not supported as it's impossible to return multiple results.
     */
    suspend fun execInBatch(stmts: List<String>) {
        connection().executeInBatch(stmts)
    }

    /**
     * Executes the provided [Statement] object, retrieves the generated value, then calls the specified
     * function [body] with this generated value as its argument and returns its result.
     *
     * This function also updates its calling [R2dbcTransaction] instance's statement count and overall duration,
     * as well as whether the execution time for [stmt] exceeds the threshold set by [R2dbcDatabaseConfig.warnLongQueriesDuration].
     * If [debug] is set to `true`, these tracked values
     * are stored for each call in [statementStats].
     */
    suspend fun <T, R> exec(stmt: SuspendExecutable<T, *>, body: suspend Statement<T>.(T) -> R): R? {
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

    /**
     * Executes the provided query object, retrieves the generated result wrapped as a [R2dbcResult], then calls the specified
     * function [body] with this generated value as its argument and returns its result.
     *
     * This function also updates its calling [R2dbcTransaction] instance's statement count and overall duration,
     * as well as whether the execution time for [query] exceeds the threshold set by
     * `DatabaseConfig.warnLongQueriesDuration`. If [Transaction.debug] is set to `true`, these tracked values
     * are stored for each call in [Transaction.statementStats].
     *
     * ```kotlin
     * val allCompleteTasks = Tasks.selectAll().where { Tasks.isComplete eq true }
     *
     * val completeTitles = execQuery(allCompleteTasks) {
     *     it.mapRows { row ->
     *         row.origin.get("title")
     *     }
     *     .mapNotNull(Any?::toString)
     *     .toList()
     * }
     * ```
     *
     * @param query An executable statement that is expected to potentially returns results. This includes instances of
     * the [Query] class as well as executables defined using [explain] or any of the extension functions like
     * [insertReturning] or [updateReturning].
     * @return The result of calling [body] on the [R2dbcResult] generated by the query execution,
     * or `null` if no result was returned by the database.
     */
    suspend fun <R> execQuery(
        query: SuspendExecutable<ResultApi, *>,
        body: suspend Statement<ResultApi>.(R2dbcResult) -> R
    ): R? = exec(stmt = query) {
        body(it as R2dbcResult)
    }

    internal suspend fun execQuery(query: SuspendExecutable<ResultApi, *>): R2dbcResult = execQuery(query) { it }
        ?: error("A R2dbcResult was expected, but a result was not retrieved from the database")

    /** Closes all previously executed statements and resets or releases any used database and/or driver resources. */
    suspend fun closeExecutedStatements() {
        executedStatements.forEach {
            it.closeIfPossible()
        }
        openResultRowsCount = 0
        executedStatements.clear()
    }

    final override fun addLogger(vararg logger: SqlLogger): CompositeSqlLogger {
        return super.addLogger(*logger).apply {
            registerInterceptor(this)
        }
    }

    internal fun getRetryInterval(): Long = if (maxAttempts > 0) {
        maxOf((maxRetryDelay - minRetryDelay) / (maxAttempts + 1), 1)
    } else {
        0
    }

    companion object {
        val globalInterceptors = arrayListOf<GlobalSuspendStatementInterceptor>()

        init {
            ServiceLoader.load(
                GlobalSuspendStatementInterceptor::class.java,
                GlobalSuspendStatementInterceptor::class.java.classLoader
            ).forEach {
                globalInterceptors.add(it)
            }

            ServiceLoader.load(
                GlobalStatementInterceptor::class.java,
                GlobalStatementInterceptor::class.java.classLoader
            ).forEach {
                val suspendInterceptor = GlobalStatementInterceptorWrapper(it)
                globalInterceptors.add(suspendInterceptor)
            }
        }
    }
}

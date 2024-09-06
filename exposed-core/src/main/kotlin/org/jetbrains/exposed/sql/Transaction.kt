package org.jetbrains.exposed.sql

import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.exceptions.LongQueryException
import org.jetbrains.exposed.sql.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.StatementResult
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.DatabaseApi
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Represents a key for a value of type [T]. */
class Key<T>

/**
 * Class for storing transaction data that should remain available to the transaction scope even
 * after the transaction is committed.
 */
@Suppress("UNCHECKED_CAST")
open class UserDataHolder {
    /** A mapping of a [Key] to any data value. */
    protected val userdata = ConcurrentHashMap<Key<*>, Any?>()

    /** Maps the specified [key] to the specified [value]. */
    fun <T : Any> putUserData(key: Key<T>, value: T) {
        userdata[key] = value
    }

    /** Removes the specified [key] and its corresponding value. */
    fun <T : Any> removeUserData(key: Key<T>) = userdata.remove(key)

    /** Returns the value to which the specified [key] is mapped, as a value of type [T]. */
    fun <T : Any> getUserData(key: Key<T>): T? = userdata[key] as T?

    /**
     * Returns the value for the specified [key]. If the [key] is not found, the [init] function is called,
     * then its result is mapped to the [key] and returned.
     */
    fun <T : Any> getOrCreate(key: Key<T>, init: () -> T): T = userdata.getOrPut(key, init) as T
}

/** Class representing a unit block of work that is performed on a database. */
open class Transaction(
    private val transactionImpl: TransactionInterface
) : UserDataHolder(), TransactionInterface by transactionImpl {
    final override val db: DatabaseApi = transactionImpl.db

    /** The current number of statements executed in this transaction. */
    var statementCount: Int = 0

    /** The current total amount of time, in milliseconds, spent executing statements in this transaction. */
    var duration: Long = 0

    /** The threshold in milliseconds for query execution to exceed before logging a warning. */
    var warnLongQueriesDuration: Long? = db.config.warnLongQueriesDuration

    /** Whether tracked values like [statementCount] and [duration] should be stored in [statementStats] for debugging. */
    var debug = false

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

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("maxAttempts"),
        level = DeprecationLevel.ERROR
    )
    var repetitionAttempts: Int
        get() = maxAttempts
        set(value) { maxAttempts = value }

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("minRetryDelay"),
        level = DeprecationLevel.ERROR
    )
    var minRepetitionDelay: Long
        get() = minRetryDelay
        set(value) { minRetryDelay = value }

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("maxRetryDelay"),
        level = DeprecationLevel.ERROR
    )
    var maxRepetitionDelay: Long
        get() = maxRetryDelay
        set(value) { maxRetryDelay = value }

    /**
     * The number of seconds the JDBC driver should wait for a statement to execute in [Transaction] transaction before timing out.
     * Note Not all JDBC drivers implement this limit. Please check the driver documentation.
     */
    var queryTimeout: Int? = null

    /** The unique ID for this transaction. */
    val id by lazy { UUID.randomUUID().toString() }

    /** The currently executing statement. */
    var currentStatement: PreparedStatementApi? = null

    /** The current statement for which an execution plan should be queried, but which should never itself be executed. */
    internal var explainStatement: Statement<*>? = null

    /** Whether this [Transaction] should prevent any statement execution from proceeding. */
    internal var blockStatementExecution: Boolean = false

    internal val executedStatements: MutableList<PreparedStatementApi> = arrayListOf()
    internal var openResultSetsCount: Int = 0

    internal val interceptors = arrayListOf<StatementInterceptor>()

    /**
     * A [StringBuilder] containing string representations of previously executed statements
     * prefixed by their execution time in milliseconds.
     *
     * **Note:** [Transaction.debug] must be set to `true` for execution strings to be appended.
     */
    val statements = StringBuilder()

    /**
     * A mapping of previously executed statements in this transaction, with a string representation of
     * the prepared statement as the key and the statement count to execution time as the value.
     *
     * **Note:** [Transaction.debug] must be set to `true` for this mapping to be populated.
     */
    val statementStats by lazy { hashMapOf<String, Pair<Int, Long>>() }

    init {
        addLogger(db.config.sqlLogger)
        globalInterceptors // init interceptors
    }

    /** Adds the specified [StatementInterceptor] to act on this transaction. */
    fun registerInterceptor(interceptor: StatementInterceptor) = interceptors.add(interceptor)

    /** Removes the specified [StatementInterceptor] from acting on this transaction. */
    fun unregisterInterceptor(interceptor: StatementInterceptor) = interceptors.remove(interceptor)

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

    @Suppress("MagicNumber")
    private fun describeStatement(delta: Long, stmt: String): String = "[${delta}ms] ${stmt.take(1024)}\n\n"

    /**
     * Executes the provided statement exactly, using the supplied [args] to set values to question mark
     * placeholders (if applicable).
     *
     * The [explicitStatementType] can be manually set to avoid iterating over [StatementType] values for the best match.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.ParameterizationTests.testInsertWithQuotesAndGetItBack
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
     * @sample org.jetbrains.exposed.sql.tests.shared.ParameterizationTests.testInsertWithQuotesAndGetItBack
     * @sample org.jetbrains.exposed.sql.tests.shared.TransactionExecTests.testExecWithSingleStatementQuery
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

        return exec(object : Statement<T>(type, emptyList()) {
            override fun PreparedStatementApi.executeInternal(transaction: Transaction): T? {
                val result = when (type) {
                    StatementType.SELECT, StatementType.EXEC, StatementType.SHOW, StatementType.PRAGMA -> executeQuery()
                    StatementType.MULTI -> {
                        val result = executeMultiple().firstOrNull { it is StatementResult.Object }
                        (result as? StatementResult.Object)?.resultSet
                    }
                    else -> {
                        executeUpdate()
                        resultSet
                    }
                }
                return result?.use { transform(it) }
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
     * This function also updates its calling [Transaction] instance's statement count and overall duration,
     * as well as whether the execution time for [stmt] exceeds the threshold set by
     * `DatabaseConfig.warnLongQueriesDuration`. If [Transaction.debug] is set to `true`, these tracked values
     * are stored for each call in [Transaction.statementStats].
     */
    fun <T> exec(stmt: Statement<T>): T? = exec(stmt) { it }

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
     * This function also updates its calling [Transaction] instance's statement count and overall duration,
     * as well as whether the execution time for [stmt] exceeds the threshold set by
     * `DatabaseConfig.warnLongQueriesDuration`. If [Transaction.debug] is set to `true`, these tracked values
     * are stored for each call in [Transaction.statementStats].
     */
    fun <T, R> exec(stmt: Statement<T>, body: Statement<T>.(T) -> R): R? {
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

        return answer.first?.let { stmt.body(it) }
    }

    /** Returns the string identifier of a [table], based on its [Table.tableName] and [Table.alias], if applicable. */
    fun identity(table: Table): String =
        (table as? Alias<*>)?.let { "${identity(it.delegate)} ${db.identifierManager.quoteIfNecessary(it.alias)}" }
            ?: db.identifierManager.quoteIfNecessary(table.tableName.inProperCase())

    /** Returns the complete string identifier of a [column], based on its [Table.tableName] and [Column.name]. */
    fun fullIdentity(column: Column<*>): String = QueryBuilder(false).also {
        fullIdentity(column, it)
    }.toString()

    internal fun fullIdentity(column: Column<*>, queryBuilder: QueryBuilder) = queryBuilder {
        if (column.table is Alias<*>) {
            append(db.identifierManager.quoteIfNecessary(column.table.alias))
        } else {
            append(db.identifierManager.quoteIfNecessary(column.table.tableName.inProperCase()))
        }
        append('.')
        append(identity(column))
    }

    /** Returns the string identifier of a [column], based on its [Column.name]. */
    fun identity(column: Column<*>): String = db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(column.name)

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
        internal val globalInterceptors = arrayListOf<GlobalStatementInterceptor>()

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

package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Key<T>

@Suppress("UNCHECKED_CAST")
open class UserDataHolder {
    protected val userdata = ConcurrentHashMap<Key<*>, Any?>()

    fun <T : Any> putUserData(key: Key<T>, value: T) {
        userdata[key] = value
    }

    fun <T : Any> removeUserData(key: Key<T>) = userdata.remove(key)

    fun <T : Any> getUserData(key: Key<T>): T? = userdata[key] as T?

    fun <T : Any> getOrCreate(key: Key<T>, init: () -> T): T = userdata.getOrPut(key, init) as T
}

open class Transaction(private val transactionImpl: TransactionInterface) : UserDataHolder(), TransactionInterface by transactionImpl {

    init {
        Companion.globalInterceptors // init interceptors
    }

    internal val interceptors = arrayListOf<StatementInterceptor>()

    fun registerInterceptor(interceptor: StatementInterceptor) = interceptors.add(interceptor)

    fun unregisterInterceptor(interceptor: StatementInterceptor) = interceptors.remove(interceptor)

    var statementCount: Int = 0
    var duration: Long = 0
    var warnLongQueriesDuration: Long? = null
    var debug = false
    val id = UUID.randomUUID().toString()

    // currently executing statement. Used to log error properly
    var currentStatement: PreparedStatementApi? = null
    internal val executedStatements: MutableList<PreparedStatementApi> = arrayListOf()

    val statements = StringBuilder()

    // prepare statement as key and count to execution time as value
    val statementStats = hashMapOf<String, Pair<Int, Long>>()

    init {
        addLogger(Slf4jSqlDebugLogger)
    }

    override fun commit() {
        globalInterceptors.forEach { it.beforeCommit(this) }
        interceptors.forEach { it.beforeCommit(this) }
        transactionImpl.commit()
        globalInterceptors.forEach { it.afterCommit() }
        interceptors.forEach { it.afterCommit() }
        userdata.clear()
    }

    override fun rollback() {
        globalInterceptors.forEach { it.beforeRollback(this) }
        interceptors.forEach { it.beforeRollback(this) }
        transactionImpl.rollback()
        globalInterceptors.forEach { it.afterRollback() }
        interceptors.forEach { it.afterRollback() }
        userdata.clear()
    }

    private fun describeStatement(delta: Long, stmt: String): String = "[${delta}ms] ${stmt.take(1024)}\n\n"

    fun exec(stmt: String, args: Iterable<Pair<ColumnType, Any?>> = emptyList()) = exec(stmt, args) { }

    fun <T : Any> exec(stmt: String, args: Iterable<Pair<ColumnType, Any?>> = emptyList(), transform: (ResultSet) -> T): T? {
        if (stmt.isEmpty()) return null

        val type = StatementType.values().find {
            stmt.trim().startsWith(it.name, true)
        } ?: StatementType.OTHER

        return exec(object : Statement<T>(type, emptyList()) {
            override fun PreparedStatementApi.executeInternal(transaction: Transaction): T? {
                val result = when (type) {
                    StatementType.SELECT -> executeQuery()
                    else -> {
                        executeUpdate()
                        resultSet
                    }
                }
                return result?.let {
                    try {
                        transform(it)
                    } finally {
                        it.close()
                    }
                }
            }

            override fun prepareSQL(transaction: Transaction): String = stmt

            override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = listOf(args)
        })
    }

    fun <T> exec(stmt: Statement<T>): T? = exec(stmt) { it }

    /**
     * Provided statements will be executed in a batch.
     * Select statements are not supported as it's impossible to return multiple results.
     */
    fun execInBatch(stmts: List<String>) {
        connection.executeInBatch(stmts)
    }

    fun <T, R> exec(stmt: Statement<T>, body: Statement<T>.(T) -> R): R? {
        statementCount++

        val start = System.currentTimeMillis()
        val answer = stmt.executeIn(this)
        val delta = System.currentTimeMillis() - start

        val lazySQL = lazy(LazyThreadSafetyMode.NONE) {
            answer.second.map { it.sql(this) }.distinct().joinToString()
        }

        duration += delta

        if (debug) {
            statements.append(describeStatement(delta, lazySQL.value))
            statementStats.getOrPut(lazySQL.value, { 0 to 0L }).let { (count, time) ->
                statementStats[lazySQL.value] = (count + 1) to (time + delta)
            }
        }

        if (delta > warnLongQueriesDuration ?: Long.MAX_VALUE) {
            exposedLogger.warn("Long query: ${describeStatement(delta, lazySQL.value)}", RuntimeException())
        }

        return answer.first?.let { stmt.body(it) }
    }

    fun identity(table: Table): String =
            (table as? Alias<*>)?.let { "${identity(it.delegate)} ${db.identifierManager.quoteIfNecessary(it.alias)}"}
                ?: db.identifierManager.quoteIfNecessary(table.tableName.inProperCase())

    fun fullIdentity(column: Column<*>): String = QueryBuilder(false).also {
        fullIdentity(column, it)
    }.toString()

    internal fun fullIdentity(column: Column<*>, queryBuilder: QueryBuilder) = queryBuilder {
        if (column.table is Alias<*>)
            append(db.identifierManager.quoteIfNecessary(column.table.alias))
        else
            append(db.identifierManager.quoteIfNecessary(column.table.tableName.inProperCase()))
        append('.')
        append(identity(column))
    }


    fun identity(column: Column<*>): String = db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(column.name)

    fun closeExecutedStatements() {
        executedStatements.forEach {
            it.closeIfPossible()
        }
        executedStatements.clear()
    }

    companion object {
        internal val globalInterceptors = arrayListOf<GlobalStatementInterceptor>()

        init {
            ServiceLoader.load(GlobalStatementInterceptor::class.java, GlobalStatementInterceptor::class.java.classLoader).forEach {
                globalInterceptors.add(it)
            }
        }
    }
}


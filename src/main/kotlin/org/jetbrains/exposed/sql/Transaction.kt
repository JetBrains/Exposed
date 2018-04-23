package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.dao.EntityHook
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementMonitor
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionInterface
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap

class Key<T>
@Suppress("UNCHECKED_CAST")
open class UserDataHolder {
    protected val userdata = ConcurrentHashMap<Key<*>, Any?>()

    fun <T : Any> putUserData(key: Key<T>, value: T) {
        userdata[key] = value
    }

    fun <T:Any> removeUserData(key: Key<T>) = userdata.remove(key)

    fun <T:Any> getUserData(key: Key<T>) : T? = userdata[key] as T?

    fun <T:Any> getOrCreate(key: Key<T>, init: ()->T): T = userdata.getOrPut(key, init) as T
}

open class Transaction(private val transactionImpl: TransactionInterface): UserDataHolder(), TransactionInterface by transactionImpl {

    val monitor = StatementMonitor()

    val logger = CompositeSqlLogger()

    var statementCount: Int = 0
    var duration: Long = 0
    var warnLongQueriesDuration: Long? = null
    var debug = false
    val entityCache = EntityCache()

    // currently executing statement. Used to log error properly
    var currentStatement: PreparedStatement? = null
    internal val executedStatements: MutableList<PreparedStatement> = arrayListOf()

    val statements = StringBuilder()
    // prepare statement as key and count to execution time as value
    val statementStats = hashMapOf<String, Pair<Int,Long>>()

    init {
        logger.addLogger(Slf4jSqlLogger)
    }

    override fun commit() {
        val created = flushCache()
        EntityHook.alertSubscribers()
        val createdByHooks = flushCache()
        transactionImpl.commit()
        userdata.clear()
        EntityCache.invalidateGlobalCaches(created + createdByHooks)
    }

    fun flushCache(): List<Entity<*>> {
        with(entityCache) {
            val newEntities = inserts.flatMap { it.value }
            flush()
            return newEntities
        }
    }

    private fun describeStatement(delta: Long, stmt: String): String = "[${delta}ms] ${stmt.take(1024)}\n\n"

    fun exec(stmt: String) = exec(stmt, { })

    fun <T:Any> exec(stmt: String, transform: (ResultSet) -> T): T? {
        if (stmt.isEmpty()) return null

        val type = StatementType.values().find {
            stmt.trim().startsWith(it.name, true)
        } ?: StatementType.OTHER

        return exec(object : Statement<T>(type, emptyList()) {
            override fun PreparedStatement.executeInternal(transaction: Transaction): T? {
                when (type) {
                    StatementType.SELECT -> executeQuery()
                    else  -> executeUpdate()
                }
                return resultSet?.let {
                    try {
                        transform(it)
                    } finally {
                        it.close()
                    }
                }
            }

            override fun prepareSQL(transaction: Transaction): String = stmt

            override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = emptyList()
        })
    }

    fun <T> exec(stmt: Statement<T>): T? = exec(stmt, {it})

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

    internal fun quoteIfNecessary (identity: String) : String {
        if (identity.contains('.'))
            return identity.split('.').joinToString(".") {quoteTokenIfNecessary(it)}
        else {
            return quoteTokenIfNecessary(identity)
        }
    }

    // REVIEW
    internal fun cutIfNecessary (identity: String) = identity.substring(0, Math.min(currentDialect.identifierLengthLimit, identity.length))

    private fun quoteTokenIfNecessary(token: String) : String = if (db.needQuotes(token)) token.quoted else token

    fun identity(table: Table): String =
            (table as? Alias<*>)?.let { "${identity(it.delegate)} ${quoteIfNecessary(it.alias)}"} ?: quoteIfNecessary(table.tableName.inProperCase())

    fun fullIdentity(column: Column<*>): String =
            "${quoteIfNecessary(column.table.tableName.inProperCase())}.${identity(column)}"

    fun identity(column: Column<*>): String {
        val nameInProperCase = column.name.inProperCase()
        return if (db.shouldQuoteIdentifiers && nameInProperCase != column.name)
            column.name.quoted
        else quoteIfNecessary(nameInProperCase)
    }

    fun closeExecutedStatements() {
        executedStatements.forEach {
            if (!it.isClosed) it.close()
        }
        executedStatements.clear()
    }

    private val String.quoted get() = "${db.identityQuoteString}$this${db.identityQuoteString}".trim()
}


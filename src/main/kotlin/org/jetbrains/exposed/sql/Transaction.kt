package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementMonitor
import org.jetbrains.exposed.sql.statements.StatementType
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

class Key<T>()
@Suppress("UNCHECKED_CAST")
open class UserDataHolder() {
    private val userdata = HashMap<Key<*>, Any?>()

    fun <T:Any> putUserData(key: Key<T>, value: T?) {
        userdata[key] = value
    }

    fun <T:Any> getUserData(key: Key<T>) : T? {
        return userdata[key] as T?
    }

    fun <T:Any> getOrCreate(key: Key<T>, init: ()->T): T {
        if (userdata.containsKey(key)) {
            return userdata[key] as T
        }

        val new = init()
        userdata[key] = new
        return new
    }
}

open class Transaction(internal val transactionImpl: TransactionAbstraction, val db: Database = transactionImpl.db): UserDataHolder() {

    val connection: Connection get() = transactionImpl.connection

    val identityQuoteString by lazy(LazyThreadSafetyMode.NONE) { db.metadata.identifierQuoteString!! }
    val extraNameCharacters by lazy(LazyThreadSafetyMode.NONE) { db.metadata.extraNameCharacters!!}
    val keywords by lazy(LazyThreadSafetyMode.NONE) { db.metadata.sqlKeywords.split(',') }

    val monitor = StatementMonitor()

    val logger = CompositeSqlLogger()

    var statementCount: Int = 0
    var duration: Long = 0
    var warnLongQueriesDuration: Long = 2000
    var debug = false
    var selectsForUpdate = false

    val statements = StringBuilder()
    // prepare statement as key and count to execution time as value
    val statementStats = hashMapOf<String, Pair<Int,Long>>()

    init {
        logger.addLogger(Slf4jSqlLogger())
    }


    fun commit() {
        val created = flushCache()
        transactionImpl.commit()
        EntityCache.invalidateGlobalCaches(created)
    }

    fun flushCache(): List<Entity<*>> {
        with(EntityCache.getOrCreate(this)) {
            val newEntities = inserts.flatMap { it.value }
            flush()
            return newEntities
        }
    }

    fun rollback() {
        transactionImpl.rollback()
    }

    private fun describeStatement(delta: Long, stmt: String): String {
        return "[${delta}ms] ${stmt.take(1024)}\n\n"
    }

    fun exec(stmt: String) = exec(stmt, { })

    fun <T:Any> exec(stmt: String, transform: (ResultSet) -> T): T? {
        val type = StatementType.values().first { stmt.startsWith(it.name, true) }
        return exec(object : Statement<T>(type, emptyList()) {
            override fun PreparedStatement.executeInternal(transaction: Transaction): T? {
                when (type) {
                    StatementType.SELECT -> executeQuery()
                    else  -> executeUpdate()
                }
                return resultSet?.let { transform(it) }
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
            statementStats.getOrPut(lazySQL.value, { 0 to 0L }).let { pair ->
                statementStats[lazySQL.value] = (pair.first + 1) to (pair.second + delta)
            }
        }

        if (delta > warnLongQueriesDuration) {
            exposedLogger.warn("Long query: ${describeStatement(delta, lazySQL.value)}", RuntimeException())
        }

        return answer.first?.let { stmt.body(it) }
    }



    private fun String.isIdentifier() = !isEmpty() && first().isIdentifierStart() && all { it.isIdentifierStart() || it in '0'..'9' }
    private fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_' || this in extraNameCharacters

    private fun needQuotes (identity: String) : Boolean {
        return keywords.any { identity.equals(it, true) } || !identity.isIdentifier()
    }

    internal fun quoteIfNecessary (identity: String) : String {
        return (identity.split('.').map {quoteTokenIfNecessary(it)}).joinToString(".")
    }

    private fun quoteTokenIfNecessary(token: String) : String {
        return if (needQuotes(token)) "$identityQuoteString$token$identityQuoteString" else token
    }

    fun identity(table: Table): String {
        return (table as? Alias<*>)?.let { "${identity(it.delegate)} AS ${quoteIfNecessary(it.alias)}"} ?: quoteIfNecessary(table.tableName)
    }

    fun fullIdentity(column: Column<*>): String {
        return "${quoteIfNecessary(column.table.tableName)}.${quoteIfNecessary(column.name)}"
    }

    fun identity(column: Column<*>): String {
        return quoteIfNecessary(column.name)
    }

    fun prepareStatement(sql: String, autoincs: List<String>? = null): PreparedStatement {
        val flag = if (autoincs != null && autoincs.isNotEmpty())
            java.sql.Statement.RETURN_GENERATED_KEYS
        else
            java.sql.Statement.NO_GENERATED_KEYS
        return connection.prepareStatement(sql, flag)!!
    }

    fun close() = transactionImpl.close()

    companion object {

        fun currentOrNew(isolation: Int) = currentOrNull() ?: TransactionProvider.provider.newTransaction(isolation)

        fun currentOrNull() = TransactionProvider.provider.currentOrNull()

        fun current(): Transaction {
            return currentOrNull() ?: error("No transaction in context. Use Database.transaction() { ... }")
        }
    }
}

interface TransactionAbstraction {

    val db : Database

    val connection: Connection

    fun commit()

    fun rollback()

    fun close()
}

interface TransactionProvider {

    fun newTransaction(isolation: Int = Connection.TRANSACTION_REPEATABLE_READ) : Transaction

    fun close()

    fun currentOrNull(): Transaction?

    companion object {
        internal lateinit var provider: TransactionProvider
    }
}

class ThreadLocalTransactionProvider(private val db: Database) : TransactionProvider {
    override fun newTransaction(isolation: Int): Transaction = Transaction(ExposedTransaction(db, isolation)).apply {
        threadLocal.set(this)
    }

    val threadLocal = ThreadLocal<Transaction>()

    fun hasTransaction(): Boolean = currentOrNull() != null

    override fun currentOrNull(): Transaction? = threadLocal.get()

    override fun close() {
        threadLocal.set((currentOrNull()?.transactionImpl as? ExposedTransaction)?.outerTransaction)
    }

    private class ExposedTransaction(override val db: Database, isolation: Int) : TransactionAbstraction {
        override val connection: Connection by lazy(LazyThreadSafetyMode.NONE) {
            db.connector().apply {
                autoCommit = false
                transactionIsolation = isolation
            }
        }

        val outerTransaction = TransactionProvider.provider.currentOrNull()

        override fun commit() {
            connection.commit()
        }

        override fun rollback() {
            if (!connection.isClosed) {
                connection.rollback()
            }
        }

        override fun close() {
            connection.close()
            TransactionProvider.provider.close()
        }

    }
}

fun <T> transaction(statement: Transaction.() -> T): T = transaction(Connection.TRANSACTION_REPEATABLE_READ, 3, statement)

fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, statement: Transaction.() -> T): T {
    val outer = Transaction.currentOrNull()

    return if (outer != null) {
        outer.statement()
    }
    else {
        inTopLevelTransaction(transactionIsolation, repetitionAttempts, statement)
    }
}

fun <T> inTopLevelTransaction(transactionIsolation: Int, repetitionAttempts: Int, statement: Transaction.() -> T): T {
    var repetitions = 0

    while (true) {

        val transaction = Transaction.currentOrNew(transactionIsolation)

        try {
            val answer = transaction.statement()
            transaction.commit()
            return answer
        }
        catch (e: SQLException) {
            exposedLogger.info("Transaction attempt #$repetitions: ${e.message}", e)
            transaction.rollback()
            repetitions++
            if (repetitions >= repetitionAttempts) {
                throw e
            }
        }
        catch (e: Throwable) {
            transaction.rollback()
            throw e
        }
        finally {
            transaction.close()
        }
    }
}
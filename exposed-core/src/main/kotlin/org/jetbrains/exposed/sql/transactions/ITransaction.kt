package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import kotlin.collections.ArrayList

const val DEFAULT_ISOLATION_LEVEL = Connection.TRANSACTION_REPEATABLE_READ

const val DEFAULT_REPETITION_ATTEMPTS = 3

interface ITransaction {

    val db : Database

    val connection: ExposedConnection<*>

    val transactionIsolation: Int

    val outerTransaction: ITransaction?

    // currently executing statement. Used to log error properly
    var currentStatement: PreparedStatementApi?

    var debug: Boolean

    fun commit()

    fun rollback()

    fun close()

    fun getExecutedStatements(): MutableList<PreparedStatementApi>

    fun execInBatch(stmts: List<String>)

    fun closeExecutedStatements()

    fun <T> exec(stmt: Statement<T>): T?

    fun <T, R> exec(stmt: Statement<T>, body: Statement<T>.(T) -> R): R?

    fun exec(stmt: String): Unit?

    fun <T:Any> exec(stmt: String, transform: (ResultSet) -> T): T?

    fun getInterceptors(): ArrayList<StatementInterceptor>

    fun registerInterceptor(interceptor: StatementInterceptor): Boolean

    fun unregisterInterceptor(interceptor: StatementInterceptor): Boolean

    fun identity(table: Table): String

    fun identity(column: Column<*>): String

    fun fullIdentity(column: Column<*>): String

    fun fullIdentity(column: Column<*>, queryBuilder: QueryBuilder): Unit

    fun <T : Any> putUserData(key: Key<T>, value: T)

    fun <T:Any> removeUserData(key: Key<T>): Any?

    fun <T:Any> getUserData(key: Key<T>) : T?

    fun <T:Any> getOrCreate(key: Key<T>, init: ()->T): T

    companion object {
        internal val globalInterceptors = arrayListOf<GlobalStatementInterceptor>()

        init {
            ServiceLoader.load(GlobalStatementInterceptor::class.java, GlobalStatementInterceptor::class.java.classLoader).forEach {
                globalInterceptors.add(it)
            }
        }
    }
}

fun ITransaction.rollbackLoggingException(log: (Exception) -> Unit){
    try {
        rollback()
    } catch (e: Exception){
        log(e)
    }
}

inline fun ITransaction.closeLoggingException(log: (Exception) -> Unit){
    try {
        close()
    } catch (e: Exception){
        log(e)
    }
}

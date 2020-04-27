package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.ITransaction
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.text.append

class Key<T>

@Suppress("UNCHECKED_CAST")
abstract class AbstractTransaction(override val db: Database, override val transactionIsolation: Int, override val outerTransaction: ITransaction?, override var currentStatement: PreparedStatementApi?, override var debug: Boolean): ITransaction {

	init {
		ITransaction.globalInterceptors // init interceptors
	}
	internal val interceptors = arrayListOf<StatementInterceptor>()

	override fun registerInterceptor(interceptor: StatementInterceptor) = interceptors.add(interceptor)

	override fun unregisterInterceptor(interceptor: StatementInterceptor) = interceptors.remove(interceptor)

	private var statementCount: Int = 0
	private var duration: Long = 0
	private var warnLongQueriesDuration: Long? = null
	val id = UUID.randomUUID().toString()
	internal val executedStatements: MutableList<PreparedStatementApi> = arrayListOf()
	protected val userdata = ConcurrentHashMap<Key<*>, Any?>()

	val statements = StringBuilder()
	// prepare statement as key and count to execution time as value
	val statementStats = hashMapOf<String, Pair<Int,Long>>()

	init {
		addLogger(Slf4jSqlDebugLogger)
	}

	override fun commit() {
		ITransaction.globalInterceptors.forEach { it.beforeCommit(this) }
		interceptors.forEach { it.beforeCommit(this) }
		this.txCommit()
		ITransaction.globalInterceptors.forEach { it.afterCommit() }
		interceptors.forEach { it.afterCommit() }
		userdata.clear()
	}

	override fun rollback() {
		ITransaction.globalInterceptors.forEach { it.beforeRollback(this) }
		interceptors.forEach { it.beforeRollback(this) }
		this.txRollback()
		ITransaction.globalInterceptors.forEach { it.afterRollback() }
		interceptors.forEach { it.afterRollback() }
		userdata.clear()
	}

	private fun describeStatement(delta: Long, stmt: String): String = "[${delta}ms] ${stmt.take(1024)}\n\n"

	override fun exec(stmt: String) = exec(stmt, { })

	override fun <T:Any> exec(stmt: String, transform: (ResultSet) -> T): T? {
		if (stmt.isEmpty()) return null

		val type = StatementType.values().find {
			stmt.trim().startsWith(it.name, true)
		} ?: StatementType.OTHER

		return exec(object : Statement<T>(type, emptyList()) {
			override fun PreparedStatementApi.executeInternal(transaction: ITransaction): T? {
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

			override fun prepareSQL(transaction: ITransaction): String = stmt

			override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = emptyList()
		})
	}

	override fun <T> exec(stmt: Statement<T>): T? = exec(stmt, {it})

	/**
	 * Provided statements will be executed in a batch.
	 * Select statements are not supported as it's impossible to return multiple results.
	 */
	override fun execInBatch(stmts: List<String>) {
		connection.executeInBatch(stmts)
	}

	override fun <T, R> exec(stmt: Statement<T>, body: Statement<T>.(T) -> R): R? {
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

	override fun identity(table: Table): String =
			(table as? Alias<*>)?.let { "${identity(it.delegate)} ${db.identifierManager.quoteIfNecessary(it.alias)}"}
					?: db.identifierManager.quoteIfNecessary(table.tableName.inProperCase())

	override fun fullIdentity(column: Column<*>): String = QueryBuilder(false).also {
		fullIdentity(column, it)
	}.toString()

	override fun fullIdentity(column: Column<*>, queryBuilder: QueryBuilder) = queryBuilder {
		if (column.table is Alias<*>)
			append(db.identifierManager.quoteIfNecessary(column.table.alias))
		else
			append(db.identifierManager.quoteIfNecessary(column.table.tableName.inProperCase()))
		append('.')
		append(identity(column))
	}


	override fun identity(column: Column<*>): String = db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(column.name)

	override fun getExecutedStatements(): MutableList<PreparedStatementApi> {
		return executedStatements
	}

	override fun closeExecutedStatements() {
		executedStatements.forEach {
			it.closeIfPossible()
		}
		executedStatements.clear()
	}

	override fun getInterceptors(): ArrayList<StatementInterceptor> {
		return interceptors
	}

	override fun <T : Any> putUserData(key: Key<T>, value: T) {
		userdata[key] = value
	}

	override fun <T:Any> removeUserData(key: Key<T>) = userdata.remove(key)

	override fun <T:Any> getUserData(key: Key<T>) : T? = userdata[key] as T?

	override fun <T:Any> getOrCreate(key: Key<T>, init: ()->T): T = userdata.getOrPut(key, init) as T

}


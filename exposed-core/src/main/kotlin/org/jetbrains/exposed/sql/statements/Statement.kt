package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

internal object DefaultValueMarker {
    override fun toString(): String = "DEFAULT"
}

abstract class Statement<out T>(val type: StatementType, val targets: List<Table>) {

    abstract fun PreparedStatementApi.executeInternal(transaction: Transaction): T?

    abstract fun prepareSQL(transaction: Transaction, prepared: Boolean = true): String

    abstract fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>>

    open fun prepared(transaction: Transaction, sql: String): PreparedStatementApi =
        transaction.connection.prepareStatement(sql, false)

    open val isAlwaysBatch: Boolean = false

    fun execute(transaction: Transaction): T? = transaction.exec(this)

    internal fun executeIn(transaction: Transaction): Pair<T?, List<StatementContext>> {
        val arguments = arguments()
        val contexts = if (arguments.any()) {
            arguments.map { args ->
                val context = StatementContext(this, args)
                Transaction.globalInterceptors.forEach { it.beforeExecution(transaction, context) }
                transaction.interceptors.forEach { it.beforeExecution(transaction, context) }
                context
            }
        } else {
            val context = StatementContext(this, emptyList())
            Transaction.globalInterceptors.forEach { it.beforeExecution(transaction, context) }
            transaction.interceptors.forEach { it.beforeExecution(transaction, context) }
            listOf(context)
        }

        val statement = try {
            prepared(transaction, prepareSQL(transaction)).apply {
                timeout = transaction.queryTimeout
            }
        } catch (e: SQLException) {
            throw ExposedSQLException(e, contexts, transaction)
        }
        contexts.forEachIndexed { i, context ->
            statement.fillParameters(context.args)
            // REVIEW
            if (contexts.size > 1 || isAlwaysBatch) statement.addBatch()
        }
        if (!transaction.db.supportsMultipleResultSets) {
            transaction.closeExecutedStatements()
        }

        transaction.currentStatement = statement
        transaction.interceptors.forEach { it.afterStatementPrepared(transaction, statement) }
        val result = try {
            statement.executeInternal(transaction)
        } catch (cause: SQLException) {
            throw ExposedSQLException(cause, contexts, transaction)
        }
        transaction.currentStatement = null
        transaction.executedStatements.add(statement)

        Transaction.globalInterceptors.forEach { it.afterExecution(transaction, contexts, statement) }
        transaction.interceptors.forEach { it.afterExecution(transaction, contexts, statement) }
        return result to contexts
    }
}

class StatementContext(val statement: Statement<*>, val args: Iterable<Pair<IColumnType, Any?>>) {
    fun sql(transaction: Transaction) = statement.prepareSQL(transaction)
}

fun StatementContext.expandArgs(transaction: Transaction): String {
    val sql = sql(transaction)
    val iterator = args.iterator()
    if (!iterator.hasNext()) return sql

    return buildString {
        val quoteStack = Stack<Char>()
        var lastPos = 0
        var i = -1
        while (++i < sql.length) {
            val char = sql[i]
            if (char == '?') {
                if (quoteStack.isEmpty()) {
                    if (sql.getOrNull(i + 1) == '?') {
                        ++i
                        continue
                    }
                    append(sql.substring(lastPos, i))
                    lastPos = i + 1
                    val (col, value) = iterator.next()
                    append(col.valueToString(value))
                }
            } else if (char == '\'' || char == '\"') {
                if (quoteStack.isEmpty()) {
                    quoteStack.push(char)
                } else {
                    val currentQuote = quoteStack.peek()
                    if (currentQuote == char) {
                        quoteStack.pop()
                    } else {
                        quoteStack.push(char)
                    }
                }
            }
        }

        if (lastPos < sql.length) {
            append(sql.substring(lastPos))
        }
    }
}

/** Represents the groups that are used to classify the purpose of an SQL statement. */
enum class StatementGroup {
    /** Data definition language group. */
    DDL,

    /** Data manipulation language group. */
    DML
}

/**
 * Possible SQL statement types, most often represented by the leading word in the command syntax.
 *
 * @property group The [StatementGroup] associated with the SQL statement.
 */
enum class StatementType(val group: StatementGroup) {
    /** A SELECT statement to query data. */
    SELECT(StatementGroup.DML),

    /** An INSERT statement to insert new records. */
    INSERT(StatementGroup.DML),

    /** An UPDATE statement to modify existing records. */
    UPDATE(StatementGroup.DML),

    /** A DELETE statement to delete existing records. */
    DELETE(StatementGroup.DML),

    /** A GRANT statement to provide privileges on database objects. */
    GRANT(StatementGroup.DDL),

    /** A CREATE statement to create database objects. */
    CREATE(StatementGroup.DDL),

    /** An ALTER statement to modify database objects. */
    ALTER(StatementGroup.DDL),

    /** A TRUNCATE statement to delete data in a database object. */
    TRUNCATE(StatementGroup.DDL),

    /** A DROP statement to delete database objects. */
    DROP(StatementGroup.DDL),

    /** An EXEC statement to execute a stored procedure or command. */
    EXEC(StatementGroup.DML),

    /** A PRAGMA statement to configure or query the internal database state. */
    PRAGMA(StatementGroup.DML),

    /** A SHOW statement to provide information about database objects. */
    SHOW(StatementGroup.DML),

    /** Represents multiple statements of mixed types concatenated in a single string. */
    MULTI(StatementGroup.DML),

    /** Represents statements not covered by existing constants. */
    OTHER(StatementGroup.DDL),
}

/** Stores the result generated by a database after statement execution and indicates the form of the result. */
sealed class StatementResult {
    /** Stores the affected row [count] (or update count) retrieved on statement execution. */
    data class Count(val count: Int) : StatementResult()

    /** Stores the [resultSet] retrieved on statement execution. */
    data class Object(val resultSet: ResultSet) : StatementResult()
}

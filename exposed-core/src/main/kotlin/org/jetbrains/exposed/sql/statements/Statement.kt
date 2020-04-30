package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.ITransaction
import java.sql.SQLException
import java.util.*


internal object DefaultValueMarker {
    override fun toString(): String = "DEFAULT"
}

abstract class Statement<out T>(val type: StatementType, val targets: List<Table>) {

    abstract fun PreparedStatementApi.executeInternal(transaction: ITransaction): T?

    abstract fun prepareSQL(transaction: ITransaction): String

    abstract fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>>

    open fun prepared(transaction: ITransaction, sql: String) : PreparedStatementApi =
        transaction.connection.prepareStatement(sql, false)

    open val isAlwaysBatch: Boolean = false

    fun execute(transaction: ITransaction): T? = transaction.exec(this)

    internal fun executeIn(transaction: ITransaction): Pair<T?, List<StatementContext>> {
        val arguments = arguments()
        val contexts = if (arguments.count() > 0) {
            arguments.map { args ->
                val context = StatementContext(this, args)
                ITransaction.globalInterceptors.forEach { it.beforeExecution(transaction, context) }
                transaction.getInterceptors().forEach { it.beforeExecution(transaction, context) }
                context
            }
        } else {
            val context = StatementContext(this, emptyList())
            ITransaction.globalInterceptors.forEach { it.beforeExecution(transaction, context) }
            transaction.getInterceptors().forEach { it.beforeExecution(transaction, context) }
            listOf(context)
        }

        val statement = try {
            prepared(transaction, prepareSQL(transaction))
        } catch (e: SQLException) {
            throw ExposedSQLException(e, contexts, transaction)
        }
        contexts.forEachIndexed { i, context ->
            statement.fillParameters(context.args)
            // REVIEW
            if (contexts.size > 1 || isAlwaysBatch) statement.addBatch()
        }
        if (!transaction.db.supportsMultipleResultSets) transaction.closeExecutedStatements()

        transaction.currentStatement = statement
        val result = try {
            statement.executeInternal(transaction)
        } catch (e: SQLException) {
            throw ExposedSQLException(e, contexts, transaction)
        }
        transaction.currentStatement = null
        transaction.getExecutedStatements().add(statement)

        ITransaction.globalInterceptors.forEach { it.afterExecution(transaction, contexts, statement) }
        transaction.getInterceptors().forEach { it.afterExecution(transaction, contexts, statement) }
        return result to contexts
    }
}

class StatementContext(val statement: Statement<*>, val args: Iterable<Pair<IColumnType, Any?>>) {
    fun sql(transaction: ITransaction) = statement.prepareSQL(transaction)
}

fun StatementContext.expandArgs(transaction: ITransaction) : String {
    val sql = sql(transaction)
    val iterator = args.iterator()
    if (!iterator.hasNext())
        return sql

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
                continue
            }

            if (char == '\'' || char == '\"') {
                if (quoteStack.isEmpty()) {
                    quoteStack.push(char)
                } else {
                    val currentQuote = quoteStack.peek()
                    if (currentQuote == char)
                        quoteStack.pop()
                    else
                        quoteStack.push(char)
                }
            }
        }

        if (lastPos < sql.length)
            append(sql.substring(lastPos))
    }
}



enum class StatementGroup {
    DDL, DML
}

enum class StatementType(val group: StatementGroup) {
    INSERT(StatementGroup.DML), UPDATE(StatementGroup.DML), DELETE(StatementGroup.DML), SELECT(StatementGroup.DML),
    CREATE(StatementGroup.DDL), ALTER(StatementGroup.DDL), TRUNCATE(StatementGroup.DDL), DROP(StatementGroup.DDL),
    GRANT(StatementGroup.DDL), OTHER(StatementGroup.DDL)
}

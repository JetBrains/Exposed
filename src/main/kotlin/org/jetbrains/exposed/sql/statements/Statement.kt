package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import java.sql.PreparedStatement
import java.util.*


internal object DefaultValueMarker {
    override fun toString(): String = "DEFAULT"
}

abstract class Statement<out T>(val type: StatementType, val targets: List<Table>) {

    abstract fun PreparedStatement.executeInternal(transaction: Transaction): T?

    abstract fun prepareSQL(transaction: Transaction): String

    abstract fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>>

    open fun prepared(transaction: Transaction, sql: String) : PreparedStatement =
        transaction.connection.prepareStatement(sql, PreparedStatement.NO_GENERATED_KEYS)!!

    open val isAlwaysBatch: Boolean get() = false

    fun execute(transaction: Transaction): T? = transaction.exec(this)

    internal fun executeIn(transaction: Transaction): Pair<T?, List<StatementContext>> {
        try {
            transaction.monitor.register(transaction.logger)

            val arguments = arguments()
            val contexts = if (arguments.count() > 0) {
                arguments.map { args ->
                    val context = StatementContext(this, args)
                    transaction.monitor.notifyBeforeExecution(transaction, context)
                    context
                }
            } else {
                val context = StatementContext(this, emptyList())
                transaction.monitor.notifyBeforeExecution(transaction, context)
                listOf(context)
            }

            val statement = prepared(transaction, prepareSQL(transaction))
            contexts.forEachIndexed { i, context ->
                statement.fillParameters(context.args)
                // REVIEW
                if (contexts.size > 1 || isAlwaysBatch) statement.addBatch()
            }
            transaction.lastExecutedStatement?.run {
                if (!isClosed && !transaction.db.supportsMultipleResultSets) close()
            }
            transaction.currentStatement = statement
            val result = statement.executeInternal(transaction)
            transaction.currentStatement = null
            transaction.lastExecutedStatement = statement

            transaction.monitor.notifyAfterExecution(transaction, contexts, statement)
            return result to contexts
        } finally {
            transaction.monitor.unregister(transaction.logger)
        }
    }
}

class StatementContext(val statement: Statement<*>, val args: Iterable<Pair<IColumnType, Any?>>) {
    fun sql(transaction: Transaction) = statement.prepareSQL(transaction)
}

fun StatementContext.expandArgs(transaction: Transaction) : String {
    val sql = sql(transaction)
    val iterator = args.iterator()
    if (!iterator.hasNext())
        return sql

    return buildString {
        val quoteStack = Stack<Char>()
        var lastPos = 0
        for (i in 0..sql.length - 1) {
            val char = sql[i]
            if (char == '?') {
                if (quoteStack.isEmpty()) {
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

fun PreparedStatement.fillParameters(args: Iterable<Pair<IColumnType, Any?>>): Int {
    args.forEachIndexed { index, (c, v) ->
        c.setParameter(this, index + 1, c.valueToDB(v))
    }

    return args.count() + 1
}

enum class StatementGroup {
    DDL, DML
}

enum class StatementType(val group: StatementGroup) {
    INSERT(StatementGroup.DML), UPDATE(StatementGroup.DML), DELETE(StatementGroup.DML), SELECT(StatementGroup.DML),
    CREATE(StatementGroup.DDL), ALTER(StatementGroup.DDL), TRUNCATE(StatementGroup.DDL), DROP(StatementGroup.DDL),
    GRANT(StatementGroup.DDL), OTHER(StatementGroup.DDL)
}
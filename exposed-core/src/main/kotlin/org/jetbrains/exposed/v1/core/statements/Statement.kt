package org.jetbrains.exposed.v1.core.statements

import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.api.ResultApi
import java.util.*

internal object DefaultValueMarker {
    override fun toString(): String = "DEFAULT"
}

/**
 * Base class representing an SQL statement that can be executed.
 *
 * @param type The specific [StatementType], usually represented by the leading word in the command syntax.
 * @param targets Tables on which to perform the SQL statement.
 */
abstract class Statement<out T>(val type: StatementType, val targets: List<Table>) {
    /**
     * Returns the string representation of an SQL statement.
     *
     * If necessary, [transaction] can be used to ensure that database-specific syntax is used to generate the string.
     * To return a non-parameterized string, set [prepared] to `false`.
     */
    abstract fun prepareSQL(transaction: Transaction, prepared: Boolean = true): String

    /** Returns all mappings of columns and expression types to their values needed to prepare an SQL statement. */
    abstract fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>>
}

/** Holds information related to a particular [statement] and the [args] needed to prepare it for execution. */
class StatementContext(val statement: Statement<*>, val args: Iterable<Pair<IColumnType<*>, Any?>>) {
    /** Returns the string representation of the SQL statement associated with this [StatementContext]. */
    fun sql(transaction: Transaction) = statement.prepareSQL(transaction)
}

/**
 * Returns the string representation of [this] context's [Statement] with its argument values included
 * directly instead of parameter placeholders.
 */
fun StatementContext.expandArgs(transaction: Transaction): String {
    val sql = sql(transaction)
    val iterator = args.iterator()

    if (!iterator.hasNext()) return sql

    return buildString {
        val quoteStack = Stack<Char>()
        var lastPos = 0

        var i = -1
        while (++i < sql.length) {
            when (val char = sql[i]) {
                '?' if quoteStack.isEmpty() -> {
                    if (sql.getOrNull(i + 1) == '?') {
                        i++
                        continue
                    }
                    append(sql.substring(lastPos, i))
                    lastPos = i + 1
                    val (col, value) = iterator.next()
                    append((col as IColumnType<Any>).valueToString(value))
                }
                '\'', '\"' -> {
                    when {
                        quoteStack.isEmpty() -> quoteStack.push(char)
                        quoteStack.peek() == char -> quoteStack.pop()
                        else -> quoteStack.push(char)
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

    /** A MERGE statement to insert, update, or delete values by comparing data between source and destination tables. */
    MERGE(StatementGroup.DML),
}

private val cteMainStatementTypes = listOf(
    StatementType.SELECT, StatementType.INSERT, StatementType.UPDATE, StatementType.DELETE, StatementType.MERGE
)

/**
 * Returns the [StatementType] that matches the leading word of the provided SQL [stmt],
 * or `null` if no match is found.
 *
 * If [stmt] starts with a `WITH` clause, the type of the main statement that follows all common table
 * expressions is returned instead, for example [StatementType.SELECT] for `WITH cte AS (...) SELECT * FROM cte`.
 */
@InternalApi
fun inferStatementType(stmt: String): StatementType? {
    val sql = stmt.trim()
    val withKeyword = "WITH"
    if (!sql.startsWith(withKeyword, ignoreCase = true) || sql.getOrNull(withKeyword.length)?.isLetterOrDigit() == true) {
        return StatementType.entries.find { sql.startsWith(it.name, true) }
    }

    var depth = 0
    var index = withKeyword.length
    while (index < sql.length) {
        val char = sql[index]
        when {
            char == '\'' || char == '"' -> {
                val end = sql.indexOf(char, index + 1)
                if (end == -1) return null
                index = end
            }
            char == '(' -> depth++
            char == ')' -> depth--
            depth == 0 && char.isLetter() -> {
                var end = index
                while (end < sql.length && (sql[end].isLetterOrDigit() || sql[end] == '_')) end++
                val word = sql.substring(index, end)
                cteMainStatementTypes.find { it.name.equals(word, ignoreCase = true) }?.let { return it }
                index = end - 1
            }
        }
        index++
    }
    return null
}

/** Stores the result generated by a database after statement execution and indicates the form of the result. */
sealed class StatementResult {
    /** Stores the affected row [count] (or update count) retrieved on statement execution. */
    data class Count(val count: Int) : StatementResult()

    /** Stores the [resultSet] retrieved on statement execution. */
    data class Object(val resultSet: ResultApi) : StatementResult()
}

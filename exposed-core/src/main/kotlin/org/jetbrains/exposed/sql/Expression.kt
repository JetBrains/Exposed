package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.DefaultValueMarker
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * An object to which SQL expressions and values can be appended.
 */
class QueryBuilder(
    /** Whether the query is parameterized or not. */
    val prepared: Boolean
) {
    private val internalBuilder = StringBuilder()
    private val _args = mutableListOf<Pair<IColumnType, Any?>>()
    /** Returns the list of arguments used in this query. */
    val args: List<Pair<IColumnType, Any?>> get() = _args

    operator fun invoke(body: QueryBuilder.() -> Unit): Unit = body()

    /** Appends all the elements separated using [separator] and using the given [prefix] and [postfix] if supplied. */
    fun <T> Iterable<T>.appendTo(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = "",
        transform: QueryBuilder.(T) -> Unit
    ) {
        internalBuilder.append(prefix)
        forEachIndexed { index, element ->
            if (index > 0) internalBuilder.append(separator)
            transform(element)
        }
        internalBuilder.append(postfix)
    }

    /** Appends the specified [value] to this [QueryBuilder]. */
    fun append(value: Char): QueryBuilder = apply { internalBuilder.append(value) }

    /** Appends the specified [value] to this [QueryBuilder]. */
    fun append(value: String): QueryBuilder = apply { internalBuilder.append(value) }

    /** Appends the specified [value] to this [QueryBuilder]. */
    fun append(value: Expression<*>): QueryBuilder = apply(value::toQueryBuilder)

    /** Appends the receiver [Char] to this [QueryBuilder]. */
    operator fun Char.unaryPlus(): QueryBuilder = append(this)

    /** Appends the receiver [String] to this [QueryBuilder]. */
    operator fun String.unaryPlus(): QueryBuilder = append(this)

    /** Appends the receiver [Expression] to this [QueryBuilder]. */
    operator fun Expression<*>.unaryPlus(): QueryBuilder = append(this)

    /** Adds the specified [argument] as a value of the specified [column]. */
    fun registerArgument(column: ExpressionWithColumnType<*>, argument: Any?) {
        when (argument) {
            is Expression<*> -> {
                require(column.columnType.nullable || argument !is LiteralOp<*> || argument.value != null) {
                    "Column ${column.identity} does not support NULLs, so cannot register null literal $argument"
                }
                append(argument)
            }
            DefaultValueMarker -> {
                require(column is Column<*>) {
                    "DefaultValueMarker is supported only for Column<*>, given argument is $column"
                }
                append(TransactionManager.current().db.dialect.dataTypeProvider.processForDefaultValue(column.dbDefaultValue!!))
            }
            else -> {
                require(column.columnType.nullable || argument != null) {
                    "Column ${column.identity} does not support NULLs"
                }
                @Suppress("DEPRECATION")
                registerArgument(column.columnType, argument)
            }
        }
    }

    private val ExpressionWithColumnType<*>.identity: String
        get() = if (this is Column<*>) "${table.tableName}.$name" else toString()

    /** Adds the specified [argument] as a value of the specified [sqlType]. */
    @Deprecated(
        level = DeprecationLevel.WARNING,
        message = "Prefer registerArgument(Column, ...) and registerArgument(ExpressionWithColumnType, ...) since they have better error reporting"
    )
    @Suppress("DeprecatedCallableAddReplaceWith")
    fun registerArgument(sqlType: IColumnType, argument: Any?) {
        if (!prepared) {
            +sqlType.valueToString(argument)
            return
        }
        require(argument != null || sqlType.nullable) {
            "Can't register NULL value for non-nullable type $sqlType"
        }
        +"?"
        _args += sqlType to argument
    }

    /** Adds the specified sequence of [arguments] as values of the specified [sqlType]. */
    @Deprecated(
        message = "Replace with [SingleValueInListOp]",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("org.jetbrains.exposed.sql.ops.SingleValueInListOp")
    )
    fun <T> registerArguments(sqlType: IColumnType, arguments: Iterable<T>) {
        if (!prepared) {
            arguments.appendTo { +sqlType.valueToString(it) }
            return
        }
        arguments.appendTo {
            +"?"
            _args += sqlType to it
        }
    }

    override fun toString(): String = internalBuilder.toString()
}

/** Appends all arguments to this [QueryBuilder]. */
fun QueryBuilder.append(vararg expr: Any): QueryBuilder = apply {
    for (item in expr) {
        when (item) {
            is Char -> append(item)
            is String -> append(item)
            is Expression<*> -> append(item)
            else -> throw IllegalArgumentException("Can't append $item as it has unknown type")
        }
    }
}

/** Appends all the elements separated using [separator] and using the given [prefix] and [postfix] if supplied. */
fun <T> Iterable<T>.appendTo(
    builder: QueryBuilder,
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    transform: QueryBuilder.(T) -> Unit
): QueryBuilder = builder.apply { this@appendTo.appendTo(separator, prefix, postfix, transform) }

/**
 * Represents an SQL expression of type [T].
 */
abstract class Expression<T> {
    private val _hashCode: Int by lazy { toString().hashCode() }

    /** Appends the SQL representation of this expression to the specified [queryBuilder]. */
    abstract fun toQueryBuilder(queryBuilder: QueryBuilder)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Expression<*>) return false

        if (toString() != other.toString()) return false

        return true
    }

    override fun hashCode(): Int = _hashCode

    override fun toString(): String = QueryBuilder(false).append(this).toString()

    companion object {
        /** Builds a new [Expression] using the provided [builder]. */
        inline fun <T, E : Expression<T>> build(builder: SqlExpressionBuilder.() -> E): E = SqlExpressionBuilder.builder()
    }
}

/**
 * Represents an SQL expression of type [T], but with a specific column type.
 */
abstract class ExpressionWithColumnType<T> : Expression<T>() {
    /** Returns the column type of this expression. Used for operations with literals. */
    abstract val columnType: IColumnType
}

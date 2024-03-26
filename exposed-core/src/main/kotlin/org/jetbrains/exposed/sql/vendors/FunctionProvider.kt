package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*

/**
 * Provides definitions for all the supported SQL functions.
 * By default, definitions from the SQL standard are provided but if a vendor doesn't support a specific function, or it
 * is implemented differently, the corresponding function should be overridden.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class FunctionProvider {
    // Mathematical functions

    /**
     * SQL function that returns the next value of the specified sequence.
     *
     * @param seq Sequence that produces the value.
     * @param builder Query builder to append the SQL function to.
     */
    open fun nextVal(seq: Sequence, builder: QueryBuilder): Unit = builder {
        append(seq.identifier, ".NEXTVAL")
    }

    /**
     * SQL function that generates a random value uniformly distributed between 0 (inclusive) and 1 (exclusive).
     *
     * **Note:** Some vendors generate values outside this range, or ignore the given seed, check the documentation.
     *
     * @param seed Optional seed.
     */
    open fun random(seed: Int?): String = "RANDOM(${seed?.toString().orEmpty()})"

    // String functions

    /**
     * SQL function that returns the length of [expr], measured in characters, or `null` if [expr] is null.
     *
     * @param expr String expression to count characters in.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T : String?> charLength(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("CHAR_LENGTH(", expr, ")")
    }

    /**
     * SQL function that extracts a substring from the specified string expression.
     *
     * @param expr The expression to extract the substring from.
     * @param start The start of the substring.
     * @param length The length of the substring.
     * @param builder Query builder to append the SQL function to.
     */
    open fun <T : String?> substring(
        expr: Expression<T>,
        start: Expression<Int>,
        length: Expression<Int>,
        builder: QueryBuilder,
        prefix: String = "SUBSTRING"
    ): Unit = builder {
        append(prefix, "(", expr, ", ", start, ", ", length, ")")
    }

    /**
     * SQL function that concatenates multiple string expressions together with a given separator.
     *
     * @param separator Separator to use.
     * @param queryBuilder Query builder to append the SQL function to.
     * @param expr String expressions to concatenate.
     */
    open fun concat(separator: String, queryBuilder: QueryBuilder, vararg expr: Expression<*>): Unit = queryBuilder {
        if (separator == "") {
            append("CONCAT(")
        } else {
            append("CONCAT_WS('", separator, "',")
        }
        expr.appendTo { +it }
        append(")")
    }

    /**
     * SQL function that concatenates strings from a group into a single string.
     *
     * @param expr Group concat options.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("GROUP_CONCAT(")
        if (expr.distinct) {
            append("DISTINCT ")
        }
        append(expr.expr)
        if (expr.orderBy.isNotEmpty()) {
            append(" ORDER BY ")
            expr.orderBy.appendTo { (expression, sortOrder) ->
                currentDialect.dataTypeProvider.precessOrderByClause(this, expression, sortOrder)
            }
        }
        expr.separator?.let {
            append(" SEPARATOR '$it'")
        }
        append(")")
    }

    /**
     * SQL function that returns the index of the first occurrence of the given substring [substring]
     * in the string expression [expr]
     *
     * @param queryBuilder Query builder to append the SQL function to.
     * @param expr String expression to find the substring in.
     * @param substring: Substring to find
     * @return index of the first occurrence of [substring] in [expr] starting from 1
     * or 0 if [expr] doesn't contain [substring]
     */
    open fun <T : String?> locate(queryBuilder: QueryBuilder, expr: Expression<T>, substring: String) {
        throw UnsupportedByDialectException(
            "There's no generic SQL for LOCATE. There must be vendor specific implementation.", currentDialect
        )
    }

    // Pattern matching

    /**
     * Marker interface for the possible pattern matching modes.
     */
    interface MatchMode {
        /** SQL representation of the mode. */
        fun mode(): String
    }

    /**
     * SQL function that checks whether the given string expression matches the given pattern.
     *
     * **Note:** The `mode` parameter is not supported by all vendors, please check the documentation.
     *
     * @receiver Expression to check.
     * @param pattern Pattern the expression is checked against.
     * @param mode Match mode used to check the expression.
     */
    open fun <T : String?> Expression<T>.match(pattern: String, mode: MatchMode? = null): Op<Boolean> = with(
        SqlExpressionBuilder
    ) {
        this@match.like(pattern)
    }

    /**
     * SQL function that performs a pattern match of a given string expression against a given pattern.
     *
     * @param expr1 String expression to test.
     * @param pattern Pattern to match against.
     * @param caseSensitive Whether the matching is case-sensitive or not.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T : String?> regexp(
        expr1: Expression<T>,
        pattern: Expression<String>,
        caseSensitive: Boolean,
        queryBuilder: QueryBuilder
    ): Unit = queryBuilder {
        append("REGEXP_LIKE(", expr1, ", ", pattern, ", ")
        if (caseSensitive) {
            append("'c'")
        } else {
            append("'i'")
        }
        append(")")
    }

    // Date/Time functions

    /**
     * SQL function that extracts the year field from a given date.
     *
     * @param expr Expression to extract the year from.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("YEAR(")
        append(expr)
        append(")")
    }

    /**
     * SQL function that extracts the month field from a given date.
     * The returned value is a number between 1 and 12 both inclusive.
     *
     * @param expr Expression to extract the month from.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("MONTH(")
        append(expr)
        append(")")
    }

    /**
     * SQL function that extracts the day field from a given date.
     * The returned value is a number between 1 and 31 both inclusive.
     *
     * @param expr Expression to extract the day from.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DAY(")
        append(expr)
        append(")")
    }

    /**
     * SQL function that extracts the hour field from a given date.
     * The returned value is a number between 0 and 23 both inclusive.
     *
     * @param expr Expression to extract the hour from.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("HOUR(")
        append(expr)
        append(")")
    }

    /**
     * SQL function that extracts the minute field from a given date.
     * The returned value is a number between 0 and 59 both inclusive.
     *
     * @param expr Expression to extract the minute from.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("MINUTE(")
        append(expr)
        append(")")
    }

    /**
     * SQL function that extracts the second field from a given date.
     * The returned value is a number between 0 and 59 both inclusive.
     *
     * @param expr Expression to extract the second from.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("SECOND(")
        append(expr)
        append(")")
    }

    // Cast functions

    /**
     * SQL function that casts an expression to a specific type.
     *
     * @param expr Expression to cast.
     * @param type Type to cast the expression to.
     * @param builder Query builder to append the SQL function to.
     */
    open fun cast(
        expr: Expression<*>,
        type: IColumnType<*>,
        builder: QueryBuilder
    ): Unit = builder {
        append("CAST(", expr, " AS ", type.sqlType(), ")")
    }

    // Aggregate Functions for Statistics

    /**
     * SQL function that returns the population standard deviation of the non-null input values,
     * or `null` if there are no non-null values.
     *
     * @param expression Expression from which the population standard deviation is calculated.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> stdDevPop(expression: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STDDEV_POP(", expression, ")")
    }

    /**
     * SQL function that returns the sample standard deviation of the non-null input values,
     * or `null` if there are no non-null values.
     *
     * @param expression Expression from which the sample standard deviation is calculated.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> stdDevSamp(expression: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STDDEV_SAMP(", expression, ")")
    }

    /**
     * SQL function that returns the population variance of the non-null input values (square of the population standard deviation),
     * or `null` if there are no non-null values.
     *
     * @param expression Expression from which the population variance is calculated.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> varPop(expression: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("VAR_POP(", expression, ")")
    }

    /**
     * SQL function that returns the sample variance of the non-null input values (square of the sample standard deviation),
     * or `null` if there are no non-null values.
     *
     * @param expression Expression from which the sample variance is calculated.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> varSamp(expression: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("VAR_SAMP(", expression, ")")
    }

    // Array Functions

    /**
     * SQL function that returns a subarray of elements stored from between [lower] and [upper] bounds (inclusive),
     * or `null` if the stored array itself is null.
     *
     * @param expression Array expression from which the subarray is returned.
     * @param lower Lower bounds (inclusive) of a subarray.
     * @param upper Upper bounds (inclusive) of a subarray.
     * **Note** If either bounds is left `null`, the database will use the stored array's respective lower or upper limit.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> arraySlice(expression: Expression<T>, lower: Int?, upper: Int?, queryBuilder: QueryBuilder) {
        throw UnsupportedByDialectException(
            "There's no generic SQL for ARRAY_SLICE. There must be a vendor specific implementation", currentDialect
        )
    }

    // JSON Functions

    /**
     * SQL function that extracts data from a JSON object at the specified [path], either as a JSON representation or as a scalar value.
     *
     * @param expression Expression from which to extract JSON subcomponents matched by [path].
     * @param path String(s) representing JSON path/keys that match fields to be extracted.
     * **Note:** Multiple [path] arguments are not supported by all vendors; please check the documentation.
     * @param toScalar If `true`, the extracted result is a scalar or text value; otherwise, it is a JSON object.
     * @param jsonType Column type of [expression] to check, if casting to JSONB is required.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> jsonExtract(
        expression: Expression<T>,
        vararg path: String,
        toScalar: Boolean,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        throw UnsupportedByDialectException(
            "There's no generic SQL for JSON_EXTRACT. There must be a vendor specific implementation", currentDialect
        )
    }

    /**
     * SQL function that checks whether a [candidate] expression is contained within a JSON [target].
     *
     * @param target JSON expression being searched.
     * @param candidate Expression to search for in [target].
     * @param path String representing JSON path/keys that match specific fields to search for [candidate].
     * **Note:** A [path] argument is not supported by all vendors; please check the documentation.
     * @param jsonType Column type of [target] to check, if casting to JSONB is required.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun jsonContains(
        target: Expression<*>,
        candidate: Expression<*>,
        path: String?,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        throw UnsupportedByDialectException(
            "There's no generic SQL for JSON_CONTAINS. There must be a vendor specific implementation", currentDialect
        )
    }

    /**
     * SQL function that checks whether data exists within a JSON [expression] at the specified [path].
     *
     * @param expression JSON expression being checked.
     * @param path String(s) representing JSON path/keys that match fields to check for existing data.
     * **Note:** Multiple [path] arguments are not supported by all vendors; please check the documentation.
     * @param optional String representing any optional vendor-specific clause or argument.
     * @param jsonType Column type of [expression] to check, if casting to JSONB is required.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun jsonExists(
        expression: Expression<*>,
        vararg path: String,
        optional: String?,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        throw UnsupportedByDialectException(
            "There's no generic SQL for JSON_EXISTS. There must be a vendor specific implementation", currentDialect
        )
    }

    // Commands
    @Suppress("VariableNaming")
    open val DEFAULT_VALUE_EXPRESSION: String = "DEFAULT VALUES"

    /**
     * Returns the SQL command that inserts a new row into a table.
     *
     * **Note:** The `ignore` parameter is not supported by all vendors, please check the documentation.
     *
     * @param ignore Whether to ignore errors or not.
     * @param table Table to insert the new row into.
     * @param columns Columns to insert the values into.
     * @param expr Expression with the values to insert.
     * @param transaction Transaction where the operation is executed.
     */
    open fun insert(
        ignore: Boolean,
        table: Table,
        columns: List<Column<*>>,
        expr: String,
        transaction: Transaction
    ): String {
        if (ignore) {
            transaction.throwUnsupportedException("There's no generic SQL for INSERT IGNORE. There must be vendor specific implementation.")
        }

        val autoIncColumn = table.autoIncColumn

        val nextValExpression = autoIncColumn?.autoIncColumnType?.nextValExpression?.takeIf { autoIncColumn !in columns }
        val isInsertFromSelect = columns.isNotEmpty() && expr.isNotEmpty() && !expr.startsWith("VALUES")

        val (columnsToInsert, valuesExpr) = when {
            isInsertFromSelect -> columns to expr
            nextValExpression != null && columns.isNotEmpty() -> (columns + autoIncColumn) to expr.dropLast(1) + ", $nextValExpression)"
            nextValExpression != null -> listOf(autoIncColumn) to "VALUES ($nextValExpression)"
            columns.isNotEmpty() -> columns to expr
            else -> emptyList<Column<*>>() to DEFAULT_VALUE_EXPRESSION
        }
        val columnsExpr = columnsToInsert.takeIf { it.isNotEmpty() }?.joinToString(prefix = "(", postfix = ")") { transaction.identity(it) } ?: ""

        return "INSERT INTO ${transaction.identity(table)} $columnsExpr $valuesExpr"
    }

    /**
     * Returns the SQL command that updates one or more rows of a table.
     *
     * @param target Table to update values from.
     * @param columnsAndValues Pairs of column to update and values to update with.
     * @param limit Maximum number of rows to update.
     * @param where Condition that decides the rows to update.
     * @param transaction Transaction where the operation is executed.
     */
    open fun update(
        target: Table,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = with(QueryBuilder(true)) {
        +"UPDATE "
        target.describe(transaction, this)

        columnsAndValues.appendTo(this, prefix = " SET ") { (col, value) ->
            append("${transaction.identity(col)}=")
            registerArgument(col, value)
        }

        where?.let {
            +" WHERE "
            +it
        }
        limit?.let { +" LIMIT $it" }
        toString()
    }

    /**
     * Returns the SQL command that updates one or more rows of a join.
     *
     * @param targets Join to update values from.
     * @param columnsAndValues Pairs of column to update and values to update with.
     * @param limit Maximum number of rows to update.
     * @param where Condition that decides the rows to update.
     * @param transaction Transaction where the operation is executed.
     */
    open fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = transaction.throwUnsupportedException("UPDATE with a join clause is unsupported")

    protected fun QueryBuilder.appendJoinPartForUpdateClause(tableToUpdate: Table, targets: Join, transaction: Transaction) {
        +" FROM "
        val joinPartsToAppend = targets.joinParts.filter { it.joinPart != tableToUpdate }
        if (targets.table != tableToUpdate) {
            targets.table.describe(transaction, this)
            if (joinPartsToAppend.isNotEmpty()) {
                +", "
            }
        }

        joinPartsToAppend.appendTo(this, ", ") {
            it.joinPart.describe(transaction, this)
        }

        +" WHERE "
        targets.joinParts.appendTo(this, " AND ") {
            it.appendConditions(this)
        }
    }

    /**
     * Returns the SQL command that either inserts a new row into a table, or, if insertion would violate a unique constraint,
     * first deletes the existing row before inserting a new row.
     *
     * **Note:** This operation is not supported by all vendors, please check the documentation.
     *
     * @param table Table to either insert values into or delete values from then insert into.
     * @param columns Columns to replace the values in.
     * @param expression Expression with the values to use in replace.
     * @param transaction Transaction where the operation is executed.
     */
    open fun replace(
        table: Table,
        columns: List<Column<*>>,
        expression: String,
        transaction: Transaction,
        prepared: Boolean = true
    ): String = transaction.throwUnsupportedException("There's no generic SQL for REPLACE. There must be a vendor specific implementation.")

    /**
     * Returns the SQL command that either inserts a new row into a table, or updates the existing row if insertion would violate a unique constraint.
     *
     * **Note:** Vendors that do not support this operation directly implement the standard MERGE USING command.
     *
     * @param table Table to either insert values into or update values from.
     * @param data Pairs of columns to use for insert or update and values to insert or update.
     * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
     * @param onUpdateExclude List of specific columns to exclude from updating.
     * @param where Condition that determines which rows to update, if a unique violation is found.
     * @param transaction Transaction where the operation is executed.
     */
    open fun upsert(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        onUpdate: List<Pair<Column<*>, Expression<*>>>?,
        onUpdateExclude: List<Column<*>>?,
        where: Op<Boolean>?,
        transaction: Transaction,
        vararg keys: Column<*>
    ): String {
        if (where != null) {
            transaction.throwUnsupportedException("MERGE implementation of UPSERT doesn't support single WHERE clause")
        }
        val keyColumns = getKeyColumnsForUpsert(table, *keys)
        if (keyColumns.isNullOrEmpty()) {
            transaction.throwUnsupportedException("UPSERT requires a unique key or constraint as a conflict target")
        }

        val dataColumns = data.unzip().first
        val autoIncColumn = table.autoIncColumn
        val nextValExpression = autoIncColumn?.autoIncColumnType?.nextValExpression
        val dataColumnsWithoutAutoInc = autoIncColumn?.let { dataColumns - autoIncColumn } ?: dataColumns
        val updateColumns = getUpdateColumnsForUpsert(dataColumns, onUpdateExclude, keyColumns)

        return with(QueryBuilder(true)) {
            +"MERGE INTO "
            table.describe(transaction, this)
            +" T USING "
            data.appendTo(prefix = "(VALUES (", postfix = ")") { (column, value) ->
                registerArgument(column, value)
            }
            dataColumns.appendTo(prefix = ") S(", postfix = ")") { column ->
                append(transaction.identity(column))
            }

            +" ON "
            keyColumns.appendTo(separator = " AND ", prefix = "(", postfix = ")") { column ->
                val columnName = transaction.identity(column)
                append("T.$columnName=S.$columnName")
            }

            +" WHEN MATCHED THEN"
            appendUpdateToUpsertClause(table, updateColumns, onUpdate, transaction, isAliasNeeded = true)

            +" WHEN NOT MATCHED THEN INSERT "
            dataColumnsWithoutAutoInc.appendTo(prefix = "(") { column ->
                append(transaction.identity(column))
            }
            nextValExpression?.let {
                append(", ${transaction.identity(autoIncColumn)}")
            }
            dataColumnsWithoutAutoInc.appendTo(prefix = ") VALUES(") { column ->
                append("S.${transaction.identity(column)}")
            }
            nextValExpression?.let {
                append(", $it")
            }
            +")"
            toString()
        }
    }

    /**
     * Returns the columns to be used in the conflict condition of an upsert statement.
     */
    protected fun getKeyColumnsForUpsert(table: Table, vararg keys: Column<*>): List<Column<*>>? {
        return keys.toList().ifEmpty {
            table.primaryKey?.columns?.toList() ?: table.indices.firstOrNull { it.unique }?.columns
        }
    }

    /** Returns the columns to be used in the update clause of an upsert statement. */
    protected fun getUpdateColumnsForUpsert(
        dataColumns: List<Column<*>>,
        toExclude: List<Column<*>>?,
        keyColumns: List<Column<*>>?
    ): List<Column<*>> {
        val updateColumns = toExclude?.let { dataColumns - it.toSet() } ?: dataColumns
        return keyColumns?.let { keys ->
            updateColumns.filter { it !in keys }.ifEmpty { updateColumns }
        } ?: updateColumns
    }

    /**
     * Appends the complete default SQL insert (no ignore) command to [this] QueryBuilder.
     */
    protected fun QueryBuilder.appendInsertToUpsertClause(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction) {
        val valuesSql = if (data.isEmpty()) {
            ""
        } else {
            data.appendTo(QueryBuilder(true), prefix = "VALUES (", postfix = ")") { (column, value) ->
                registerArgument(column, value)
            }.toString()
        }
        val insertStatement = insert(false, table, data.unzip().first, valuesSql, transaction)

        +insertStatement
    }

    /**
     * Appends an SQL update command for a derived table (with or without alias identifiers) to [this] QueryBuilder.
     */
    protected fun QueryBuilder.appendUpdateToUpsertClause(
        table: Table,
        updateColumns: List<Column<*>>,
        onUpdate: List<Pair<Column<*>, Expression<*>>>?,
        transaction: Transaction,
        isAliasNeeded: Boolean
    ) {
        +" UPDATE SET "
        onUpdate?.appendTo { (columnToUpdate, updateExpression) ->
            if (isAliasNeeded) {
                val aliasExpression = updateExpression.toString().replace(transaction.identity(table), "T")
                append("T.${transaction.identity(columnToUpdate)}=$aliasExpression")
            } else {
                append("${transaction.identity(columnToUpdate)}=$updateExpression")
            }
        } ?: run {
            updateColumns.appendTo { column ->
                val columnName = transaction.identity(column)
                if (isAliasNeeded) {
                    append("T.$columnName=S.$columnName")
                } else {
                    append("$columnName=EXCLUDED.$columnName")
                }
            }
        }
    }

    /**
     * Returns the SQL command that deletes one or more rows of a table.
     *
     * **Note:** The `ignore` parameter is not supported by all vendors, please check the documentation.
     *
     * @param ignore Whether to ignore errors or not.
     * @param table Table to delete rows from.
     * @param where Condition that decides the rows to delete.
     * @param limit Maximum number of rows to delete.
     * @param transaction Transaction where the operation is executed.
     */
    open fun delete(
        ignore: Boolean,
        table: Table,
        where: String?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (ignore) {
            transaction.throwUnsupportedException("There's no generic SQL for DELETE IGNORE. There must be vendor specific implementation.")
        }
        return buildString {
            append("DELETE FROM ")
            append(transaction.identity(table))
            if (where != null) {
                append(" WHERE ")
                append(where)
            }
            if (limit != null) {
                append(" LIMIT ")
                append(limit)
            }
        }
    }

    /**
     * Returns the SQL command that limits and offsets the result of a query.
     *
     * @param size The limit of rows to return.
     * @param offset The number of rows to skip.
     * @param alreadyOrdered Whether the query is already ordered or not.
     */
    open fun queryLimit(size: Int, offset: Long, alreadyOrdered: Boolean): String = buildString {
        append("LIMIT $size")
        if (offset > 0) {
            append(" OFFSET $offset")
        }
    }

    /**
     * Returns the SQL command that obtains information about a statement execution plan.
     *
     * @param analyze Whether [internalStatement] should also be executed.
     * @param options Optional string of comma-separated parameters specific to the database.
     * @param internalStatement SQL string representing the statement to get information about.
     * @param transaction Transaction where the operation is executed.
     */
    open fun explain(
        analyze: Boolean,
        options: String?,
        internalStatement: String,
        transaction: Transaction
    ): String {
        return buildString {
            append("EXPLAIN ")
            if (analyze) {
                append("ANALYZE ")
            }
            options?.let {
                appendOptionsToExplain(it)
            }
            append(internalStatement)
        }
    }

    /** Appends optional parameters to an EXPLAIN query. */
    protected open fun StringBuilder.appendOptionsToExplain(options: String) { append("$options ") }
}

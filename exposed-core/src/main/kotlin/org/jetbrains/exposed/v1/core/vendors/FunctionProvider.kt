package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.MergeStatement
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.exceptions.throwUnsupportedException

/**
 * Provides definitions for all the supported SQL functions.
 * By default, definitions from the SQL standard are provided but if a vendor doesn't support a specific function, or it
 * is implemented differently, the corresponding function should be overridden.
 */
@Suppress("UnnecessaryAbstractClass", "TooManyFunctions")
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
    open fun <T : String?> Expression<T>.match(pattern: String, mode: MatchMode? = null): Op<Boolean> = this@match.like(pattern)

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
     * SQL function that extracts the date field from a given temporal expression.
     *
     * @param expr Expression to extract the year from.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> date(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATE(")
        append(expr)
        append(")")
    }

    /**
     * SQL function that extracts the time field from a given temporal expression.
     *
     * @param expr Expression to extract the year from.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> time(expr: Expression<T>, queryBuilder: QueryBuilder) {
        throw UnsupportedByDialectException(
            "There's no generic SQL for TIME. There must be a vendor-specific implementation.", currentDialect
        )
    }

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
     * SQL function that specifies a casting from one data type to the JSON type, if supported.
     *
     * @param expression Expression to cast.
     * @param jsonType The exact JSON column type to cast the expression to.
     * @param queryBuilder Query builder to append the SQL function to.
     */
    open fun <T> jsonCast(
        expression: Expression<T>,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        cast(expression, jsonType, queryBuilder)
    }

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
            currentDialect is OracleDialect -> {
                val oracleDefaults = table.columns.joinToString(prefix = "VALUES(", postfix = ")") { "DEFAULT" }
                emptyList<Column<*>>() to oracleDefaults
            }
            else -> emptyList<Column<*>>() to DEFAULT_VALUE_EXPRESSION
        }
        val columnsExpr = columnsToInsert.takeIf { it.isNotEmpty() }?.joinToString(prefix = "(", postfix = ")") { transaction.identity(it) } ?: ""

        return "INSERT INTO ${transaction.identity(table)} $columnsExpr $valuesExpr"
    }

    /**
     * Generates the SQL MERGE command which synchronizes two datasets by inserting new rows,
     * or updating/deleting existing ones in the target table based on data from another table.
     *
     * @param dest The table that will be modified.
     * @param source The table providing the data for modification.
     * @param transaction The transaction in which the operation will be executed.
     * @param clauses A list of `MergeStatement.When` instances describing the `when` clauses of the SQL command.
     * @param on The condition that determines whether to apply insertions or updates/deletions.
     */
    open fun merge(
        dest: Table,
        source: Table,
        transaction: Transaction,
        clauses: List<MergeStatement.Clause>,
        on: Op<Boolean>?
    ): String {
        validateMergeStatement(transaction, clauses)

        val onCondition = (
            on?.toString() ?: run {
                val targetKey = dest.primaryKey?.columns?.singleOrNull()
                val sourceKey = source.primaryKey?.columns?.singleOrNull()

                if (targetKey == null || sourceKey == null) {
                    transaction.throwUnsupportedException("MERGE requires an ON condition to be specified.")
                }

                "${transaction.fullIdentity(targetKey)}=${transaction.fullIdentity(sourceKey)}"
            }
            ).let { if (currentDialect is OracleDialect) "($it)" else it }

        return with(QueryBuilder(true)) {
            +"MERGE INTO ${transaction.identity(dest)} "
            +"USING ${transaction.identity(source)} "
            +"ON $onCondition "
            addClausesToMergeStatement(transaction, dest, clauses)
            toString()
        }
    }

    /**
     * Generates the SQL MERGE command which synchronizes two datasets by inserting new rows,
     * or updating/deleting existing ones in the target table based on data from subquery.
     *
     * @param dest The table that will be modified.
     * @param source The query providing the data for modification.
     * @param transaction The transaction in which the operation will be executed.
     * @param clauses A list of `MergeStatement.When` instances describing the `when` clauses of the SQL command.
     * @param on The condition that determines whether to apply insertions or updates/deletions.
     */
    open fun mergeSelect(
        dest: Table,
        source: QueryAlias,
        transaction: Transaction,
        clauses: List<MergeStatement.Clause>,
        on: Op<Boolean>,
        prepared: Boolean
    ): String {
        validateMergeStatement(transaction, clauses)

        val using = source.query.prepareSQL(transaction, prepared)

        val onRaw = if (currentDialect is OracleDialect) "($on)" else "$on"

        return with(QueryBuilder(true)) {
            +"MERGE INTO ${transaction.identity(dest)} "
            +"USING ( $using ) ${if (currentDialect is OracleDialect) "" else "as"} ${source.alias} "
            +"ON $onRaw "
            addClausesToMergeStatement(transaction, dest, clauses)
            toString()
        }
    }

    private fun validateMergeStatement(transaction: Transaction, clauses: List<MergeStatement.Clause>) {
        if (currentDialect !is OracleDialect) {
            if (clauses.any { it.deleteWhere != null }) {
                transaction.throwUnsupportedException("'deleteWhere' parameter can be used only as a part of Oracle SQL update clause statement.")
            }
        }

        if (currentDialect !is PostgreSQLDialect) {
            if (clauses.any { it.action == MergeStatement.ClauseAction.DO_NOTHING }) {
                transaction.throwUnsupportedException("DO NOTHING actions is supported only by Postgres database.")
            }

            if (clauses.any { it.overridingUserValue }) {
                transaction.throwUnsupportedException("OVERRIDING USER VALUE modifier is supported only by Postgres database.")
            }

            if (clauses.any { it.overridingSystemValue }) {
                transaction.throwUnsupportedException("OVERRIDING SYSTEM VALUE modifier is supported only by Postgres database.")
            }
        }
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

    protected fun QueryBuilder.appendJoinPart(
        targetTable: Table,
        targets: Join,
        transaction: Transaction,
        filterTargetTable: Boolean = true
    ) {
        val joinPartsToAppend = if (filterTargetTable) {
            targets.joinParts.filter { it.joinPart != targetTable }
        } else {
            targets.joinParts
        }
        if (targets.table != targetTable) {
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

    protected fun QueryBuilder.appendJoinPartForUpdateClause(tableToUpdate: Table, targets: Join, transaction: Transaction) {
        +" FROM "
        appendJoinPart(tableToUpdate, targets, transaction, true)
    }

    internal fun Join.checkJoinTypes(statementType: StatementType) {
        if (joinParts.any { it.joinType != JoinType.INNER }) {
            exposedLogger.warn("All tables in ${statementType.name} statement will be joined using inner join by default")
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
     * @param expression Expression with the values to use in the insert clause.
     * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
     * @param keyColumns Columns to include in the condition that determines a unique constraint match.
     * @param where Condition that determines which rows to update, if a unique violation is found.
     * @param transaction Transaction where the operation is executed.
     */
    open fun upsert(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        expression: String,
        onUpdate: List<Pair<Column<*>, Any?>>,
        keyColumns: List<Column<*>>,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        if (where != null) {
            transaction.throwUnsupportedException("MERGE implementation of UPSERT doesn't support single WHERE clause")
        }
        if (keyColumns.isEmpty()) {
            transaction.throwUnsupportedException("UPSERT requires a unique key or constraint as a conflict target")
        }

        val dataColumns = data.unzip().first
        val autoIncColumn = table.autoIncColumn
        val nextValExpression = autoIncColumn?.autoIncColumnType?.nextValExpression
        val dataColumnsWithoutAutoInc = autoIncColumn?.let { dataColumns - autoIncColumn } ?: dataColumns
        val tableIdentifier = transaction.identity(table)

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

            if (onUpdate.isNotEmpty()) {
                +" WHEN MATCHED THEN UPDATE SET "
                onUpdate.appendTo { (columnToUpdate, updateExpression) ->
                    append("T.${transaction.identity(columnToUpdate)}=")
                    when (updateExpression) {
                        !is Expression<*> -> registerArgument(columnToUpdate.columnType, updateExpression)
                        else -> append(updateExpression.toString().replace("$tableIdentifier.", "T."))
                    }
                }
            }

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
     * Appends to a [queryBuilder] the SQL syntax for a column that represents the same values from the INSERT clause
     * of an [upsert] command, which should be used in the UPDATE clause.
     *
     * @param columnName Name of the column for update.
     * @param queryBuilder Query builder to append the SQL syntax to.
     */
    open fun insertValue(columnName: String, queryBuilder: QueryBuilder) {
        queryBuilder { +"S.$columnName" }
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
     * Returns the SQL command that deletes one or more rows from a table in a join relation.
     *
     * **Note:** The `ignore` and `limit` parameters are not supported by all vendors; please check the documentation.
     *
     * @param ignore Whether to ignore errors or not.
     * @param targets Join to delete rows from.
     * @param targetTables Specific tables in the join to delete rows from.
     * @param where Condition that decides the rows to delete.
     * @param limit Maximum number of rows to delete.
     * @param transaction Transaction where the operation is executed.
     */
    open fun delete(
        ignore: Boolean,
        targets: Join,
        targetTables: List<Table>,
        where: Op<Boolean>?,
        limit: Int?,
        transaction: Transaction
    ): String = transaction.throwUnsupportedException("DELETE from a join relation is unsupported")

    /**
     * Returns the SQL command that limits and offsets the result of a query.
     *
     * @param size The limit of rows to return.
     * @param offset The number of rows to skip.
     * @param alreadyOrdered Whether the query is already ordered or not.
     */
    open fun queryLimitAndOffset(size: Int?, offset: Long, alreadyOrdered: Boolean): String = buildString {
        size?.let {
            append("LIMIT $size")
        }
        if (offset > 0) {
            size?.also { append(" ") }
            append("OFFSET $offset")
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
    protected open fun StringBuilder.appendOptionsToExplain(options: String) {
        append("$options ")
    }

    /**
     * Returns the SQL command that performs an insert, update, or delete, and also returns data from any modified rows.
     *
     * **Note:** This operation is not supported by all vendors, please check the documentation.
     *
     * @param mainSql SQL string representing the underlying statement before appending a RETURNING clause.
     * @param returning Columns and expressions to include in the returned result set.
     * @param transaction Transaction where the operation is executed.
     */
    open fun returning(
        mainSql: String,
        returning: List<Expression<*>>,
        transaction: Transaction
    ): String {
        transaction.throwUnsupportedException(
            "There's no generic SQL for a command with a RETURNING clause. There must be a vendor specific implementation."
        )
    }
}

@Suppress("NestedBlockDepth", "CyclomaticComplexMethod")
private fun QueryBuilder.addClausesToMergeStatement(transaction: Transaction, table: Table, clauses: List<MergeStatement.Clause>) {
    fun QueryBuilder.appendValueAlias(column: Column<*>, value: Any?) {
        when (value) {
            is Column<*> -> {
                val aliasExpression = transaction.fullIdentity(value)
                append(aliasExpression)
            }

            is Expression<*> -> {
                val aliasExpression = value.toString()
                append(aliasExpression)
            }

            else -> registerArgument(column.columnType, value)
        }
    }

    val autoIncColumn = table.autoIncColumn

    clauses.forEach { clause ->
        val whenMatchedOrNotPrefix = if (clause.type == MergeStatement.ClauseCondition.MATCHED) "WHEN MATCHED " else "WHEN NOT MATCHED "
        val defaultValuesStatementSupported = currentDialect !is H2Dialect
        when (clause.action) {
            MergeStatement.ClauseAction.INSERT -> {
                val nextValExpression = autoIncColumn?.autoIncColumnType?.nextValExpression?.takeIf { autoIncColumn !in clause.arguments.map { (key, _) -> key } }

                val extraArg = if (nextValExpression != null) listOf(autoIncColumn to nextValExpression) else emptyList()

                val allArguments = clause.arguments + extraArg
                +whenMatchedOrNotPrefix
                if (currentDialect !is OracleDialect) {
                    clause.and?.let { append("AND ($it) ") }
                }
                +"THEN INSERT "
                if (allArguments.isNotEmpty() || !defaultValuesStatementSupported) {
                    +allArguments.map { it.first }.joinToString(prefix = "(", postfix = ") ") {
                        transaction.identity(it)
                    }
                }
                if (clause.overridingSystemValue) {
                    +"OVERRIDING SYSTEM VALUE"
                }
                if (clause.overridingUserValue) {
                    +"OVERRIDING USER VALUE"
                }
                if (allArguments.isNotEmpty() || !defaultValuesStatementSupported) {
                    allArguments.appendTo(prefix = " VALUES (", postfix = ") ") { (column, value) ->
                        appendValueAlias(column, value)
                    }
                } else {
                    +"DEFAULT VALUES"
                }
                if (currentDialect is OracleDialect) {
                    clause.and?.let { append("WHERE ($it) ") }
                }
            }

            MergeStatement.ClauseAction.UPDATE -> {
                +whenMatchedOrNotPrefix
                if (currentDialect !is OracleDialect) {
                    clause.and?.let { append("AND ($it) ") }
                }
                +"THEN UPDATE SET "
                clause.arguments.appendTo(postfix = " ") { (column, expression) ->
                    append("${transaction.identity(column)}=")
                    appendValueAlias(column, expression)
                }
                if (currentDialect is OracleDialect) {
                    clause.and?.let { append("WHERE ($it) ") }
                }
                clause.deleteWhere?.let {
                    append("DELETE WHERE $it")
                }
            }

            MergeStatement.ClauseAction.DELETE -> {
                +whenMatchedOrNotPrefix
                clause.and?.let { append("AND ($it) ") }
                +"THEN DELETE "
            }

            MergeStatement.ClauseAction.DO_NOTHING -> {
                +whenMatchedOrNotPrefix
                clause.and?.let { append("AND ($it) ") }
                +"THEN DO NOTHING "
            }
        }
    }
}

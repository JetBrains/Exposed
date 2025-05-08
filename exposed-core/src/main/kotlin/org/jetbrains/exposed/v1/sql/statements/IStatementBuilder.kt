package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.sql.vendors.currentDialect

/** Represents all the DSL methods available when building SQL statements. */
@Suppress("TooManyFunctions")
interface IStatementBuilder {
    /**
     * Represents the SQL statement that deletes only rows in a table that match the provided [op].
     *
     * @param limit Maximum number of rows to delete.
     * @param op Condition that determines which rows to delete.
     * @return A [DeleteStatement] that can be executed.
     */
    fun <T : Table> T.deleteWhere(
        limit: Int? = null,
        op: T.(ISqlExpressionBuilder) -> Op<Boolean>
    ): DeleteStatement {
        return DeleteStatement(this, op(SqlExpressionBuilder), false, limit, emptyList())
    }

    /**
     * Represents the SQL statement that deletes only rows in a table that match the provided [op], while ignoring any
     * possible errors that occur during the process.
     *
     * **Note:** `DELETE IGNORE` is not supported by all vendors. Please check the documentation.
     *
     * @param limit Maximum number of rows to delete.
     * @param op Condition that determines which rows to delete.
     * @return A [DeleteStatement] that can be executed.
     */
    fun <T : Table> T.deleteIgnoreWhere(
        limit: Int? = null,
        op: T.(ISqlExpressionBuilder) -> Op<Boolean>
    ): DeleteStatement {
        return DeleteStatement(this, op(SqlExpressionBuilder), true, limit, emptyList())
    }

    /**
     * Represents the SQL statement that deletes all rows in a table.
     *
     * @return A [DeleteStatement] that can be executed.
     */
    fun <T : Table> T.deleteAll(): DeleteStatement = DeleteStatement(this)

    /**
     * Represents the SQL statement that deletes rows in a table and returns specified data from the deleted rows.
     *
     * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
     * @param where Condition that determines which rows to delete. If left as `null`, all rows in the table will be deleted.
     * @return A [ReturningStatement] that can be executed once iterated over.
     */
    fun <T : Table> T.deleteReturning(
        returning: List<Expression<*>> = columns,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
    ): ReturningStatement {
        val delete = DeleteStatement(this, where?.let { SqlExpressionBuilder.it() }, false, null)
        return ReturningStatement(this, returning, delete)
    }

    /**
     * Represents the SQL statement that deletes rows from a table in a join relation.
     *
     * @param targetTable The specific table from this join relation to delete rows from.
     * @param targetTables (Optional) Other tables from this join relation to delete rows from.
     * **Note** Targeting multiple tables for deletion is not supported by all vendors. Please check the documentation.
     * @param ignore Whether to ignore any possible errors that occur when deleting rows.
     * **Note** [ignore] is not supported by all vendors. Please check the documentation.
     * @param limit Maximum number of rows to delete.
     * **Note** [limit] is not supported by all vendors. Please check the documentation.
     * @param where Condition that determines which rows to delete. If left as `null`, all rows will be deleted.
     * @return A [DeleteStatement] that can be executed.
     */
    fun Join.delete(
        targetTable: Table,
        vararg targetTables: Table,
        ignore: Boolean = false,
        limit: Int? = null,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
    ): DeleteStatement {
        val targets = listOf(targetTable) + targetTables
        return DeleteStatement(this, where?.let { SqlExpressionBuilder.it() }, ignore, limit, targets)
    }

    /**
     * Represents the SQL statement that inserts a new row into a table.
     *
     * @return Am [InsertStatement] that can be executed.
     */
    fun <T : Table> T.insert(
        body: T.(UpdateBuilder<*>) -> Unit
    ): InsertStatement<Number> {
        return InsertStatement<Number>(this).apply { body(this) }
    }

    /**
     * Represents the SQL statement that inserts a new row into a table, while ignoring any possible errors that occur
     * during the process.
     *
     * For example, if the new row would violate a unique constraint, its insertion would be ignored.
     * **Note:** `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
     *
     * @return An [InsertStatement] that can be executed.
     */
    fun <T : Table> T.insertIgnore(
        body: T.(UpdateBuilder<*>) -> Unit
    ): InsertStatement<Long> {
        return InsertStatement<Long>(this, true).apply { body(this) }
    }

    /**
     * Represents the SQL statement that uses data retrieved from a [selectQuery] to insert new rows into a table.
     *
     * @param selectQuery Source `SELECT` query that provides the values to insert.
     * @param columns Columns to insert the values into. This defaults to all columns in the table that are not
     * auto-increment columns without a valid sequence to generate new values.
     * @return An [InsertSelectStatement] that can be executed.
     */
    fun <T : Table> T.insert(
        selectQuery: AbstractQuery<*>,
        columns: List<Column<*>>? = null
    ): InsertSelectStatement {
        val columnsToReplace = columns ?: this.columns.filter { it.isValidIfAutoIncrement() }
        return InsertSelectStatement(columnsToReplace, selectQuery, false)
    }

    /**
     * Represents the SQL statement that uses data retrieved from a [selectQuery] to insert new rows into a table,
     * while ignoring any possible errors that occur during the process.
     *
     * **Note:** `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
     *
     * @param selectQuery Source `SELECT` query that provides the values to insert.
     * @param columns Columns to insert the values into. This defaults to all columns in the table that are not
     * auto-increment columns without a valid sequence to generate new values.
     * @return An [InsertSelectStatement] that can be executed.
     */
    fun <T : Table> T.insertIgnore(
        selectQuery: AbstractQuery<*>,
        columns: List<Column<*>>? = null
    ): InsertSelectStatement {
        val columnsToReplace = columns ?: this.columns.filter { it.isValidIfAutoIncrement() }
        return InsertSelectStatement(columnsToReplace, selectQuery, true)
    }

    /**
     * Represents the SQL statement that inserts new rows into a table and returns specified data from the inserted rows.
     *
     * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
     * @param ignoreErrors Whether to ignore any possible errors that occur during the process.
     * Note `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
     * @return A [ReturningStatement] that can be executed once iterated over.
     */
    fun <T : Table> T.insertReturning(
        returning: List<Expression<*>> = columns,
        ignoreErrors: Boolean = false,
        body: T.(InsertStatement<Number>) -> Unit
    ): ReturningStatement {
        val insert = InsertStatement<Number>(this, ignoreErrors)
        body(insert)
        return ReturningStatement(this, returning, insert)
    }

    /**
     * Represents the SQL statement that batch inserts new rows into a table.
     *
     * @param ignoreErrors Whether to ignore errors or not.
     * **Note** [ignoreErrors] is not supported by all vendors. Please check the documentation.
     * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs)
     * should be returned.
     * @return A [BatchInsertStatement] that can be executed.
     */
    fun <T : Table, E> T.batchInsert(
        ignoreErrors: Boolean = false,
        shouldReturnGeneratedValues: Boolean = true,
        body: BatchInsertStatement.(E) -> Unit
    ): BatchInsertStatement {
        return if (currentDialect is SQLServerDialect && autoIncColumn != null) {
            SQLServerBatchInsertStatement(this, ignoreErrors, shouldReturnGeneratedValues)
        } else {
            BatchInsertStatement(this, ignoreErrors, shouldReturnGeneratedValues)
        }
    }

    /**
     * Represents the SQL statement that either inserts a new row into a table, or, if insertion would violate a unique constraint,
     * first deletes the existing row before inserting a new row.
     *
     * **Note:** This operation is not supported by all vendors, please check the documentation.
     *
     * @return A [ReplaceStatement] that can be executed.
     */
    fun <T : Table> T.replace(
        body: T.(UpdateBuilder<*>) -> Unit
    ): ReplaceStatement<Long> {
        return ReplaceStatement<Long>(this).apply { body(this) }
    }

    /**
     * Represents the SQL statement that uses data retrieved from a [selectQuery] to either insert a new row into a table,
     * or, if insertion would violate a unique constraint, first delete the existing row before inserting a new row.
     *
     * **Note:** This operation is not supported by all vendors, please check the documentation.
     *
     * @param selectQuery Source `SELECT` query that provides the values to insert.
     * @param columns Columns to either insert values into or delete values from then insert into. This defaults to all
     * columns in the table that are not auto-increment columns without a valid sequence to generate new values.
     * @return A [ReplaceSelectStatement] that can be executed.
     */
    fun <T : Table> T.replace(
        selectQuery: AbstractQuery<*>,
        columns: List<Column<*>>? = null
    ): ReplaceSelectStatement {
        val columnsToReplace = columns ?: this.columns.filter { it.isValidIfAutoIncrement() }
        return ReplaceSelectStatement(columnsToReplace, selectQuery)
    }

    /**
     * Represents the SQL statement that either batch inserts new rows into a table, or, if insertions violate unique constraints,
     * first deletes the existing rows before inserting new rows.
     *
     * **Note:** This operation is not supported by all vendors, please check the documentation.
     *
     * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs)
     * should be returned.
     * @return A [BatchReplaceStatement] that can be executed.
     */
    fun <T : Table, E : Any> T.batchReplace(
        shouldReturnGeneratedValues: Boolean = true,
        body: BatchReplaceStatement.(E) -> Unit
    ): BatchReplaceStatement {
        return BatchReplaceStatement(this, shouldReturnGeneratedValues)
    }

    /**
     * Represents the SQL statement that updates rows of a table.
     *
     * @param where Condition that determines which rows to update. If left `null`, all columns will be updated.
     * @param limit Maximum number of rows to update.
     * @return An [UpdateStatement] that can be executed.
     */
    fun <T : Table> T.update(
        where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
        limit: Int? = null,
        body: T.(UpdateStatement) -> Unit
    ): UpdateStatement {
        return UpdateStatement(this, limit, where?.let { SqlExpressionBuilder.it() }).apply { body(this) }
    }

    /**
     * Represents the SQL statement that updates rows of a join relation.
     *
     * @param where Condition that determines which rows to update. If left `null`, all columns will be updated.
     * @param limit Maximum number of rows to update.
     * @return An [UpdateStatement] that can be executed.
     */
    fun Join.update(
        where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
        limit: Int? = null,
        body: (UpdateStatement) -> Unit
    ): UpdateStatement {
        return UpdateStatement(this, limit, where?.let { SqlExpressionBuilder.it() }).apply(body)
    }

    /**
     * Represents the SQL statement that updates rows of a table and returns specified data from the updated rows.
     *
     * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
     * @param where Condition that determines which rows to update. If left `null`, all columns will be updated.
     * @return A [ReturningStatement] that can be executed once iterated over.
     */
    fun <T : Table> T.updateReturning(
        returning: List<Expression<*>> = columns,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
        body: T.(UpdateStatement) -> Unit
    ): ReturningStatement {
        val update = UpdateStatement(this, null, where?.let { SqlExpressionBuilder.it() })
        body(update)
        return ReturningStatement(this, returning, update)
    }

    /**
     * Represents the SQL statement that either inserts a new row into a table, or updates the existing row if insertion would violate a unique constraint.
     *
     * **Note:** Vendors that do not support this operation directly implement the standard MERGE USING command.
     *
     * @param keys (optional) Columns to include in the condition that determines a unique constraint match.
     * If no columns are provided, primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
     * @param onUpdate Lambda block with an [UpdateStatement] as its argument, allowing values to be assigned to the UPDATE clause.
     * To specify manually that the insert value should be used when updating a column, for example within an expression
     * or function, invoke `insertValue()` with the desired column as the function argument.
     * If left `null`, all columns will be updated with the values provided for the insert.
     * @param onUpdateExclude List of specific columns to exclude from updating.
     * If left `null`, all columns will be updated with the values provided for the insert.
     * @param where Condition that determines which rows to update, if a unique violation is found.
     * @return An [UpsertStatement] that can be executed.
     */
    fun <T : Table> T.upsert(
        vararg keys: Column<*>,
        onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
        onUpdateExclude: List<Column<*>>? = null,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
        body: T.(UpsertStatement<Long>) -> Unit
    ): UpsertStatement<Long> {
        return UpsertStatement<Long>(
            table = this,
            keys = keys,
            onUpdateExclude = onUpdateExclude,
            where = where?.let { SqlExpressionBuilder.it() }
        ).apply {
            onUpdate?.let { storeUpdateValues(it) }
            body(this)
        }
    }

    /**
     * Represents the SQL statement that either inserts a new row into a table, or updates the existing row if insertion would
     * violate a unique constraint, and also returns specified data from the modified rows.
     *
     * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are
     * provided, primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
     * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
     * @param onUpdate Lambda block with an [UpdateStatement] as its argument, allowing values to be assigned to the UPDATE clause.
     * To specify manually that the insert value should be used when updating a column, for example within an expression
     * or function, invoke `insertValue()` with the desired column as the function argument.
     * If left null, all columns will be updated with the values provided for the insert.
     * @param onUpdateExclude List of specific columns to exclude from updating.
     * If left null, all columns will be updated with the values provided for the insert.
     * @param where Condition that determines which rows to update, if a unique violation is found.
     * @return A [ReturningStatement] that can be executed once iterated over.
     */
    fun <T : Table> T.upsertReturning(
        vararg keys: Column<*>,
        returning: List<Expression<*>> = columns,
        onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
        onUpdateExclude: List<Column<*>>? = null,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
        body: T.(UpsertStatement<Long>) -> Unit
    ): ReturningStatement {
        val upsert = UpsertStatement<Long>(
            table = this,
            keys = keys,
            onUpdateExclude = onUpdateExclude,
            where = where?.let { SqlExpressionBuilder.it() }
        )
        onUpdate?.let { upsert.storeUpdateValues(it) }
        body(upsert)
        return ReturningStatement(this, returning, upsert)
    }

    /**
     * Represents the SQL statement that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
     *
     * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
     * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
     * @param onUpdate Lambda block with an [UpdateStatement] as its argument, allowing values to be assigned to the UPDATE clause.
     * To specify manually that the insert value should be used when updating a column, for example within an expression
     * or function, invoke `insertValue()` with the desired column as the function argument.
     * If left null, all columns will be updated with the values provided for the insert.
     * @param onUpdateExclude List of specific columns to exclude from updating.
     * If left null, all columns will be updated with the values provided for the insert.
     * @param where Condition that determines which rows to update, if a unique violation is found.
     * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs)
     * should be returned.
     * @return A [BatchUpsertStatement] that can be executed.
     */
    @Suppress("LongParameterList")
    fun <T : Table, E> T.batchUpsert(
        onUpdateList: List<Pair<Column<*>, Any?>>? = null,
        onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
        onUpdateExclude: List<Column<*>>? = null,
        where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
        shouldReturnGeneratedValues: Boolean = true,
        vararg keys: Column<*>,
        body: BatchUpsertStatement.(E) -> Unit
    ): BatchUpsertStatement {
        return BatchUpsertStatement(
            table = this,
            keys = keys,
            onUpdateExclude = onUpdateExclude,
            where = where?.let { SqlExpressionBuilder.it() },
            shouldReturnGeneratedValues = shouldReturnGeneratedValues
        ).apply {
            onUpdate?.let { storeUpdateValues(it) }
                ?: onUpdateList?.let { updateValues.putAll(it) }
        }
    }

    /**
     * Represents the SQL statement that inserts, updates, or deletes records in a target table based on
     * a comparison with a source table.
     *
     * @param source An instance of the source table.
     * @param on A lambda function with [SqlExpressionBuilder] as its receiver that should return an `Op<Boolean>` condition.
     * This condition is used to match records between the source and target tables.
     * @param body A lambda where [MergeTableStatement] can be configured with specific actions to perform
     * when records are matched or not matched.
     * @return A [MergeTableStatement] that can be executed.
     */
    fun <D : Table, S : Table> D.mergeFrom(
        source: S,
        on: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
        body: MergeTableStatement.() -> Unit
    ): MergeTableStatement {
        return MergeTableStatement(this, source, on = on?.invoke(SqlExpressionBuilder)).apply(body)
    }

    /**
     * Represents the SQL statement that inserts, updates, or deletes records in a target table based on
     * a comparison with a select query source.
     *
     * @param selectQuery The aliased query for a complex subquery to be used as the source.
     * @param on A lambda with a receiver of type [SqlExpressionBuilder] that returns an `Op<Boolean>` condition.
     * This condition is used to match records between the source query and the target table.
     * @param body A lambda where [MergeSelectStatement] can be configured with specific actions to perform
     * when records are matched or not matched.
     * @return A [MergeSelectStatement] which represents the MERGE operation with the configured actions.
     */
    fun <T : Table> T.mergeFrom(
        selectQuery: QueryAlias,
        on: SqlExpressionBuilder.() -> Op<Boolean>,
        body: MergeSelectStatement.() -> Unit
    ): MergeSelectStatement {
        return MergeSelectStatement(this, selectQuery, SqlExpressionBuilder.on()).apply(body)
    }
    private fun Column<*>.isValidIfAutoIncrement(): Boolean =
        !columnType.isAutoInc || autoIncColumnType?.nextValExpression != null
}

/** Builder object for creating SQL statements. Made it private to avoid imports clash */
@Suppress("ForbiddenComment")
// TODO: StatementBuilder -> StatementBuilderImpl, and IStatementBuilder -> StatementBuilder
private object StatementBuilder : IStatementBuilder

// TODO: add documentation for building statements without execution, like in the old DSL
@Suppress("ForbiddenComment", "AnnotationSpacing")
fun <S> buildStatement(body: IStatementBuilder.() -> S): S = body(StatementBuilder)

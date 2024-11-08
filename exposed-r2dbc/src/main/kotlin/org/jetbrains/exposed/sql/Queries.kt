package org.jetbrains.exposed.sql

// uncomment when decision is made on how terminal operations are invoked when driver is unknown

// import org.jetbrains.exposed.dao.id.EntityID
// import org.jetbrains.exposed.dao.id.IdTable
// import org.jetbrains.exposed.sql.statements.*
// import org.jetbrains.exposed.sql.transactions.TransactionManager
//
// @Deprecated(
//    "This `offset` parameter is not being used and will be removed in future releases. Please leave a comment on " +
//        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-550/DeleteStatement-holds-unused-offset-property) " +
//        "with a use-case if your database supports the OFFSET clause in a DELETE statement.",
//    ReplaceWith("deleteWhere(limit) { op.invoke() }"),
//    DeprecationLevel.WARNING
// )
// @Suppress("UnusedParameter")
// suspend fun <T : Table> T.deleteWhere(limit: Int? = null, offset: Long? = null, op: T.(ISqlExpressionBuilder) -> Op<Boolean>): Int =
//    deleteWhere(limit, op)
//
// /**
// * Represents the SQL statement that deletes only rows in a table that match the provided [op].
// *
// * @param limit Maximum number of rows to delete.
// * @param op Condition that determines which rows to delete.
// * @return Count of deleted rows.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.DeleteTests.testDelete01
// */
// suspend inline fun <T : Table> T.deleteWhere(
//    limit: Int? = null,
//    op: T.(ISqlExpressionBuilder) -> Op<Boolean>
// ): Int =
//    DeleteStatement(this, op(SqlExpressionBuilder), false, limit, emptyList())
//        .execute(TransactionManager.current()) ?: 0
//
// @Deprecated(
//    "This `offset` parameter is not being used and will be removed in future releases. Please leave a comment on " +
//        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-550/DeleteStatement-holds-unused-offset-property) " +
//        "with a use-case if your database supports the OFFSET clause in a DELETE statement.",
//    ReplaceWith("deleteIgnoreWhere(limit) { op.invoke() }"),
//    DeprecationLevel.WARNING
// )
// @Suppress("UnusedParameter")
// suspend fun <T : Table> T.deleteIgnoreWhere(limit: Int? = null, offset: Long? = null, op: T.(ISqlExpressionBuilder) -> Op<Boolean>): Int =
//    deleteIgnoreWhere(limit, op)
//
// /**
// * Represents the SQL statement that deletes only rows in a table that match the provided [op], while ignoring any
// * possible errors that occur during the process.
// *
// * **Note:** `DELETE IGNORE` is not supported by all vendors. Please check the documentation.
// *
// * @param limit Maximum number of rows to delete.
// * @param op Condition that determines which rows to delete.
// * @return Count of deleted rows.
// */
// suspend inline fun <T : Table> T.deleteIgnoreWhere(
//    limit: Int? = null,
//    op: T.(ISqlExpressionBuilder) -> Op<Boolean>
// ): Int =
//    DeleteStatement(this, op(SqlExpressionBuilder), true, limit, emptyList())
//        .execute(TransactionManager.current()) ?: 0
//
// /**
// * Represents the SQL statement that deletes all rows in a table.
// *
// * @return Count of deleted rows.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.DeleteTests.testDelete01
// */
// suspend fun Table.deleteAll(): Int = DeleteStatement(this).execute(TransactionManager.current()) ?: 0
//
// @Deprecated(
//    "This `deleteReturning()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
//        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
//        "with a use-case if a nullable condition cannot be replaced with the new `deleteReturning()` overloads.",
//    ReplaceWith("deleteReturning(returning)"),
//    DeprecationLevel.WARNING
// )
// @JvmName("deleteReturningNullableParam")
// suspend fun <T : Table> T.deleteReturning(
//    returning: List<Expression<*>> = columns,
//    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
// ): ReturningStatement {
//    return where?.let { deleteReturning(returning, it) } ?: deleteReturning(returning)
// }
//
// /**
// * Represents the SQL statement that deletes rows in a table and returns specified data from the deleted rows.
// *
// * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
// * @param where Condition that determines which rows to delete.
// * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
// * expressions mapped to their resulting data.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReturningTests.testDeleteReturning
// */
// suspend inline fun <T : Table> T.deleteReturning(
//    returning: List<Expression<*>> = columns,
//    where: SqlExpressionBuilder.() -> Op<Boolean>
// ): ReturningStatement {
//    val delete = DeleteStatement(this, SqlExpressionBuilder.where(), false, null)
//    return ReturningStatement(this, returning, delete)
// }
//
// /**
// * Represents the SQL statement that deletes all rows in a table and returns specified data from the deleted rows.
// *
// * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
// * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
// * expressions mapped to their resulting data.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReturningTests.testDeleteReturning
// */
// suspend fun <T : Table> T.deleteReturning(
//    returning: List<Expression<*>> = columns
// ): ReturningStatement {
//    val delete = DeleteStatement(this, null, false, null)
//    return ReturningStatement(this, returning, delete)
// }
//
// @Deprecated(
//    "This `Join.delete()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
//        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
//        "with a use-case if a nullable condition cannot be replaced with the new `Join.delete()` overloads.",
//    ReplaceWith("delete(targetTable, targetTables = targetTables, ignore, limit)"),
//    DeprecationLevel.WARNING
// )
// @JvmName("deleteJoinNullableParam")
// suspend fun Join.delete(
//    targetTable: Table,
//    vararg targetTables: Table,
//    ignore: Boolean = false,
//    limit: Int? = null,
//    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
// ): Int {
//    return where?.let {
//        delete(targetTable, targetTables = targetTables, ignore, limit, it)
//    } ?: delete(targetTable, targetTables = targetTables, ignore, limit)
// }
//
// /**
// * Represents the SQL statement that deletes rows from a table in a join relation.
// *
// * @param targetTable The specific table from this join relation to delete rows from.
// * @param targetTables (Optional) Other tables from this join relation to delete rows from.
// * **Note** Targeting multiple tables for deletion is not supported by all vendors. Please check the documentation.
// * @param ignore Whether to ignore any possible errors that occur when deleting rows.
// * **Note** [ignore] is not supported by all vendors. Please check the documentation.
// * @param limit Maximum number of rows to delete.
// * **Note** [limit] is not supported by all vendors. Please check the documentation.
// * @param where Condition that determines which rows to delete.
// * @return The number of deleted rows.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.DeleteTests.testDeleteWithSingleJoin
// */
// suspend inline fun Join.delete(
//    targetTable: Table,
//    vararg targetTables: Table,
//    ignore: Boolean = false,
//    limit: Int? = null,
//    where: SqlExpressionBuilder.() -> Op<Boolean>
// ): Int {
//    val targets = listOf(targetTable) + targetTables
//    val delete = DeleteStatement(this, SqlExpressionBuilder.where(), ignore, limit, targets)
//    return delete.execute(TransactionManager.current()) ?: 0
// }
//
// /**
// * Represents the SQL statement that deletes all rows from a table in a join relation.
// *
// * @param targetTable The specific table from this join relation to delete rows from.
// * @param targetTables (Optional) Other tables from this join relation to delete rows from.
// * **Note** Targeting multiple tables for deletion is not supported by all vendors. Please check the documentation.
// * @param ignore Whether to ignore any possible errors that occur when deleting rows.
// * **Note** [ignore] is not supported by all vendors. Please check the documentation.
// * @param limit Maximum number of rows to delete.
// * **Note** [limit] is not supported by all vendors. Please check the documentation.
// * @return The number of deleted rows.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.DeleteTests.testDeleteWithSingleJoin
// */
// suspend fun Join.delete(
//    targetTable: Table,
//    vararg targetTables: Table,
//    ignore: Boolean = false,
//    limit: Int? = null
// ): Int {
//    val targets = listOf(targetTable) + targetTables
//    val delete = DeleteStatement(this, null, ignore, limit, targets)
//    return delete.execute(TransactionManager.current()) ?: 0
// }
//
// /**
// * Represents the SQL statement that inserts a new row into a table.
// *
// * @sample org.jetbrains.exposed.sql.tests.h2.H2Tests.insertInH2
// */
// suspend inline fun <T : Table> T.insert(
//    crossinline body: T.(InsertStatement<Number>) -> Unit
// ): InsertStatement<Number> = InsertStatement<Number>(this).apply {
//    body(this)
//    execute(TransactionManager.current())
// }
//
// /**
// * Represents the SQL statement that inserts a new row into a table.
// *
// * @return The generated ID for the new row.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testGeneratedKey04
// */
// suspend inline fun <Key : Any, T : IdTable<Key>> T.insertAndGetId(
//    crossinline body: T.(InsertStatement<EntityID<Key>>) -> Unit
// ): EntityID<Key> =
//    InsertStatement<EntityID<Key>>(this, false).run {
//        body(this)
//        execute(TransactionManager.current())
//        get(id)
//    }
//
// /**
// * Represents the SQL statement that inserts a new row into a table, while ignoring any possible errors that occur
// * during the process.
// *
// * For example, if the new row would violate a unique constraint, its insertion would be ignored.
// * **Note:** `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
// *
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testInsertIgnoreAndGetIdWithPredefinedId
// */
// suspend inline fun <T : Table> T.insertIgnore(
//    crossinline body: T.(UpdateBuilder<*>) -> Unit
// ): InsertStatement<Long> = InsertStatement<Long>(this, isIgnore = true).apply {
//    body(this)
//    execute(TransactionManager.current())
// }
//
// /**
// * Represents the SQL statement that inserts a new row into a table, while ignoring any possible errors that occur
// * during the process.
// *
// * For example, if the new row would violate a unique constraint, its insertion would be ignored.
// * **Note:** `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
// *
// * @return The generated ID for the new row, or `null` if none was retrieved after statement execution.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testInsertIgnoreAndGetId01
// */
// suspend inline fun <Key : Any, T : IdTable<Key>> T.insertIgnoreAndGetId(
//    crossinline body: T.(UpdateBuilder<*>) -> Unit
// ): EntityID<Key>? =
//    InsertStatement<EntityID<Key>>(this, isIgnore = true).run {
//        body(this)
//        when (execute(TransactionManager.current())) {
//            null, 0 -> null
//            else -> getOrNull(id)
//        }
//    }
//
// /**
// * Represents the SQL statement that either inserts a new row into a table, or, if insertion would violate a unique constraint,
// * first deletes the existing row before inserting a new row.
// *
// * **Note:** This operation is not supported by all vendors, please check the documentation.
// *
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testReplaceWithExpression
// */
// suspend inline fun <T : Table> T.replace(
//    crossinline body: T.(UpdateBuilder<*>) -> Unit
// ): ReplaceStatement<Long> = ReplaceStatement<Long>(this).apply {
//    body(this)
//    execute(TransactionManager.current())
// }
//
// /**
// * Represents the SQL statement that uses data retrieved from a [selectQuery] to either insert a new row into a table,
// * or, if insertion would violate a unique constraint, first delete the existing row before inserting a new row.
// *
// * **Note:** This operation is not supported by all vendors, please check the documentation.
// *
// * @param selectQuery Source `SELECT` query that provides the values to insert.
// * @param columns Columns to either insert values into or delete values from then insert into. This defaults to all
// * columns in the table that are not auto-increment columns without a valid sequence to generate new values.
// * @return The number of inserted (and possibly deleted) rows, or `null` if nothing was retrieved after statement execution.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testReplaceSelect
// */
// suspend fun <T : Table> T.replace(
//    selectQuery: AbstractQuery<*>,
//    columns: List<Column<*>> = this.columns.filter { it.isValidIfAutoIncrement() }
// ): Int? = StatementBuilder {
//    replace(selectQuery, columns)
// }.execute(TransactionManager.current())
//
// /**
// * Represents the SQL statement that uses data retrieved from a [selectQuery] to insert new rows into a table.
// *
// * @param selectQuery Source `SELECT` query that provides the values to insert.
// * @param columns Columns to insert the values into. This defaults to all columns in the table that are not
// * auto-increment columns without a valid sequence to generate new values.
// * @return The number of inserted rows, or `null` if nothing was retrieved after statement execution.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertSelectTests.testInsertSelect04
// */
// suspend fun <T : Table> T.insert(
//    selectQuery: AbstractQuery<*>,
//    columns: List<Column<*>> = this.columns.filter { it.isValidIfAutoIncrement() }
// ): Int? = StatementBuilder {
//    insert(selectQuery, columns)
// }.execute(TransactionManager.current())
//
// /**
// * Represents the SQL statement that uses data retrieved from a [selectQuery] to insert new rows into a table,
// * while ignoring any possible errors that occur during the process.
// *
// * **Note:** `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
// *
// * @param selectQuery Source `SELECT` query that provides the values to insert.
// * @param columns Columns to insert the values into. This defaults to all columns in the table that are not
// * auto-increment columns without a valid sequence to generate new values.
// * @return The number of inserted rows, or `null` if nothing was retrieved after statement execution.
// */
// suspend fun <T : Table> T.insertIgnore(
//    selectQuery: AbstractQuery<*>,
//    columns: List<Column<*>> = this.columns.filter { it.isValidIfAutoIncrement() }
// ): Int? = StatementBuilder {
//    insertIgnore(selectQuery, columns)
// }.execute(TransactionManager.current())
//
// private fun Column<*>.isValidIfAutoIncrement(): Boolean =
//    !columnType.isAutoInc || autoIncColumnType?.nextValExpression != null
//
// @Deprecated(
//    "This `update()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
//        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
//        "with a use-case if a nullable condition cannot be replaced with the new `update()` overloads.",
//    ReplaceWith("update(limit = limit) { body.invoke() }"),
//    DeprecationLevel.WARNING
// )
// @JvmName("updateNullableParam")
// suspend fun <T : Table> T.update(where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null, limit: Int? = null, body: T.(UpdateStatement) -> Unit): Int {
//    return where?.let { update(it, limit, body) } ?: update(limit, body)
// }
//
// @Deprecated(
//    "This `Join.update()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
//        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
//        "with a use-case if a nullable condition cannot be replaced with the new `Join.update()` overloads.",
//    ReplaceWith("update(limit = limit) { body.invoke() }"),
//    DeprecationLevel.WARNING
// )
// @JvmName("updateJoinNullableParam")
// suspend fun Join.update(where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null, limit: Int? = null, body: (UpdateStatement) -> Unit): Int {
//    return where?.let { update(it, limit, body) } ?: update(limit, body)
// }
//
// /**
// * Represents the SQL statement that updates rows of a table.
// *
// * @param where Condition that determines which rows to update.
// * @param limit Maximum number of rows to update.
// * @return The number of updated rows.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpdateTests.testUpdate01
// */
// suspend inline fun <T : Table> T.update(
//    where: SqlExpressionBuilder.() -> Op<Boolean>,
//    limit: Int? = null,
//    crossinline body: T.(UpdateStatement) -> Unit
// ): Int {
//    val query = UpdateStatement(this, limit, SqlExpressionBuilder.where())
//    body(query)
//    return query.execute(TransactionManager.current()) ?: 0
// }
//
// /**
// * Represents the SQL statement that updates all rows of a table.
// *
// * @param limit Maximum number of rows to update.
// * @return The number of updated rows.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpdateTests.testUpdate01
// */
// suspend inline fun <T : Table> T.update(
//    limit: Int? = null,
//    crossinline body: T.(UpdateStatement) -> Unit
// ): Int {
//    val query = UpdateStatement(this, limit, null)
//    body(query)
//    return query.execute(TransactionManager.current()) ?: 0
// }
//
// /**
// * Represents the SQL statement that updates rows of a join relation.
// *
// * @param where Condition that determines which rows to update.
// * @param limit Maximum number of rows to update.
// * @return The number of updated rows.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpdateTests.testUpdateWithSingleJoin
// */
// suspend inline fun Join.update(
//    where: SqlExpressionBuilder.() -> Op<Boolean>,
//    limit: Int? = null,
//    crossinline body: (UpdateStatement) -> Unit
// ): Int {
//    val query = UpdateStatement(this, limit, SqlExpressionBuilder.where())
//    body(query)
//    return query.execute(TransactionManager.current()) ?: 0
// }
//
// /**
// * Represents the SQL statement that updates all rows of a join relation.
// *
// * @param limit Maximum number of rows to update.
// * @return The number of updated rows.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpdateTests.testUpdateWithSingleJoin
// */
// suspend inline fun Join.update(
//    limit: Int? = null,
//    crossinline body: (UpdateStatement) -> Unit
// ): Int {
//    val query = UpdateStatement(this, limit, null)
//    body(query)
//    return query.execute(TransactionManager.current()) ?: 0
// }
//
// /**
// * Represents the SQL statement that either inserts a new row into a table, or updates the existing row if insertion would violate a unique constraint.
// *
// * **Note:** Vendors that do not support this operation directly implement the standard MERGE USING command.
// *
// * **Note:** Currently, the `upsert()` function might return an incorrect auto-generated ID (such as a UUID) if it performs an update.
// * In this case, it returns a new auto-generated ID instead of the ID of the updated row.
// * Postgres should not be affected by this issue as it implicitly returns the IDs of updated rows.
// *
// * @param keys (optional) Columns to include in the condition that determines a unique constraint match.
// * If no columns are provided, primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
// * @param onUpdate Lambda block with an [UpdateStatement] as its argument, allowing values to be assigned to the UPDATE clause.
// * To specify manually that the insert value should be used when updating a column, for example within an expression
// * or function, invoke `insertValue()` with the desired column as the function argument.
// * If left null, all columns will be updated with the values provided for the insert.
// * @param onUpdateExclude List of specific columns to exclude from updating.
// * If left null, all columns will be updated with the values provided for the insert.
// * @param where Condition that determines which rows to update, if a unique violation is found.
// * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testUpsertWithUniqueIndexConflict
// */
// suspend fun <T : Table> T.upsert(
//    vararg keys: Column<*>,
//    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
//    onUpdateExclude: List<Column<*>>? = null,
//    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
//    body: T.(UpsertStatement<Long>) -> Unit
// ): UpsertStatement<Long> {
//    return StatementBuilder {
//        upsert(this@upsert, keys = keys, onUpdate, onUpdateExclude, where, body)
//    }.apply { execute(TransactionManager.current()) }
// }
//
// @Deprecated(
//    "This `upsert()` with `onUpdate` parameter that accepts a List will be removed in future releases. " +
//        "Please use `upsert()` with `onUpdate` parameter that takes an `UpdateStatement` lambda block instead.",
//    level = DeprecationLevel.WARNING
// )
// suspend fun <T : Table> T.upsert(
//    vararg keys: Column<*>,
//    onUpdate: List<Pair<Column<*>, Expression<*>>>,
//    onUpdateExclude: List<Column<*>>? = null,
//    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
//    body: T.(UpsertStatement<Long>) -> Unit
// ): UpsertStatement<Long> {
//    val upsert = UpsertStatement<Long>(this, keys = keys, onUpdateExclude, where?.let { SqlExpressionBuilder.it() })
//    upsert.updateValues.putAll(onUpdate)
//    body(upsert)
//    upsert.execute(TransactionManager.current())
//    return upsert
// }
//
// @Deprecated(
//    "This `mergeFrom()` with a nullable `on` parameter will be removed in future releases. Please leave a comment on " +
//        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
//        "with a use-case if a nullable condition cannot be replaced with the new `mergeFrom()` overloads.",
//    ReplaceWith("mergeFrom(source) { body.invoke() }"),
//    DeprecationLevel.WARNING
// )
// @JvmName("mergeFromNullableParam")
// suspend fun <D : Table, S : Table> D.mergeFrom(
//    source: S,
//    on: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
//    body: MergeTableStatement.() -> Unit
// ): MergeTableStatement {
//    return on?.let { mergeFrom(source, it, body) } ?: mergeFrom(source, body)
// }
//
// /**
// * Performs an SQL MERGE operation to insert, update, or delete records in the target table based on
// * a comparison with a source table.
// *
// * @param D The target table type extending from [Table].
// * @param S The source table type extending from [Table].
// * @param source An instance of the source table.
// * @param on A lambda function with [SqlExpressionBuilder] as its receiver that should return a [Op<Boolean>] condition.
// *           This condition is used to match records between the source and target tables.
// * @param body A lambda where [MergeTableStatement] can be configured with specific actions to perform
// *             when records are matched or not matched.
// * @return A [MergeTableStatement] which represents the MERGE operation with the configured actions.
// */
// suspend inline fun <D : Table, S : Table> D.mergeFrom(
//    source: S,
//    on: SqlExpressionBuilder.() -> Op<Boolean>,
//    crossinline body: MergeTableStatement.() -> Unit
// ): MergeTableStatement {
//    return MergeTableStatement(this, source, on = SqlExpressionBuilder.on()).apply {
//        body(this)
//        execute(TransactionManager.current())
//    }
// }
//
// /**
// * Performs an SQL MERGE operation to insert, update, or delete records in the target table based on
// * a comparison with a source table.
// *
// * @param D The target table type extending from [Table].
// * @param S The source table type extending from [Table].
// * @param source An instance of the source table.
// * @param body A lambda where [MergeTableStatement] can be configured with specific actions to perform
// *             when records are matched or not matched.
// * @return A [MergeTableStatement] which represents the MERGE operation with the configured actions.
// */
// suspend inline fun <D : Table, S : Table> D.mergeFrom(
//    source: S,
//    crossinline body: MergeTableStatement.() -> Unit
// ): MergeTableStatement {
//    return MergeTableStatement(this, source, on = null).apply {
//        body(this)
//        execute(TransactionManager.current())
//    }
// }
//
// /**
// * Performs an SQL MERGE operation to insert, update, or delete records in the target table based on
// * a comparison with a select query source.
// *
// * @param T The target table type extending from [Table].
// * @param selectQuery represents the aliased query for a complex subquery to be used as the source.
// * @param on A lambda with a receiver of type [SqlExpressionBuilder] that returns a condition [Op<Boolean>]
// *           used to match records between the source query and the target table.
// * @param body A lambda where [MergeSelectStatement] can be configured with specific actions to perform
// *             when records are matched or not matched.
// * @return A [MergeSelectStatement] which represents the MERGE operation with the configured actions.
// */
// suspend inline fun <T : Table> T.mergeFrom(
//    selectQuery: QueryAlias,
//    on: SqlExpressionBuilder.() -> Op<Boolean>,
//    crossinline body: MergeSelectStatement.() -> Unit
// ): MergeSelectStatement {
//    return MergeSelectStatement(this, selectQuery, SqlExpressionBuilder.on()).apply {
//        body(this)
//        execute(TransactionManager.current())
//    }
// }

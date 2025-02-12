@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.jetbrains.exposed.r2dbc.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.AbstractQuery
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.ISqlExpressionBuilder
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.QueryAlias
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Slice
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.currentDialectMetadata
import kotlin.collections.ArrayList
import kotlin.collections.Iterable
import kotlin.collections.Iterator
import kotlin.collections.List
import kotlin.collections.emptyList
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.internal.LowPriorityInOverloadResolution
import kotlin.sequences.Sequence

/**
 * Creates a `SELECT` [org.jetbrains.exposed.sql.Query] by selecting all columns from this [org.jetbrains.exposed.sql.ColumnSet].
 *
 * The column set selected from may be either a [org.jetbrains.exposed.sql.Table] or a [org.jetbrains.exposed.sql.Join].
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.SelectTests.testSelect
 */
fun FieldSet.selectAll(): Query = Query(this, null)

/**
 * Creates a `SELECT` [Query] by selecting either a single [column], or a subset of [columns], from this [org.jetbrains.exposed.sql.ColumnSet].
 *
 * The column set selected from may be either a [org.jetbrains.exposed.sql.Table] or a [org.jetbrains.exposed.sql.Join].
 * Arguments provided to [column] and [columns] may be table object columns or function expressions.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.AliasesTests.testJoinSubQuery01
 */
@LowPriorityInOverloadResolution
fun ColumnSet.select(column: Expression<*>, vararg columns: Expression<*>): Query =
    Query(Slice(this, listOf(column) + columns), null)

/**
 * Creates a `SELECT` [Query] using a list of [columns] or expressions from this [ColumnSet].
 *
 * The column set selected from may be either a [org.jetbrains.exposed.sql.Table] or a [org.jetbrains.exposed.sql.Join].
 */
@LowPriorityInOverloadResolution
fun ColumnSet.select(columns: List<Expression<*>>): Query = Query(Slice(this, columns), null)

/**
 * Represents the SQL statement that deletes only rows in a table that match the provided [op].
 *
 * @param limit Maximum number of rows to delete.
 * @param op Condition that determines which rows to delete.
 * @return Count of deleted rows.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.DeleteTests.testDelete01
 */
suspend fun <T : Table> T.deleteWhere(
    limit: Int? = null,
    op: T.(ISqlExpressionBuilder) -> Op<Boolean>
): Int {
    if (limit != null && !currentDialectMetadata.supportsLimitWithUpdateOrDelete()) {
        throw UnsupportedByDialectException("LIMIT clause is not supported in DELETE statement.", currentDialect)
    }
    val stmt = StatementBuilder { deleteWhere(limit, op) }
    return DeleteExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that deletes only rows in a table that match the provided [op], while ignoring any
 * possible errors that occur during the process.
 *
 * **Note:** `DELETE IGNORE` is not supported by all vendors. Please check the documentation.
 *
 * @param limit Maximum number of rows to delete.
 * @param op Condition that determines which rows to delete.
 * @return Count of deleted rows.
 */
suspend fun <T : Table> T.deleteIgnoreWhere(
    limit: Int? = null,
    op: T.(ISqlExpressionBuilder) -> Op<Boolean>
): Int {
    if (limit != null && !currentDialectMetadata.supportsLimitWithUpdateOrDelete()) {
        throw UnsupportedByDialectException("LIMIT clause is not supported in DELETE statement.", currentDialect)
    }
    val stmt = StatementBuilder { deleteIgnoreWhere(limit, op) }
    return DeleteExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that deletes all rows in a table.
 *
 * @return Count of deleted rows.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.DeleteTests.testDelete01
 */
suspend fun Table.deleteAll(): Int {
    val stmt = StatementBuilder { deleteAll() }
    return DeleteExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

@Deprecated(
    "This `deleteReturning()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `deleteReturning()` overloads.",
    ReplaceWith("deleteReturning(returning)"),
    DeprecationLevel.WARNING
)
@JvmName("deleteReturningNullableParam")
suspend fun <T : Table> T.deleteReturning(
    returning: List<Expression<*>> = columns,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
): ReturningExecutable {
    return where?.let { deleteReturning(returning, it) } ?: deleteReturning(returning)
}

/**
 * Represents the SQL statement that deletes rows in a table and returns specified data from the deleted rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @param where Condition that determines which rows to delete.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [org.jetbrains.exposed.sql.ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReturningTests.testDeleteReturning
 */
fun <T : Table> T.deleteReturning(
    returning: List<Expression<*>> = columns,
    where: SqlExpressionBuilder.() -> Op<Boolean>
): ReturningExecutable {
    val stmt = StatementBuilder { deleteReturning(returning, where) }
    return ReturningExecutable(stmt)
}

/**
 * Represents the SQL statement that deletes all rows in a table and returns specified data from the deleted rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [org.jetbrains.exposed.sql.ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReturningTests.testDeleteReturning
 */
fun <T : Table> T.deleteReturning(
    returning: List<Expression<*>> = columns
): ReturningExecutable {
    val stmt = StatementBuilder { deleteReturning(returning) }
    return ReturningExecutable(stmt)
}

@Deprecated(
    "This `Join.delete()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `Join.delete()` overloads.",
    ReplaceWith("delete(targetTable, targetTables = targetTables, ignore, limit)"),
    DeprecationLevel.WARNING
)
@JvmName("deleteJoinNullableParam")
suspend fun Join.delete(
    targetTable: Table,
    vararg targetTables: Table,
    ignore: Boolean = false,
    limit: Int? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
): Int {
    return where?.let {
        delete(targetTable, targetTables = targetTables, ignore, limit, it)
    } ?: delete(targetTable, targetTables = targetTables, ignore, limit)
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
 * @param where Condition that determines which rows to delete.
 * @return The number of deleted rows.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.DeleteTests.testDeleteWithSingleJoin
 */
suspend fun Join.delete(
    targetTable: Table,
    vararg targetTables: Table,
    ignore: Boolean = false,
    limit: Int? = null,
    where: SqlExpressionBuilder.() -> Op<Boolean>
): Int {
    val stmt = StatementBuilder { delete(targetTable, targetTables = targetTables, ignore, limit, where) }
    return DeleteExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that deletes all rows from a table in a join relation.
 *
 * @param targetTable The specific table from this join relation to delete rows from.
 * @param targetTables (Optional) Other tables from this join relation to delete rows from.
 * **Note** Targeting multiple tables for deletion is not supported by all vendors. Please check the documentation.
 * @param ignore Whether to ignore any possible errors that occur when deleting rows.
 * **Note** [ignore] is not supported by all vendors. Please check the documentation.
 * @param limit Maximum number of rows to delete.
 * **Note** [limit] is not supported by all vendors. Please check the documentation.
 * @return The number of deleted rows.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.DeleteTests.testDeleteWithSingleJoin
 */
suspend fun Join.delete(
    targetTable: Table,
    vararg targetTables: Table,
    ignore: Boolean = false,
    limit: Int? = null
): Int {
    val stmt = StatementBuilder { delete(targetTable, targetTables = targetTables, ignore, limit, null) }
    return DeleteExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that inserts a new row into a table.
 *
 * @sample org.jetbrains.exposed.sql.tests.h2.H2Tests.insertInH2
 */
suspend fun <T : Table> T.insert(
    body: T.(UpdateBuilder<*>) -> Unit
): InsertStatement<Number> {
    val stmt = StatementBuilder { insert(body) }
    return InsertExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
}

/**
 * Represents the SQL statement that inserts a new row into a table.
 *
 * @return The generated ID for the new row.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testGeneratedKey04
 */
suspend fun <Key : Any, T : IdTable<Key>> T.insertAndGetId(
    body: T.(UpdateBuilder<*>) -> Unit
): EntityID<Key> {
    val stmt = StatementBuilder { insert(body) }
    return InsertExecutable(stmt).run {
        execute(TransactionManager.current())
        statement[id]
    }
}

/**
 * Represents the SQL statement that inserts a new row into a table, while ignoring any possible errors that occur
 * during the process.
 *
 * For example, if the new row would violate a unique constraint, its insertion would be ignored.
 * **Note:** `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testInsertIgnoreAndGetIdWithPredefinedId
 */
suspend fun <T : Table> T.insertIgnore(
    body: T.(UpdateBuilder<*>) -> Unit
): InsertStatement<Long> {
    val stmt = StatementBuilder { insertIgnore(body) }
    return InsertExecutable<Long, InsertStatement<Long>>(stmt).apply { execute(TransactionManager.current()) }.statement
}

/**
 * Represents the SQL statement that inserts a new row into a table, while ignoring any possible errors that occur
 * during the process.
 *
 * For example, if the new row would violate a unique constraint, its insertion would be ignored.
 * **Note:** `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
 *
 * @return The generated ID for the new row, or `null` if none was retrieved after statement execution.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testInsertIgnoreAndGetId01
 */
suspend fun <Key : Any, T : IdTable<Key>> T.insertIgnoreAndGetId(
    body: T.(UpdateBuilder<*>) -> Unit
): EntityID<Key>? {
    val stmt = StatementBuilder { insertIgnore(body) }
    return InsertExecutable<Long, InsertStatement<Long>>(stmt).run {
        when (execute(TransactionManager.current())) {
            null, 0 -> null
            else -> statement.getOrNull(id)
        }
    }
}

/**
 * Represents the SQL statement that uses data retrieved from a [selectQuery] to insert new rows into a table.
 *
 * @param selectQuery Source `SELECT` query that provides the values to insert.
 * @param columns Columns to insert the values into. This defaults to all columns in the table that are not
 * auto-increment columns without a valid sequence to generate new values.
 * @return The number of inserted rows, or `null` if nothing was retrieved after statement execution.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertSelectTests.testInsertSelect04
 */
suspend fun <T : Table> T.insert(
    selectQuery: AbstractQuery<*>,
    columns: List<Column<*>>? = null
): Int? {
    val stmt = StatementBuilder { insert(selectQuery, columns) }
    return InsertSelectExecutable(stmt).execute(TransactionManager.current())
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
 * @return The number of inserted rows, or `null` if nothing was retrieved after statement execution.
 */
suspend fun <T : Table> T.insertIgnore(
    selectQuery: AbstractQuery<*>,
    columns: List<Column<*>>? = null
): Int? {
    val stmt = StatementBuilder { insertIgnore(selectQuery, columns) }
    return InsertSelectExecutable(stmt).execute(TransactionManager.current())
}

/**
 * Represents the SQL statement that inserts new rows into a table and returns specified data from the inserted rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @param ignoreErrors Whether to ignore any possible errors that occur during the process.
 * Note `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [org.jetbrains.exposed.sql.ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReturningTests.testInsertReturning
 */
suspend fun <T : Table> T.insertReturning(
    returning: List<Expression<*>> = columns,
    ignoreErrors: Boolean = false,
    body: T.(InsertStatement<Number>) -> Unit
): ReturningExecutable {
    val stmt = StatementBuilder { insertReturning(returning, ignoreErrors, body) }
    return ReturningExecutable(stmt)
}

/**
 * Represents the SQL statement that batch inserts new rows into a table.
 *
 * @param data Collection of values to use in the batch insert.
 * @param ignore Whether to ignore errors or not.
 * **Note** [ignore] is not supported by all vendors. Please check the documentation.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs)
 * should be returned. See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @return A list of [org.jetbrains.exposed.sql.ResultRow] representing data from each newly inserted row.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testBatchInsert01
 */
suspend fun <T : Table, E> T.batchInsert(
    data: Iterable<E>,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchInsertStatement.(E) -> Unit
): List<ResultRow> = batchInsert(data.iterator(), ignoreErrors = ignore, shouldReturnGeneratedValues, body)

/**
 * Represents the SQL statement that batch inserts new rows into a table.
 *
 * @param data Sequence of values to use in the batch insert.
 * @param ignore Whether to ignore errors or not.
 * **Note** [ignore] is not supported by all vendors. Please check the documentation.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs)
 * should be returned. See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @return A list of [ResultRow] representing data from each newly inserted row.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertTests.testBatchInsertWithSequence
 */
suspend fun <T : Table, E> T.batchInsert(
    data: Sequence<E>,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchInsertStatement.(E) -> Unit
): List<ResultRow> = batchInsert(data.iterator(), ignoreErrors = ignore, shouldReturnGeneratedValues, body)

private suspend fun <T : Table, E> T.batchInsert(
    data: Iterator<E>,
    ignoreErrors: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchInsertStatement.(E) -> Unit
): List<ResultRow> = executeBatch(data, body) {
    val stmt = StatementBuilder { batchInsert(ignoreErrors, shouldReturnGeneratedValues, body) }
    stmt.executable()
}

/**
 * Represents the SQL statement that either inserts a new row into a table, or, if insertion would violate a unique constraint,
 * first deletes the existing row before inserting a new row.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testReplaceWithExpression
 */
suspend fun <T : Table> T.replace(
    body: T.(UpdateBuilder<*>) -> Unit
): ReplaceStatement<Long> {
    val stmt = StatementBuilder { replace(body) }
    return ReplaceExecutable<Long>(stmt).apply { execute(TransactionManager.current()) }.statement
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
 * @return The number of inserted (and possibly deleted) rows, or `null` if nothing was retrieved after statement execution.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testReplaceSelect
 */
suspend fun <T : Table> T.replace(
    selectQuery: AbstractQuery<*>,
    columns: List<Column<*>>? = null
): Int? {
    val stmt = StatementBuilder { replace(selectQuery, columns) }
    return ReplaceSelectExecutable(stmt).execute(TransactionManager.current())
}

/**
 * Represents the SQL statement that either batch inserts new rows into a table, or, if insertions violate unique constraints,
 * first deletes the existing rows before inserting new rows.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @param data Collection of values to use in replace.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testBatchReplace01
 */
suspend fun <T : Table, E : Any> T.batchReplace(
    data: Iterable<E>,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchReplaceStatement.(E) -> Unit
): List<ResultRow> = batchReplace(data.iterator(), shouldReturnGeneratedValues, body)

/**
 * Represents the SQL statement that either batch inserts new rows into a table, or, if insertions violate unique constraints,
 * first deletes the existing rows before inserting new rows.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @param data Sequence of values to use in replace.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testBatchReplaceWithSequence
 */
suspend fun <T : Table, E : Any> T.batchReplace(
    data: Sequence<E>,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchReplaceStatement.(E) -> Unit
): List<ResultRow> = batchReplace(data.iterator(), shouldReturnGeneratedValues, body)

private suspend fun <T : Table, E> T.batchReplace(
    data: Iterator<E>,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchReplaceStatement.(E) -> Unit
): List<ResultRow> = executeBatch(data, body) {
    val stmt = StatementBuilder { batchReplace(shouldReturnGeneratedValues, body) }
    BatchReplaceExecutable(stmt)
}

@OptIn(InternalApi::class)
private suspend fun <E, S1 : BaseBatchInsertStatement, S2 : BaseBatchInsertExecutable<S1>> executeBatch(
    data: Iterator<E>,
    body: S1.(E) -> Unit,
    newBatchStatement: () -> S2
): List<ResultRow> {
    if (!data.hasNext()) return emptyList()

    var executable = newBatchStatement()

    val result = ArrayList<ResultRow>()
    suspend fun S2.handleBatchException(removeLastData: Boolean = false, body: S1.() -> Unit) {
        try {
            statement.body()
            if (removeLastData) statement.validateLastBatch()
        } catch (e: BatchDataInconsistentException) {
            if (this.statement.data.size == 1) {
                throw e
            }
            val notTheFirstBatch = this.statement.data.size > 1
            if (notTheFirstBatch) {
                if (removeLastData) {
                    statement.removeLastBatch()
                }
                execute(TransactionManager.current())
                result += statement.resultedValues.orEmpty()
            }
            executable = newBatchStatement()
            if (removeLastData && notTheFirstBatch) {
                executable.statement.addBatch()
                executable.statement.body()
                executable.statement.validateLastBatch()
            }
        }
    }

    data.forEach { element ->
        executable.handleBatchException { addBatch() }
        executable.handleBatchException(true) { body(element) }
    }
    if (executable.statement.arguments().isNotEmpty()) {
        executable.execute(TransactionManager.current())
        result += executable.statement.resultedValues.orEmpty()
    }
    return result
}

@Deprecated(
    "This `update()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `update()` overloads.",
    ReplaceWith("update(limit = limit) { body.invoke() }"),
    DeprecationLevel.WARNING
)
@JvmName("updateNullableParam")
suspend fun <T : Table> T.update(where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null, limit: Int? = null, body: T.(UpdateStatement) -> Unit): Int {
    return where?.let { update(it, limit, body) } ?: update(limit, body)
}

/**
 * Represents the SQL statement that updates rows of a table.
 *
 * @param where Condition that determines which rows to update.
 * @param limit Maximum number of rows to update.
 * @return The number of updated rows.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpdateTests.testUpdate01
 */
suspend fun <T : Table> T.update(
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    limit: Int? = null,
    body: T.(UpdateStatement) -> Unit
): Int {
    if (limit != null && !currentDialectMetadata.supportsLimitWithUpdateOrDelete()) {
        throw UnsupportedByDialectException("LIMIT clause is not supported in UPDATE statement.", currentDialect)
    }
    val stmt = StatementBuilder { update(where, limit, body) }
    return UpdateExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that updates all rows of a table.
 *
 * @param limit Maximum number of rows to update.
 * @return The number of updated rows.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpdateTests.testUpdate01
 */
suspend fun <T : Table> T.update(
    limit: Int? = null,
    body: T.(UpdateStatement) -> Unit
): Int {
    if (limit != null && !currentDialectMetadata.supportsLimitWithUpdateOrDelete()) {
        throw UnsupportedByDialectException("LIMIT clause is not supported in UPDATE statement.", currentDialect)
    }
    val stmt = StatementBuilder { update(null, limit, body) }
    return UpdateExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

@Deprecated(
    "This `Join.update()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `Join.update()` overloads.",
    ReplaceWith("update(limit = limit) { body.invoke() }"),
    DeprecationLevel.WARNING
)
@JvmName("updateJoinNullableParam")
suspend fun Join.update(where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null, limit: Int? = null, body: (UpdateStatement) -> Unit): Int {
    return where?.let { update(it, limit, body) } ?: update(limit, body)
}

/**
 * Represents the SQL statement that updates rows of a join relation.
 *
 * @param where Condition that determines which rows to update.
 * @param limit Maximum number of rows to update.
 * @return The number of updated rows.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpdateTests.testUpdateWithSingleJoin
 */
suspend fun Join.update(
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    limit: Int? = null,
    body: (UpdateStatement) -> Unit
): Int {
    val stmt = StatementBuilder { update(where, limit, body) }
    return UpdateExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that updates all rows of a join relation.
 *
 * @param limit Maximum number of rows to update.
 * @return The number of updated rows.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpdateTests.testUpdateWithSingleJoin
 */
suspend fun Join.update(
    limit: Int? = null,
    body: (UpdateStatement) -> Unit
): Int {
    val stmt = StatementBuilder { update(null, limit, body) }
    return UpdateExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

@Deprecated(
    "This `updateReturning()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `updateReturning()` overloads.",
    ReplaceWith("updateReturning(returning) { body.invoke() }"),
    DeprecationLevel.WARNING
)
@JvmName("updateReturningNullableParam")
fun <T : Table> T.updateReturning(
    returning: List<Expression<*>> = columns,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    body: T.(UpdateStatement) -> Unit
): ReturningExecutable {
    return where?.let { updateReturning(returning, it, body) } ?: updateReturning(returning, body)
}

/**
 * Represents the SQL statement that updates rows of a table and returns specified data from the updated rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @param where Condition that determines which rows to update.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReturningTests.testUpdateReturning
 */
fun <T : Table> T.updateReturning(
    returning: List<Expression<*>> = columns,
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    body: T.(UpdateStatement) -> Unit
): ReturningExecutable {
    val stmt = StatementBuilder { updateReturning(returning, where, body) }
    return ReturningExecutable(stmt)
}

/**
 * Represents the SQL statement that updates all rows of a table and returns specified data from the updated rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReturningTests.testUpdateReturning
 */
fun <T : Table> T.updateReturning(
    returning: List<Expression<*>> = columns,
    body: T.(UpdateStatement) -> Unit
): ReturningExecutable {
    val stmt = StatementBuilder { updateReturning(returning, null, body) }
    return ReturningExecutable(stmt)
}

/**
 * Represents the SQL statement that either inserts a new row into a table, or updates the existing row if insertion would violate a unique constraint.
 *
 * **Note:** Vendors that do not support this operation directly implement the standard MERGE USING command.
 *
 * **Note:** Currently, the `upsert()` function might return an incorrect auto-generated ID (such as a UUID) if it performs an update.
 * In this case, it returns a new auto-generated ID instead of the ID of the updated row.
 * Postgres should not be affected by this issue as it implicitly returns the IDs of updated rows.
 *
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match.
 * If no columns are provided, primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate Lambda block with an [UpdateStatement] as its argument, allowing values to be assigned to the UPDATE clause.
 * To specify manually that the insert value should be used when updating a column, for example within an expression
 * or function, invoke `insertValue()` with the desired column as the function argument.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param onUpdateExclude List of specific columns to exclude from updating.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testUpsertWithUniqueIndexConflict
 */
suspend fun <T : Table> T.upsert(
    vararg keys: Column<*>,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    onUpdateExclude: List<Column<*>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    body: T.(UpsertStatement<Long>) -> Unit
): UpsertStatement<Long> {
    val stmt = StatementBuilder { upsert(keys = keys, onUpdate, onUpdateExclude, where, body) }
    return UpsertExecutable<Long>(stmt).apply { execute(TransactionManager.current()) }.statement
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
 * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReturningTests.testUpsertReturning
 */
fun <T : Table> T.upsertReturning(
    vararg keys: Column<*>,
    returning: List<Expression<*>> = columns,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    onUpdateExclude: List<Column<*>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    body: T.(UpsertStatement<Long>) -> Unit
): ReturningExecutable {
    val stmt = StatementBuilder { upsertReturning(keys = keys, returning, onUpdate, onUpdateExclude, where, body) }
    return ReturningExecutable(stmt)
}

/**
 * Represents the SQL statement that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
 *
 * @param data Collection of values to use in batch upsert.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate Lambda block with an [UpdateStatement] as its argument, allowing values to be assigned to the UPDATE clause.
 * To specify manually that the insert value should be used when updating a column, for example within an expression
 * or function, invoke `insertValue()` with the desired column as the function argument.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param onUpdateExclude List of specific columns to exclude from updating.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testBatchUpsertWithNoConflict
 */
suspend fun <T : Table, E : Any> T.batchUpsert(
    data: Iterable<E>,
    vararg keys: Column<*>,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    onUpdateExclude: List<Column<*>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchUpsertStatement.(E) -> Unit
): List<ResultRow> {
    return batchUpsert(data.iterator(), null, onUpdate, onUpdateExclude, where, shouldReturnGeneratedValues, keys = keys, body = body)
}

/**
 * Represents the SQL statement that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
 *
 * @param data Sequence of values to use in batch upsert.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate Lambda block with an [UpdateStatement] as its argument, allowing values to be assigned to the UPDATE clause.
 * To specify manually that the insert value should be used when updating a column, for example within an expression
 * or function, invoke `insertValue()` with the desired column as the function argument.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param onUpdateExclude List of specific columns to exclude from updating.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testBatchUpsertWithSequence
 */
suspend fun <T : Table, E : Any> T.batchUpsert(
    data: Sequence<E>,
    vararg keys: Column<*>,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    onUpdateExclude: List<Column<*>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchUpsertStatement.(E) -> Unit
): List<ResultRow> {
    return batchUpsert(data.iterator(), null, onUpdate, onUpdateExclude, where, shouldReturnGeneratedValues, keys = keys, body = body)
}

@Suppress("LongParameterList")
private suspend fun <T : Table, E> T.batchUpsert(
    data: Iterator<E>,
    onUpdateList: List<Pair<Column<*>, Any?>>? = null,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    onUpdateExclude: List<Column<*>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    shouldReturnGeneratedValues: Boolean = true,
    vararg keys: Column<*>,
    body: BatchUpsertStatement.(E) -> Unit
): List<ResultRow> = executeBatch(data, body) {
    val stmt = StatementBuilder {
        batchUpsert(onUpdateList, onUpdate, onUpdateExclude, where, shouldReturnGeneratedValues, keys = keys, body)
    }
    BatchUpsertExecutable(stmt)
}

@Deprecated(
    "This `mergeFrom()` with a nullable `on` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `mergeFrom()` overloads.",
    ReplaceWith("mergeFrom(source) { body.invoke() }"),
    DeprecationLevel.WARNING
)
@JvmName("mergeFromNullableParam")
suspend fun <D : Table, S : Table> D.mergeFrom(
    source: S,
    on: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    body: MergeTableStatement.() -> Unit
): MergeTableStatement {
    return on?.let { mergeFrom(source, it, body) } ?: mergeFrom(source, body)
}

/**
 * Performs an SQL MERGE operation to insert, update, or delete records in the target table based on
 * a comparison with a source table.
 *
 * @param D The target table type extending from [Table].
 * @param S The source table type extending from [Table].
 * @param source An instance of the source table.
 * @param on A lambda function with [SqlExpressionBuilder] as its receiver that should return a [Op<Boolean>] condition.
 *           This condition is used to match records between the source and target tables.
 * @param body A lambda where [MergeTableStatement] can be configured with specific actions to perform
 *             when records are matched or not matched.
 * @return A [MergeTableStatement] which represents the MERGE operation with the configured actions.
 */
suspend fun <D : Table, S : Table> D.mergeFrom(
    source: S,
    on: SqlExpressionBuilder.() -> Op<Boolean>,
    body: MergeTableStatement.() -> Unit
): MergeTableStatement {
    val stmt = StatementBuilder { mergeFrom(source, on, body) }
    return MergeTableExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
}

/**
 * Performs an SQL MERGE operation to insert, update, or delete records in the target table based on
 * a comparison with a source table.
 *
 * @param D The target table type extending from [Table].
 * @param S The source table type extending from [Table].
 * @param source An instance of the source table.
 * @param body A lambda where [MergeTableStatement] can be configured with specific actions to perform
 *             when records are matched or not matched.
 * @return A [MergeTableStatement] which represents the MERGE operation with the configured actions.
 */
suspend fun <D : Table, S : Table> D.mergeFrom(
    source: S,
    body: MergeTableStatement.() -> Unit
): MergeTableStatement {
    val stmt = StatementBuilder { mergeFrom(source, null, body) }
    return MergeTableExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
}

/**
 * Performs an SQL MERGE operation to insert, update, or delete records in the target table based on
 * a comparison with a select query source.
 *
 * @param T The target table type extending from [Table].
 * @param selectQuery represents the aliased query for a complex subquery to be used as the source.
 * @param on A lambda with a receiver of type [SqlExpressionBuilder] that returns a condition [Op<Boolean>]
 *           used to match records between the source query and the target table.
 * @param body A lambda where [MergeSelectStatement] can be configured with specific actions to perform
 *             when records are matched or not matched.
 * @return A [MergeSelectStatement] which represents the MERGE operation with the configured actions.
 */
suspend fun <T : Table> T.mergeFrom(
    selectQuery: QueryAlias,
    on: SqlExpressionBuilder.() -> Op<Boolean>,
    body: MergeSelectStatement.() -> Unit
): MergeSelectStatement {
    val stmt = StatementBuilder { mergeFrom(selectQuery, on, body) }
    return MergeSelectExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
}

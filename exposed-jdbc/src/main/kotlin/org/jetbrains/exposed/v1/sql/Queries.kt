@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.jetbrains.exposed.v1.sql

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.*
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.IdTable
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.sql.statements.*
import org.jetbrains.exposed.v1.sql.statements.executable
import org.jetbrains.exposed.v1.sql.transactions.TransactionManager
import org.jetbrains.exposed.v1.sql.vendors.currentDialectMetadata
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
 * Creates a `SELECT` [Query] by selecting all columns from this [ColumnSet].
 *
 * The column set selected from may be either a [Table] or a [Join].
 *
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.SelectTests.testSelect
 */
fun FieldSet.selectAll(): org.jetbrains.exposed.v1.sql.Query = Query(this, null)

/**
 * Creates a `SELECT` [Query] by selecting either a single [column], or a subset of [columns], from this [ColumnSet].
 *
 * The column set selected from may be either a [Table] or a [Join].
 * Arguments provided to [column] and [columns] may be table object columns or function expressions.
 *
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.AliasesTests.testJoinSubQuery01
 */
@LowPriorityInOverloadResolution
fun ColumnSet.select(column: Expression<*>, vararg columns: Expression<*>): org.jetbrains.exposed.v1.sql.Query =
    Query(Select(this, listOf(column) + columns), null)

/**
 * Creates a `SELECT` [Query] using a list of [columns] or expressions from this [ColumnSet].
 *
 * The column set selected from may be either a [Table] or a [Join].
 */
@LowPriorityInOverloadResolution
fun ColumnSet.select(columns: List<Expression<*>>): org.jetbrains.exposed.v1.sql.Query = Query(Select(this, columns), null)

/**
 * Represents the SQL statement that deletes only rows in a table that match the provided [op].
 *
 * @param limit Maximum number of rows to delete.
 * @param op Condition that determines which rows to delete.
 * @return Count of deleted rows.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.DeleteTests.testDelete01
 */
fun <T : Table> T.deleteWhere(
    limit: Int? = null,
    op: T.(ISqlExpressionBuilder) -> Op<Boolean>
): Int {
    if (limit != null && !currentDialectMetadata.supportsLimitWithUpdateOrDelete()) {
        throw UnsupportedByDialectException("LIMIT clause is not supported in DELETE statement.", currentDialect)
    }
    val stmt = buildStatement { deleteWhere(limit, op) }
    return DeleteBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
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
fun <T : Table> T.deleteIgnoreWhere(
    limit: Int? = null,
    op: T.(ISqlExpressionBuilder) -> Op<Boolean>
): Int {
    if (limit != null && !currentDialectMetadata.supportsLimitWithUpdateOrDelete()) {
        throw UnsupportedByDialectException("LIMIT clause is not supported in DELETE statement.", currentDialect)
    }
    val stmt = buildStatement { deleteIgnoreWhere(limit, op) }
    return DeleteBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that deletes all rows in a table.
 *
 * @return Count of deleted rows.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.DeleteTests.testDelete01
 */
fun Table.deleteAll(): Int {
    val stmt = buildStatement { deleteAll() }
    return DeleteBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

@Deprecated(
    "This `deleteReturning()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `deleteReturning()` overloads.",
    ReplaceWith("deleteReturning(returning)"),
    DeprecationLevel.WARNING
)
@JvmName("deleteReturningNullableParam")
fun <T : Table> T.deleteReturning(
    returning: List<Expression<*>> = columns,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
): ReturningBlockingExecutable {
    return where?.let { deleteReturning(returning, it) } ?: deleteReturning(returning)
}

/**
 * Represents the SQL statement that deletes rows in a table and returns specified data from the deleted rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @param where Condition that determines which rows to delete.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReturningTests.testDeleteReturning
 */
fun <T : Table> T.deleteReturning(
    returning: List<Expression<*>> = columns,
    where: SqlExpressionBuilder.() -> Op<Boolean>
): ReturningBlockingExecutable {
    val stmt = buildStatement { deleteReturning(returning, where) }
    return ReturningBlockingExecutable(stmt)
}

/**
 * Represents the SQL statement that deletes all rows in a table and returns specified data from the deleted rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReturningTests.testDeleteReturning
 */
fun <T : Table> T.deleteReturning(
    returning: List<Expression<*>> = columns
): ReturningBlockingExecutable {
    val stmt = buildStatement { deleteReturning(returning) }
    return ReturningBlockingExecutable(stmt)
}

@Deprecated(
    "This `Join.delete()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `Join.delete()` overloads.",
    ReplaceWith("delete(targetTable, targetTables = targetTables, ignore, limit)"),
    DeprecationLevel.WARNING
)
@JvmName("deleteJoinNullableParam")
fun Join.delete(
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.DeleteTests.testDeleteWithSingleJoin
 */
fun Join.delete(
    targetTable: Table,
    vararg targetTables: Table,
    ignore: Boolean = false,
    limit: Int? = null,
    where: SqlExpressionBuilder.() -> Op<Boolean>
): Int {
    val stmt = buildStatement { delete(targetTable, targetTables = targetTables, ignore, limit, where) }
    return DeleteBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.DeleteTests.testDeleteWithSingleJoin
 */
fun Join.delete(
    targetTable: Table,
    vararg targetTables: Table,
    ignore: Boolean = false,
    limit: Int? = null
): Int {
    val stmt = buildStatement { delete(targetTable, targetTables = targetTables, ignore, limit, null) }
    return DeleteBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that inserts a new row into a table.
 *
 * @sample org.jetbrains.exposed.v1.sql.tests.h2.H2Tests.insertInH2
 */
fun <T : Table> T.insert(
    body: T.(UpdateBuilder<*>) -> Unit
): InsertStatement<Number> {
    val stmt = buildStatement { insert(body) }
    return InsertBlockingExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
}

/**
 * Represents the SQL statement that inserts a new row into a table.
 *
 * @return The generated ID for the new row.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.InsertTests.testGeneratedKey04
 */
fun <Key : Any, T : IdTable<Key>> T.insertAndGetId(
    body: T.(UpdateBuilder<*>) -> Unit
): EntityID<Key> {
    val stmt = buildStatement { insert(body) }
    return InsertBlockingExecutable(stmt).run {
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.InsertTests.testInsertIgnoreAndGetIdWithPredefinedId
 */
fun <T : Table> T.insertIgnore(
    body: T.(UpdateBuilder<*>) -> Unit
): InsertStatement<Long> {
    val stmt = buildStatement { insertIgnore(body) }
    return InsertBlockingExecutable<Long, InsertStatement<Long>>(stmt).apply { execute(TransactionManager.current()) }.statement
}

/**
 * Represents the SQL statement that inserts a new row into a table, while ignoring any possible errors that occur
 * during the process.
 *
 * For example, if the new row would violate a unique constraint, its insertion would be ignored.
 * **Note:** `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
 *
 * @return The generated ID for the new row, or `null` if none was retrieved after statement execution.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.InsertTests.testInsertIgnoreAndGetId01
 */
fun <Key : Any, T : IdTable<Key>> T.insertIgnoreAndGetId(
    body: T.(UpdateBuilder<*>) -> Unit
): EntityID<Key>? {
    val stmt = buildStatement { insertIgnore(body) }
    return InsertBlockingExecutable<Long, InsertStatement<Long>>(stmt).run {
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.InsertSelectTests.testInsertSelect04
 */
fun <T : Table> T.insert(
    selectQuery: AbstractQuery<*>,
    columns: List<Column<*>>? = null
): Int? {
    val stmt = buildStatement { insert(selectQuery, columns) }
    return InsertSelectBlockingExecutable(stmt).execute(TransactionManager.current())
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
fun <T : Table> T.insertIgnore(
    selectQuery: AbstractQuery<*>,
    columns: List<Column<*>>? = null
): Int? {
    val stmt = buildStatement { insertIgnore(selectQuery, columns) }
    return InsertSelectBlockingExecutable(stmt).execute(TransactionManager.current())
}

/**
 * Represents the SQL statement that inserts new rows into a table and returns specified data from the inserted rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @param ignoreErrors Whether to ignore any possible errors that occur during the process.
 * Note `INSERT IGNORE` is not supported by all vendors. Please check the documentation.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReturningTests.testInsertReturning
 */
fun <T : Table> T.insertReturning(
    returning: List<Expression<*>> = columns,
    ignoreErrors: Boolean = false,
    body: T.(InsertStatement<Number>) -> Unit
): ReturningBlockingExecutable {
    val stmt = buildStatement { insertReturning(returning, ignoreErrors, body) }
    return ReturningBlockingExecutable(stmt)
}

/**
 * Represents the SQL statement that batch inserts new rows into a table.
 *
 * @param data Collection of values to use in the batch insert.
 * @param ignore Whether to ignore errors or not.
 * **Note** [ignore] is not supported by all vendors. Please check the documentation.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs)
 * should be returned. See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @return A list of [ResultRow] representing data from each newly inserted row.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.InsertTests.testBatchInsert01
 */
fun <T : Table, E> T.batchInsert(
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.InsertTests.testBatchInsertWithSequence
 */
fun <T : Table, E> T.batchInsert(
    data: Sequence<E>,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchInsertStatement.(E) -> Unit
): List<ResultRow> = batchInsert(data.iterator(), ignoreErrors = ignore, shouldReturnGeneratedValues, body)

private fun <T : Table, E> T.batchInsert(
    data: Iterator<E>,
    ignoreErrors: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchInsertStatement.(E) -> Unit
): List<ResultRow> = executeBatch(data, body) {
    val stmt = buildStatement { batchInsert(ignoreErrors, shouldReturnGeneratedValues, body) }
    stmt.executable()
}

/**
 * Represents the SQL statement that either inserts a new row into a table, or, if insertion would violate a unique constraint,
 * first deletes the existing row before inserting a new row.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReplaceTests.testReplaceWithExpression
 */
fun <T : Table> T.replace(
    body: T.(UpdateBuilder<*>) -> Unit
): ReplaceStatement<Long> {
    val stmt = buildStatement { replace(body) }
    return InsertBlockingExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReplaceTests.testReplaceSelect
 */
fun <T : Table> T.replace(
    selectQuery: AbstractQuery<*>,
    columns: List<Column<*>>? = null
): Int? {
    val stmt = buildStatement { replace(selectQuery, columns) }
    return InsertSelectBlockingExecutable(stmt).execute(TransactionManager.current())
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReplaceTests.testBatchReplace01
 */
fun <T : Table, E : Any> T.batchReplace(
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReplaceTests.testBatchReplaceWithSequence
 */
fun <T : Table, E : Any> T.batchReplace(
    data: Sequence<E>,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchReplaceStatement.(E) -> Unit
): List<ResultRow> = batchReplace(data.iterator(), shouldReturnGeneratedValues, body)

private fun <T : Table, E> T.batchReplace(
    data: Iterator<E>,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchReplaceStatement.(E) -> Unit
): List<ResultRow> = executeBatch(data, body) {
    val stmt = buildStatement { batchReplace(shouldReturnGeneratedValues, body) }
    BatchInsertBlockingExecutable(stmt)
}

@OptIn(InternalApi::class)
private fun <E, S1 : BaseBatchInsertStatement, S2 : BatchInsertBlockingExecutable<S1>> executeBatch(
    data: Iterator<E>,
    body: S1.(E) -> Unit,
    newBatchStatement: () -> S2
): List<ResultRow> {
    if (!data.hasNext()) return emptyList()

    var executable = newBatchStatement()

    val result = ArrayList<ResultRow>()
    fun S2.handleBatchException(removeLastData: Boolean = false, body: S1.() -> Unit) {
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
fun <T : Table> T.update(where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null, limit: Int? = null, body: T.(UpdateStatement) -> Unit): Int {
    return where?.let { update(it, limit, body) } ?: update(limit, body)
}

/**
 * Represents the SQL statement that updates rows of a table.
 *
 * @param where Condition that determines which rows to update.
 * @param limit Maximum number of rows to update.
 * @return The number of updated rows.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.UpdateTests.testUpdate01
 */
fun <T : Table> T.update(
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    limit: Int? = null,
    body: T.(UpdateStatement) -> Unit
): Int {
    if (limit != null && !currentDialectMetadata.supportsLimitWithUpdateOrDelete()) {
        throw UnsupportedByDialectException("LIMIT clause is not supported in UPDATE statement.", currentDialect)
    }
    val stmt = buildStatement { update(where, limit, body) }
    return UpdateBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that updates all rows of a table.
 *
 * @param limit Maximum number of rows to update.
 * @return The number of updated rows.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.UpdateTests.testUpdate01
 */
fun <T : Table> T.update(
    limit: Int? = null,
    body: T.(UpdateStatement) -> Unit
): Int {
    if (limit != null && !currentDialectMetadata.supportsLimitWithUpdateOrDelete()) {
        throw UnsupportedByDialectException("LIMIT clause is not supported in UPDATE statement.", currentDialect)
    }
    val stmt = buildStatement { update(null, limit, body) }
    return UpdateBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

@Deprecated(
    "This `Join.update()` with a nullable `where` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `Join.update()` overloads.",
    ReplaceWith("update(limit = limit) { body.invoke() }"),
    DeprecationLevel.WARNING
)
@JvmName("updateJoinNullableParam")
fun Join.update(where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null, limit: Int? = null, body: (UpdateStatement) -> Unit): Int {
    return where?.let { update(it, limit, body) } ?: update(limit, body)
}

/**
 * Represents the SQL statement that updates rows of a join relation.
 *
 * @param where Condition that determines which rows to update.
 * @param limit Maximum number of rows to update.
 * @return The number of updated rows.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.UpdateTests.testUpdateWithSingleJoin
 */
fun Join.update(
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    limit: Int? = null,
    body: (UpdateStatement) -> Unit
): Int {
    val stmt = buildStatement { update(where, limit, body) }
    return UpdateBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
}

/**
 * Represents the SQL statement that updates all rows of a join relation.
 *
 * @param limit Maximum number of rows to update.
 * @return The number of updated rows.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.UpdateTests.testUpdateWithSingleJoin
 */
fun Join.update(
    limit: Int? = null,
    body: (UpdateStatement) -> Unit
): Int {
    val stmt = buildStatement { update(null, limit, body) }
    return UpdateBlockingExecutable(stmt).execute(TransactionManager.current()) ?: 0
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
): ReturningBlockingExecutable {
    return where?.let { updateReturning(returning, it, body) } ?: updateReturning(returning, body)
}

/**
 * Represents the SQL statement that updates rows of a table and returns specified data from the updated rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @param where Condition that determines which rows to update.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReturningTests.testUpdateReturning
 */
fun <T : Table> T.updateReturning(
    returning: List<Expression<*>> = columns,
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    body: T.(UpdateStatement) -> Unit
): ReturningBlockingExecutable {
    val stmt = buildStatement { updateReturning(returning, where, body) }
    return ReturningBlockingExecutable(stmt)
}

/**
 * Represents the SQL statement that updates all rows of a table and returns specified data from the updated rows.
 *
 * @param returning Columns and expressions to include in the returned data. This defaults to all columns in the table.
 * @return A [ReturningStatement] that will be executed once iterated over, providing [ResultRow]s containing the specified
 * expressions mapped to their resulting data.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReturningTests.testUpdateReturning
 */
fun <T : Table> T.updateReturning(
    returning: List<Expression<*>> = columns,
    body: T.(UpdateStatement) -> Unit
): ReturningBlockingExecutable {
    val stmt = buildStatement { updateReturning(returning, null, body) }
    return ReturningBlockingExecutable(stmt)
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.UpsertTests.testUpsertWithUniqueIndexConflict
 */
fun <T : Table> T.upsert(
    vararg keys: Column<*>,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    onUpdateExclude: List<Column<*>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    body: T.(UpsertStatement<Long>) -> Unit
): UpsertStatement<Long> {
    val stmt = buildStatement { upsert(keys = keys, onUpdate, onUpdateExclude, where, body) }
    return UpsertBlockingExecutable<Long>(stmt).apply { execute(TransactionManager.current()) }.statement
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.ReturningTests.testUpsertReturning
 */
fun <T : Table> T.upsertReturning(
    vararg keys: Column<*>,
    returning: List<Expression<*>> = columns,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    onUpdateExclude: List<Column<*>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    body: T.(UpsertStatement<Long>) -> Unit
): ReturningBlockingExecutable {
    val stmt = buildStatement { upsertReturning(keys = keys, returning, onUpdate, onUpdateExclude, where, body) }
    return ReturningBlockingExecutable(stmt)
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.UpsertTests.testBatchUpsertWithNoConflict
 */
fun <T : Table, E : Any> T.batchUpsert(
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
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.dml.UpsertTests.testBatchUpsertWithSequence
 */
fun <T : Table, E : Any> T.batchUpsert(
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
private fun <T : Table, E> T.batchUpsert(
    data: Iterator<E>,
    onUpdateList: List<Pair<Column<*>, Any?>>? = null,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    onUpdateExclude: List<Column<*>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    shouldReturnGeneratedValues: Boolean = true,
    vararg keys: Column<*>,
    body: BatchUpsertStatement.(E) -> Unit
): List<ResultRow> = executeBatch(data, body) {
    val stmt = buildStatement {
        batchUpsert(onUpdateList, onUpdate, onUpdateExclude, where, shouldReturnGeneratedValues, keys = keys, body)
    }
    BatchUpsertBlockingExecutable(stmt)
}

@Deprecated(
    "This `mergeFrom()` with a nullable `on` parameter will be removed in future releases. Please leave a comment on " +
        "[YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-494/Inline-DSL-statement-and-query-functions) " +
        "with a use-case if a nullable condition cannot be replaced with the new `mergeFrom()` overloads.",
    ReplaceWith("mergeFrom(source) { body.invoke() }"),
    DeprecationLevel.WARNING
)
@JvmName("mergeFromNullableParam")
fun <D : Table, S : Table> D.mergeFrom(
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
fun <D : Table, S : Table> D.mergeFrom(
    source: S,
    on: SqlExpressionBuilder.() -> Op<Boolean>,
    body: MergeTableStatement.() -> Unit
): MergeTableStatement {
    val stmt = buildStatement { mergeFrom(source, on, body) }
    return MergeBlockingExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
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
fun <D : Table, S : Table> D.mergeFrom(
    source: S,
    body: MergeTableStatement.() -> Unit
): MergeTableStatement {
    val stmt = buildStatement { mergeFrom(source, null, body) }
    return MergeBlockingExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
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
fun <T : Table> T.mergeFrom(
    selectQuery: QueryAlias,
    on: SqlExpressionBuilder.() -> Op<Boolean>,
    body: MergeSelectStatement.() -> Unit
): MergeSelectStatement {
    val stmt = buildStatement { mergeFrom(selectQuery, on, body) }
    return MergeBlockingExecutable(stmt).apply { execute(TransactionManager.current()) }.statement
}

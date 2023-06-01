package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import kotlin.sequences.Sequence

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testSelect01
 */
inline fun FieldSet.select(where: SqlExpressionBuilder.() -> Op<Boolean>): Query = select(SqlExpressionBuilder.where())

fun FieldSet.select(where: Op<Boolean>): Query = Query(this, where)

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testSelectDistinct
 */
fun FieldSet.selectAll(): Query = Query(this, null)

/**
 * That function will make multiple queries with limit equals to [batchSize]
 * and return result as a collection of [ResultRow] sub-collections.
 * [FieldSet] will be sorted by the first auto-increment column and then returned in batches.
 *
 * @param batchSize Size of a sub-collections to return
 * @param where Where condition to be applied
 */
fun FieldSet.selectBatched(
    batchSize: Int = 1000,
    where: SqlExpressionBuilder.() -> Op<Boolean>
): Iterable<Iterable<ResultRow>> {
    return selectBatched(batchSize, SqlExpressionBuilder.where())
}

/**
 * That function will make multiple queries with limit equals to [batchSize]
 * and return result as a collection of [ResultRow] sub-collections.
 * [FieldSet] will be sorted by the first auto-increment column and then returned in batches.
 *
 * @param batchSize Size of a sub-collections to return
 */
fun FieldSet.selectAllBatched(
    batchSize: Int = 1000
): Iterable<Iterable<ResultRow>> {
    return selectBatched(batchSize, Op.TRUE)
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testDelete01
 */
fun <T : Table> T.deleteWhere(limit: Int? = null, offset: Long? = null, op: T.(ISqlExpressionBuilder) -> Op<Boolean>) =
    DeleteStatement.where(TransactionManager.current(), this@deleteWhere, op(SqlExpressionBuilder), false, limit, offset)

fun <T : Table> T.deleteIgnoreWhere(limit: Int? = null, offset: Long? = null, op: T.(ISqlExpressionBuilder) -> Op<Boolean>) =
    DeleteStatement.where(TransactionManager.current(), this@deleteIgnoreWhere, op(SqlExpressionBuilder), true, limit, offset)

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testDelete01
 */
fun Table.deleteAll() =
    DeleteStatement.all(TransactionManager.current(), this@deleteAll)

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testInsert01
 */
fun <T : Table> T.insert(body: T.(InsertStatement<Number>) -> Unit): InsertStatement<Number> = InsertStatement<Number>(this).apply {
    body(this)
    execute(TransactionManager.current())
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testGeneratedKey03
 */
fun <Key : Comparable<Key>, T : IdTable<Key>> T.insertAndGetId(body: T.(InsertStatement<EntityID<Key>>) -> Unit) =
    InsertStatement<EntityID<Key>>(this, false).run {
        body(this)
        execute(TransactionManager.current())
        get(id)
    }

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testBatchInsert01
 */
fun <T : Table, E> T.batchInsert(
    data: Iterable<E>,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchInsertStatement.(E) -> Unit
): List<ResultRow> = batchInsert(data.iterator(), ignoreErrors = ignore, shouldReturnGeneratedValues, body)

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
    if (currentDialect is SQLServerDialect && this.autoIncColumn != null) {
        SQLServerBatchInsertStatement(this, ignoreErrors, shouldReturnGeneratedValues)
    } else {
        BatchInsertStatement(this, ignoreErrors, shouldReturnGeneratedValues)
    }
}

/**
 * Represents the SQL command that either batch inserts new rows into a table, or, if insertions violate unique constraints,
 * first deletes the existing rows before inserting new rows.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @param data Collection of values to use in replace.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testBatchReplace01
 */
fun <T : Table, E : Any> T.batchReplace(
    data: Iterable<E>,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchReplaceStatement.(E) -> Unit
): List<ResultRow> = batchReplace(data.iterator(), shouldReturnGeneratedValues, body)

/**
 * Represents the SQL command that either batch inserts new rows into a table, or, if insertions violate unique constraints,
 * first deletes the existing rows before inserting new rows.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @param data Sequence of values to use in replace.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testBatchReplaceWithSequence
 */
fun <T : Table, E : Any> T.batchReplace(
    data: Sequence<E>,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchReplaceStatement.(E) -> Unit
): List<ResultRow> = batchReplace(data.iterator(), shouldReturnGeneratedValues, body)

/**
 * Represents the SQL command that either batch inserts new rows into a table, or, if insertions violate unique constraints,
 * first deletes the existing rows before inserting new rows.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @param data Iterator over a collection of values to use in replace.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testBatchReplace01
 */
private fun <T : Table, E> T.batchReplace(
    data: Iterator<E>,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchReplaceStatement.(E) -> Unit
): List<ResultRow> = executeBatch(data, body) {
    BatchReplaceStatement(this, shouldReturnGeneratedValues)
}

private fun <E, S : BaseBatchInsertStatement> executeBatch(
    data: Iterator<E>,
    body: S.(E) -> Unit,
    newBatchStatement: () -> S
): List<ResultRow> {
    if (!data.hasNext()) return emptyList()

    var statement = newBatchStatement()

    val result = ArrayList<ResultRow>()
    fun S.handleBatchException(removeLastData: Boolean = false, body: S.() -> Unit) {
        try {
            body()
            if (removeLastData) validateLastBatch()
        } catch (e: BatchDataInconsistentException) {
            if (this.data.size == 1) {
                throw e
            }
            val notTheFirstBatch = this.data.size > 1
            if (notTheFirstBatch) {
                if (removeLastData) {
                    removeLastBatch()
                }
                execute(TransactionManager.current())
                result += resultedValues.orEmpty()
            }
            statement = newBatchStatement()
            if (removeLastData && notTheFirstBatch) {
                statement.addBatch()
                statement.body()
                statement.validateLastBatch()
            }
        }
    }

    data.forEach { element ->
        statement.handleBatchException { addBatch() }
        statement.handleBatchException(true) { body(element) }
    }
    if (statement.arguments().isNotEmpty()) {
        statement.execute(TransactionManager.current())
        result += statement.resultedValues.orEmpty()
    }
    return result
}

fun <T : Table> T.insertIgnore(body: T.(UpdateBuilder<*>) -> Unit): InsertStatement<Long> = InsertStatement<Long>(this, isIgnore = true).apply {
    body(this)
    execute(TransactionManager.current())
}

fun <Key : Comparable<Key>, T : IdTable<Key>> T.insertIgnoreAndGetId(body: T.(UpdateBuilder<*>) -> Unit) =
    InsertStatement<EntityID<Key>>(this, isIgnore = true).run {
        body(this)
        when (execute(TransactionManager.current())) {
            null, 0 -> null
            else -> getOrNull(id)
        }
    }

/**
 * Represents the SQL command that either inserts a new row into a table, or, if insertion would violate a unique constraint,
 * first deletes the existing row before inserting a new row.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ReplaceTests.testReplaceWithExpression
 */
fun <T : Table> T.replace(body: T.(UpdateBuilder<*>) -> Unit): ReplaceStatement<Long> = ReplaceStatement<Long>(this).apply {
    body(this)
    execute(TransactionManager.current())
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testInsertSelect01
 */
fun <T : Table> T.insert(
    selectQuery: AbstractQuery<*>,
    columns: List<Column<*>> = this.columns.filter { !it.columnType.isAutoInc || it.autoIncColumnType?.nextValExpression != null }
) = InsertSelectStatement(columns, selectQuery).execute(TransactionManager.current())

fun <T : Table> T.insertIgnore(
    selectQuery: AbstractQuery<*>,
    columns: List<Column<*>> = this.columns.filter { !it.columnType.isAutoInc || it.autoIncColumnType?.nextValExpression != null }
) = InsertSelectStatement(columns, selectQuery, true).execute(TransactionManager.current())

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testUpdate01
 */
fun <T : Table> T.update(where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null, limit: Int? = null, body: T.(UpdateStatement) -> Unit): Int {
    val query = UpdateStatement(this, limit, where?.let { SqlExpressionBuilder.it() })
    body(query)
    return query.execute(TransactionManager.current())!!
}

fun Join.update(where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null, limit: Int? = null, body: (UpdateStatement) -> Unit): Int {
    val query = UpdateStatement(this, limit, where?.let { SqlExpressionBuilder.it() })
    body(query)
    return query.execute(TransactionManager.current())!!
}

/**
 * Represents the SQL command that either inserts a new row into a table, or updates the existing row if insertion would violate a unique constraint.
 *
 * **Note:** Vendors that do not support this operation directly implement the standard MERGE USING command.
 *
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match.
 * If no columns are provided, primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found.
 */
fun <T : Table> T.upsert(
    vararg keys: Column<*>,
    onUpdate: List<Pair<Column<*>, Expression<*>>>? = null,
    where: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    body: T.(UpsertStatement<Long>) -> Unit
) = UpsertStatement<Long>(this, *keys, onUpdate = onUpdate, where = where?.let { SqlExpressionBuilder.it() }).apply {
    body(this)
    execute(TransactionManager.current())
}

/**
 * Represents the SQL command that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
 *
 * @param data Collection of values to use in batch upsert.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testBatchUpsertWithNoConflict
 */
fun <T : Table, E : Any> T.batchUpsert(
    data: Iterable<E>,
    vararg keys: Column<*>,
    onUpdate: List<Pair<Column<*>, Expression<*>>>? = null,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchUpsertStatement.(E) -> Unit
): List<ResultRow> {
    return batchUpsert(data.iterator(), *keys, onUpdate = onUpdate, shouldReturnGeneratedValues = shouldReturnGeneratedValues, body = body)
}

/**
 * Represents the SQL command that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
 *
 * @param data Sequence of values to use in batch upsert.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testBatchUpsertWithSequence
 */
fun <T : Table, E : Any> T.batchUpsert(
    data: Sequence<E>,
    vararg keys: Column<*>,
    onUpdate: List<Pair<Column<*>, Expression<*>>>? = null,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchUpsertStatement.(E) -> Unit
): List<ResultRow> {
    return batchUpsert(data.iterator(), *keys, onUpdate = onUpdate, shouldReturnGeneratedValues = shouldReturnGeneratedValues, body = body)
}

/**
 * Represents the SQL command that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
 *
 * @param data Iterator over a collection of values to use in batch upsert.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testBatchUpsertWithNoConflict
 */
private fun <T : Table, E> T.batchUpsert(
    data: Iterator<E>,
    vararg keys: Column<*>,
    onUpdate: List<Pair<Column<*>, Expression<*>>>? = null,
    shouldReturnGeneratedValues: Boolean = true,
    body: BatchUpsertStatement.(E) -> Unit
): List<ResultRow> = executeBatch(data, body) {
    BatchUpsertStatement(this, *keys, onUpdate = onUpdate, shouldReturnGeneratedValues = shouldReturnGeneratedValues)
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.tableExists02
 */
fun Table.exists(): Boolean = currentDialect.tableExists(this)

private fun FieldSet.selectBatched(
    batchSize: Int = 1000,
    whereOp: Op<Boolean>
): Iterable<Iterable<ResultRow>> {
    require(batchSize > 0) { "Batch size should be greater than 0" }

    val autoIncColumn = try {
        source.columns.first { it.columnType.isAutoInc }
    } catch (_: NoSuchElementException) {
        throw UnsupportedOperationException("Batched select only works on tables with an autoincrementing column")
    }

    return object : Iterable<Iterable<ResultRow>> {
        override fun iterator(): Iterator<Iterable<ResultRow>> {
            return iterator {
                var lastOffset = 0L
                while (true) {
                    val query =
                        select { whereOp and (autoIncColumn greater lastOffset) }
                            .limit(batchSize)
                            .orderBy(autoIncColumn, SortOrder.ASC)

                    // query.iterator() executes the query
                    val results = query.iterator().asSequence().toList()

                    if (results.isNotEmpty()) {
                        yield(results)
                    }

                    if (results.size < batchSize) break

                    lastOffset = toLong(results.last()[autoIncColumn]!!)
                }
            }
        }

        private fun toLong(autoIncVal: Any): Long = when (autoIncVal) {
            is EntityID<*> -> toLong(autoIncVal.value)
            is Int -> autoIncVal.toLong()
            else -> autoIncVal as Long
        }
    }
}

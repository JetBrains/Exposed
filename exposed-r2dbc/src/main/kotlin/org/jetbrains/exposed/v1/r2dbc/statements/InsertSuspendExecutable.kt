package org.jetbrains.exposed.v1.r2dbc.statements

import io.r2dbc.spi.Result
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.reduce
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.BatchReplaceStatement
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.ReplaceStatement
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2DBCRow
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult
import org.jetbrains.exposed.v1.r2dbc.statements.api.metadata
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager

/**
 * Represents the execution logic for an SQL statement that inserts a new row into a table.
 */
open class InsertSuspendExecutable<Key : Any, S : InsertStatement<Key>>(
    override val statement: S
) : SuspendExecutable<Int, S> {
    protected open suspend fun R2dbcPreparedStatementApi.execInsertFunction(): Pair<Int?, R2dbcResult?> {
        val inserted = if (statement.arguments().count() > 1 || isAlwaysBatch) {
            executeBatch().takeIf { it.isNotEmpty() }?.sum()
        } else {
            executeUpdate()
            null
        }
        // According to the `processResults()` method when supportsOnlyIdentifiersInGeneratedKeys is false
        // all the columns could be taken from result set
        return if (columnsGeneratedOnDB().isNotEmpty() || !currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            inserted to getResultRow()
        } else {
            // since no result will be processed in this case, must apply a terminal operator to collect the flow
            val count = try {
                getResultRow()?.rowsUpdated()?.reduce(Int::plus) ?: 0
            } catch (_: IllegalStateException) { // result already consumed
                // only case it would have already been consumed is when executeBatch() + (wasGeneratedKeysRequested == false)
                inserted
            } catch (_: NoSuchElementException) { // flow might be empty
                0
            }
            count to null
        }
    }

    @OptIn(InternalApi::class)
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        val (inserted, rs) = execInsertFunction()

        val (processedCount, processedResults) = processResults(rs)
        statement.resultedValues = processedResults
        val affectedRowCount = inserted ?: processedCount
        statement.insertedCount = affectedRowCount
        return affectedRowCount
    }

    @OptIn(InternalApi::class)
    override suspend fun prepared(transaction: R2dbcTransaction, sql: String): R2dbcPreparedStatementApi = when {
        // https://github.com/pgjdbc/pgjdbc/issues/1168
        // Column names always escaped/quoted in RETURNING clause
        columnsGeneratedOnDB().isNotEmpty() && currentDialect is PostgreSQLDialect ->
            transaction.connection.prepareStatement(sql, true)

        autoIncColumns.isNotEmpty() -> {
            // [MariaDB] r2dbc returnGeneratedValues() does not support adding RETURNING clause to REPLACE statements
            // see: org.mariadb.r2dbc.util.ClientParser.parameterPartsCheckReturning() switch case 82 -> 114
            val needsManualReturning = (statement is ReplaceStatement<*> || statement is BatchReplaceStatement) &&
                currentDialect is MariaDBDialect
            val generatedColumns = autoIncColumns.map { it.name.inProperCase() }.toTypedArray()

            if (needsManualReturning) {
                val replaceReturning = "$sql RETURNING ${generatedColumns.joinToString()}"
                transaction.connection.prepareStatement(replaceReturning, false)
            } else {
                transaction.connection.prepareStatement(sql, generatedColumns)
            }
        }

        else -> transaction.connection.prepareStatement(sql, false)
    }

    protected val autoIncColumns: List<Column<*>>
        get() {
            @OptIn(InternalApi::class)
            val nextValExpressionColumns = statement.values.filterValues { it is NextVal<*> }.keys
            return statement.targets.flatMap { it.columns }.filter { column ->
                when {
                    column.autoIncColumnType?.nextValExpression != null -> currentDialect.supportsSequenceAsGeneratedKeys
                    column.columnType.isAutoInc -> true
                    column in nextValExpressionColumns -> currentDialect.supportsSequenceAsGeneratedKeys
                    else -> false
                }
            }
        }

    private suspend fun processResults(rs: R2dbcResult?): Pair<Int, List<ResultRow>> {
        val (count, allResultSetsValues) = rs?.returnedValues() ?: (0 to null)

        @Suppress("UNCHECKED_CAST")
        val results = statement.arguments!!
            .mapIndexed { index, columnValues ->
                val resultSetValues = allResultSetsValues?.getOrNull(index) ?: hashMapOf()
                val argumentValues = columnValues.toMap()
                    .filterValues { it != DefaultValueMarker }
                    .let { unwrapColumnValues(it) }

                argumentValues + resultSetValues
            }
            .map { unwrapColumnValues(defaultAndNullableValues(exceptColumns = it.keys)) + it }
            .map { ResultRow.createAndFillValues(it as Map<Expression<*>, Any?>) }

        return count to results
    }

    private fun defaultAndNullableValues(exceptColumns: Collection<Column<*>>): Map<Column<*>, Any?> {
        return statement.table.columns
            .filter { column -> !exceptColumns.contains(column) }
            .mapNotNull { column ->
                val defaultFn = column.defaultValueFun
                when {
                    defaultFn != null -> column to defaultFn()
                    column.columnType.nullable -> column to null
                    else -> null
                }
            }
            .toMap()
    }

    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
    private suspend fun R2dbcResult.returnedValues(): Pair<Int, ArrayList<MutableMap<Column<*>, Any?>>> {
        val resultSetsValues = arrayListOf<MutableMap<Column<*>, Any?>>()
        val resultSetsCounts = mutableListOf<Int>()
        var columnIndexesInResultSet: List<Pair<Column<*>, Int>>? = null
        val firstAutoIncColumn = autoIncColumns.firstOrNull()
        val dialect = currentDialect
        val sendsResultsOnFailure = dialect is MysqlDialect && dialect !is MariaDBDialect

        var isSqlServerBatchInsert = false

        mapSegments { segment ->
            var values: MutableMap<Column<*>, Any?>? = null
            var count: Int? = null

            // PostgreSQL sends segments separately, but MySQL for example, sends them all together;
            // So segment can match multiple types, & using a when block would lose part of the result data needed
            if (segment is Result.UpdateCount) {
                count = segment.value().toInt()
            }

            if (segment is Result.RowSegment && !isSQLServerLastRowId(segment, isSqlServerBatchInsert)) {
                isSqlServerBatchInsert = isSqlServerBatchInsert || isSQLServerBatchSegment(segment)

                val row = R2DBCRow(segment.row(), typeMapping)

                if (columnIndexesInResultSet == null) {
                    columnIndexesInResultSet = row.metadata.returnedColumns()
                }

                values = if (firstAutoIncColumn == null && !columnIndexesInResultSet.isNotEmpty()) {
                    null
                } else {
                    try {
                        val returnedValues: MutableMap<Column<*>, Any?> = columnIndexesInResultSet.associateTo(mutableMapOf()) {
                            it.first to it.first.columnType.readObject(row, it.second)
                        }
                        if (returnedValues.isEmpty() && firstAutoIncColumn != null) {
                            returnedValues[firstAutoIncColumn] = row.getObject(1)
                        }
                        returnedValues
                    } catch (cause: ArrayIndexOutOfBoundsException) {
                        // EXPOSED-191 Flaky Oracle test on TC build
                        // this try/catch should help to get information about the flaky test.
                        // try/catch can be safely removed after the fixing the issue.
                        // TooGenericExceptionCaught suppress also can be removed

                        val preparedSql = this@InsertSuspendExecutable.statement.prepareSQL(TransactionManager.current(), prepared = true)

                        val returnedColumnsString = columnIndexesInResultSet
                            .mapIndexed { index, pair ->
                                "column: ${pair.first.name}, index: ${pair.second} (columns-list-index: $index)"
                            }
                            .joinToString(prefix = "[", postfix = "]", separator = ", ")

                        exposedLogger.error(
                            "ArrayIndexOutOfBoundsException on processResults. " +
                                "Table: ${this@InsertSuspendExecutable.statement.table.tableName}, " +
                                "firstAutoIncColumn: ${firstAutoIncColumn?.name}, " +
                                "returnedColumnsString: $returnedColumnsString. " +
                                "Failed SQL: $preparedSql",
                            cause
                        )
                        throw cause
                    }
                }
            }

            if (segment is Result.Message) {
                @Suppress("ThrowingExceptionsWithoutMessageOrCause")
                throw segment.exception()
            }

            flowOf(count to values)
        }.collect { (count, values) ->
            // MySQL return a result with an id value of 0 if an insert did not occur
            // which leads to incorrect stored values in the insert statement;
            // If an insert did not occur, no generated values should be received and stored statement
            // values should be generated based on user-provided values
            // Todo review potential edge cases not covered by tests
            count?.let { c ->
                resultSetsCounts.add(c)
                values.takeIf { sendsResultsOnFailure && c != 0 }?.let { resultSetsValues.add(it) }
            }
            values.takeIf { !sendsResultsOnFailure }?.let { resultSetsValues.add(it) }
        }

        // Some databases, like H2 and MariaDB, aren't returning UpdateCount segments;
        // The workaround below therefore fails for upsert operations
        // Todo review alternatives for these dialects
        val inserted = if (resultSetsCounts.isEmpty()) {
            resultSetsValues.size
        } else {
            resultSetsCounts.sum()
        }

        if (firstAutoIncColumn != null || columnIndexesInResultSet?.isNotEmpty() == true) {
            if (inserted > 1 && firstAutoIncColumn != null && resultSetsValues.isNotEmpty() && !currentDialect.supportsMultipleGeneratedKeys) {
                // H2 only returns one last generated key...
                (resultSetsValues[0][firstAutoIncColumn] as? Number)?.toLong()?.let {
                    var id = it

                    while (resultSetsValues.size < inserted) {
                        id -= 1
                        resultSetsValues.add(0, mutableMapOf(firstAutoIncColumn to id))
                    }
                }
            }

            check(
                this@InsertSuspendExecutable.statement.isIgnore || resultSetsValues.isEmpty() || resultSetsValues.size == inserted ||
                    currentDialect.supportsTernaryAffectedRowValues
            ) {
                "Number of autoincs (${resultSetsValues.size}) doesn't match number of batch entries ($inserted)"
            }
        }

        return inserted to resultSetsValues
    }

    private fun RowMetadata?.returnedColumns(): List<Pair<Column<*>, Int>> {
        val columns = if (currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            autoIncColumns
        } else {
            this@InsertSuspendExecutable.statement.table.columns
        }
        return columns.mapNotNull { col ->
            this?.columnMetadatas?.withIndex()?.firstOrNull { it.value.name == col.name }?.let {
                col to (it.index + 1)
            }
        }
    }

    /* This check is needed for SQLServer batch insert. The problem is that R2DBC driver for SQLServer database
    returns extra `Result.RowSegment` with the id of the last row. Every insert of batch is returned as
    a segment with `GENERATED_KEYS` column name in metadata, but after them the one extra segment with `id` name
    is also returned.

    We can't just filter segments with name `id`, because that name is also returned in general insert for column
    with name `id`.

    This check is quite optimistic, and we recognize the whole insert as batch insert if there is at least one
    `GENERATED_KEYS` segment in the whole sequence. */
    private fun isSQLServerLastRowId(segment: Result.RowSegment, isSqlServerBatchInsert: Boolean): Boolean {
        return currentDialect is SQLServerDialect && isSqlServerBatchInsert && segment.row().metadata.columnMetadatas.let {
            it.size == 1 && it[0].name != "GENERATED_KEYS"
        }
    }

    private fun isSQLServerBatchSegment(segment: Result.RowSegment): Boolean {
        return currentDialect is SQLServerDialect && segment.row().metadata.columnMetadatas.let {
            it.size == 1 && it[0].name == "GENERATED_KEYS"
        }
    }

    /**
     * Returns all the columns for which value can not be derived without actual request.
     *
     * At the current moment it is the auto increment columns and columns with database side generated defaults
     */
    @OptIn(InternalApi::class)
    private fun columnsGeneratedOnDB(): Collection<Column<*>> = (autoIncColumns + statement.columnsWithDatabaseDefaults()).toSet()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Expression<*>> unwrapColumnValues(values: Map<T, Any?>): Map<T, Any?> = values.mapValues { (col, value) ->
        if (col !is ExpressionWithColumnType<*>) return@mapValues value

        value?.let { (col.columnType as? ColumnWithTransform<Any, Any>)?.unwrapRecursive(it) } ?: value
    }
}

package org.jetbrains.exposed.r2dbc.sql.statements

import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcResult
import org.jetbrains.exposed.r2dbc.sql.statements.api.metadata
import org.jetbrains.exposed.r2dbc.sql.transactions.TransactionManager
import org.jetbrains.exposed.r2dbc.sql.vendors.inProperCase
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.currentDialect

open class InsertSuspendExecutable<Key : Any, S : InsertStatement<Key>>(
    override val statement: S
) : SuspendExecutable<Int, S> {
    protected open suspend fun R2dbcPreparedStatementApi.execInsertFunction(): Pair<Int, R2dbcResult?> {
        val inserted = if (statement.arguments().count() > 1 || isAlwaysBatch) executeBatch().sum() else executeUpdate()
        // According to the `processResults()` method when supportsOnlyIdentifiersInGeneratedKeys is false
        // all the columns could be taken from result set
        val rs = if (columnsGeneratedOnDB().isNotEmpty() || !currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            getResultRow()
        } else {
            null
        }
        return inserted to rs
    }

    @OptIn(InternalApi::class)
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        val (_, rs) = execInsertFunction()
//        statement.insertedCount = this
        val processResults = processResults(rs)
        statement.resultedValues = processResults
        statement.insertedCount = processResults.size
        return processResults.size
    }

    override suspend fun prepared(transaction: R2dbcTransaction, sql: String): R2dbcPreparedStatementApi = when {
        // https://github.com/pgjdbc/pgjdbc/issues/1168
        // Column names always escaped/quoted in RETURNING clause
        columnsGeneratedOnDB().isNotEmpty() && currentDialect is PostgreSQLDialect ->
            transaction.connection.prepareStatement(sql, true)

        autoIncColumns.isNotEmpty() ->
            // http://viralpatel.net/blogs/oracle-java-jdbc-get-primary-key-insert-sql/
            transaction.connection.prepareStatement(sql, autoIncColumns.map { it.name.inProperCase() }.toTypedArray())

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

    private suspend fun processResults(rs: R2dbcResult?): List<ResultRow> {
        val allResultSetsValues = rs?.returnedValues()

        @Suppress("UNCHECKED_CAST")
        return statement.arguments!!
            .mapIndexed { index, columnValues ->
                val resultSetValues = allResultSetsValues?.getOrNull(index) ?: hashMapOf()
                val argumentValues = columnValues.toMap()
                    .filterValues { it != DefaultValueMarker }
                    .let { unwrapColumnValues(it) }

                argumentValues + resultSetValues
            }
            .map { unwrapColumnValues(defaultAndNullableValues(exceptColumns = it.keys)) + it }
            .map { ResultRow.createAndFillValues(it as Map<Expression<*>, Any?>) }
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

    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught")
    private suspend fun R2dbcResult.returnedValues(): ArrayList<MutableMap<Column<*>, Any?>> {
        val resultSetsValues = arrayListOf<MutableMap<Column<*>, Any?>>()
        var columnIndexesInResultSet: List<Pair<Column<*>, Int>>? = null
        val firstAutoIncColumn = autoIncColumns.firstOrNull()

        mapRows<MutableMap<Column<*>, Any?>?> { row ->
            if (columnIndexesInResultSet == null) {
                columnIndexesInResultSet = row.metadata.returnedColumns()
            }

            if (firstAutoIncColumn == null && !columnIndexesInResultSet.isNotEmpty()) {
                return@mapRows null
            }

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
        }.filterNotNull().toList(resultSetsValues)

        val inserted = resultSetsValues.size

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

        return resultSetsValues
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

    /**
     * Returns all the columns for which value can not be derived without actual request.
     *
     * At the current moment it is the auto increment columns and columns with database side generated defaults
     */
    @OptIn(InternalApi::class)
    private fun columnsGeneratedOnDB(): Collection<Column<*>> = (autoIncColumns + statement.columnsWithDatabaseDefaults()).toSet()

    private fun <T : Expression<*>> unwrapColumnValues(values: Map<T, Any?>): Map<T, Any?> = values.mapValues { (col, value) ->
        if (col !is ExpressionWithColumnType<*>) return@mapValues value

        value?.let { (col.columnType as? ColumnWithTransform<Any, Any>)?.unwrapRecursive(it) } ?: value
    }
}

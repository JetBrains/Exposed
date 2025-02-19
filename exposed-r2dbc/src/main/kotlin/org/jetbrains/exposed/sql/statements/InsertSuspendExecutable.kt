package org.jetbrains.exposed.sql.statements

import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcResult
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.sql.SQLException

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

    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        val (inserted, rs) = execInsertFunction()
        @OptIn(InternalApi::class)
        return inserted.apply {
            statement.insertedCount = this
            statement.resultedValues = processResults(rs, this)
        }
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

    private suspend fun processResults(rs: R2dbcResult?, inserted: Int): List<ResultRow> {
        val allResultSetsValues = rs?.returnedValues(inserted)

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
    private suspend fun R2dbcResult.returnedValues(inserted: Int): ArrayList<MutableMap<Column<*>, Any?>> {
        if (inserted == 0) return arrayListOf()

        val resultSetsValues = arrayListOf<MutableMap<Column<*>, Any?>>()
        var columnIndexesInResultSet: List<Pair<Column<*>, Int>>? = null
        val firstAutoIncColumn = autoIncColumns.firstOrNull()

        flow {
            this@returnedValues.result.collect { rs ->
                rs.map { row, rm ->
                    this@returnedValues.currentRecord = R2dbcResult.R2dbcRecord(row, rm)

                    if (columnIndexesInResultSet == null) {
                        columnIndexesInResultSet = rm.returnedColumns()
                    }

                    if (firstAutoIncColumn != null || columnIndexesInResultSet.isNotEmpty()) {
                        try {
                            val returnedValues: MutableMap<Column<*>, Any?> = columnIndexesInResultSet.associateTo(mutableMapOf()) {
                                it.first to it.first.columnType.readObject(this@returnedValues, it.second)
                            }
                            if (returnedValues.isEmpty() && firstAutoIncColumn != null) {
                                returnedValues[firstAutoIncColumn] = this@returnedValues.getObject(1)
                            }
                            returnedValues
                        } catch (cause: ArrayIndexOutOfBoundsException) {
                            // EXPOSED-191 Flaky Oracle test on TC build
                            // this try/catch should help to get information about the flaky test.
                            // try/catch can be safely removed after the fixing the issue.
                            // TooGenericExceptionCaught suppress also can be removed

                            val preparedSql = this@InsertExecutable.statement.prepareSQL(TransactionManager.current(), prepared = true)

                            val returnedColumnsString = columnIndexesInResultSet
                                .mapIndexed { index, pair ->
                                    "column: ${pair.first.name}, index: ${pair.second} (columns-list-index: $index)"
                                }
                                .joinToString(prefix = "[", postfix = "]", separator = ", ")

                            exposedLogger.error(
                                "ArrayIndexOutOfBoundsException on processResults. " +
                                    "Table: ${this@InsertExecutable.statement.table.tableName}, " +
                                    "firstAutoIncColumn: ${firstAutoIncColumn?.name}, " +
                                    "inserted: $inserted, returnedColumnsString: $returnedColumnsString. " +
                                    "Failed SQL: $preparedSql",
                                cause
                            )
                            throw cause
                        }
                    } else {
                        null
                    }
                }.collect { emit(it) }
            }
        }.filterNotNull().toList(resultSetsValues)

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

            assert(
                this@InsertExecutable.statement.isIgnore || resultSetsValues.isEmpty() || resultSetsValues.size == inserted ||
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
            this@InsertExecutable.statement.table.columns
        }
        return columns.mapNotNull { col ->
            @Suppress("SwallowedException")
            try {
                this?.columnMetadatas?.withIndex()?.firstOrNull { it.value.name == col.name }?.let {
                    col to it.index
                }
            } catch (e: SQLException) {
                null
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

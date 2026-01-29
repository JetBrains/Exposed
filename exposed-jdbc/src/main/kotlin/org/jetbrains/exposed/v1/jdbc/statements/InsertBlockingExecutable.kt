package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Represents the execution logic for an SQL statement that inserts a new row into a table.
 */
open class InsertBlockingExecutable<Key : Any, S : InsertStatement<Key>>(
    override val statement: S
) : BlockingExecutable<Int, S> {
    protected open fun JdbcPreparedStatementApi.execInsertFunction(): Pair<Int, ResultSet?> {
        val inserted = if (statement.arguments().count() > 1 || isAlwaysBatch) executeBatch().sum() else executeUpdate()
        // According to the `processResults()` method when supportsOnlyIdentifiersInGeneratedKeys is false
        // all the columns could be taken from result set
        val rs = if (columnsGeneratedOnDB().isNotEmpty() || !currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            resultSet
        } else {
            null
        }
        return inserted to rs?.result
    }

    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        val (inserted, rs) = execInsertFunction()
        @OptIn(InternalApi::class)
        return inserted.apply {
            statement.insertedCount = this
            statement.resultedValues = processResults(rs, this)
            rs?.close()
        }
    }

    override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi = when {
        // https://github.com/pgjdbc/pgjdbc/issues/1168
        // Column names always escaped/quoted in RETURNING clause
        columnsGeneratedOnDB().isNotEmpty() && currentDialect is PostgreSQLDialect ->
            transaction.connection.prepareStatement(sql, true)

        autoIncColumns.isNotEmpty() -> {
            // http://viralpatel.net/blogs/oracle-java-jdbc-get-primary-key-insert-sql/
            @OptIn(InternalApi::class)
            transaction.connection.prepareStatement(sql, autoIncColumns.map { it.name.inProperCase() }.toTypedArray())
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

    private fun processResults(rs: ResultSet?, inserted: Int): List<ResultRow> {
        val allResultSetsValues = rs?.returnedValues(inserted)

        @Suppress("UNCHECKED_CAST")
        return statement.arguments!!
            // Join the values from ResultSet with arguments
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
    private fun ResultSet.returnedValues(inserted: Int): ArrayList<MutableMap<Column<*>, Any?>> {
        if (inserted == 0) return arrayListOf()

        val resultSetsValues = arrayListOf<MutableMap<Column<*>, Any?>>()

        val columnIndexesInResultSet = returnedColumns()

        val firstAutoIncColumn = autoIncColumns.firstOrNull()
        if (firstAutoIncColumn != null || columnIndexesInResultSet.isNotEmpty()) {
            while (next()) {
                try {
                    val returnedValues = columnIndexesInResultSet.associateTo(mutableMapOf()) {
                        it.first to it.first.columnType.readObject(JdbcResult(this), it.second)
                    }
                    if (returnedValues.isEmpty() && firstAutoIncColumn != null) {
                        returnedValues[firstAutoIncColumn] = getObject(1)
                    }
                    resultSetsValues.add(returnedValues)
                } catch (cause: ArrayIndexOutOfBoundsException) {
                    // EXPOSED-191 Flaky Oracle test on TC build
                    // this try/catch should help to get information about the flaky test.
                    // try/catch can be safely removed after the fixing the issue.
                    // TooGenericExceptionCaught suppress also can be removed

                    val preparedSql = this@InsertBlockingExecutable.statement.prepareSQL(TransactionManager.current(), prepared = true)

                    val returnedColumnsString = columnIndexesInResultSet
                        .mapIndexed { index, pair -> "column: ${pair.first.name}, index: ${pair.second} (columns-list-index: $index)" }
                        .joinToString(prefix = "[", postfix = "]", separator = ", ")

                    exposedLogger.error(
                        "ArrayIndexOutOfBoundsException on processResults. " +
                            "Table: ${this@InsertBlockingExecutable.statement.table.tableName}, " +
                            "firstAutoIncColumn: ${firstAutoIncColumn?.name}, " +
                            "inserted: $inserted, returnedColumnsString: $returnedColumnsString. " +
                            "Failed SQL: $preparedSql",
                        cause
                    )
                    throw cause
                }
            }

            if (inserted > 1 && firstAutoIncColumn != null && resultSetsValues.isNotEmpty() && !currentDialect.supportsMultipleGeneratedKeys) {
                // H2/SQLite only returns one last generated key...
                (resultSetsValues[0][firstAutoIncColumn] as? Number)?.toLong()?.let {
                    var id = it

                    while (resultSetsValues.size < inserted) {
                        id -= 1
                        resultSetsValues.add(0, mutableMapOf(firstAutoIncColumn to id))
                    }
                }
            }

            assert(
                this@InsertBlockingExecutable.statement.isIgnore || resultSetsValues.isEmpty() || resultSetsValues.size == inserted ||
                    currentDialect.supportsTernaryAffectedRowValues
            ) {
                "Number of autoincs (${resultSetsValues.size}) doesn't match number of batch entries ($inserted)"
            }
        }

        return resultSetsValues
    }

    /**
     * Returns indexes of the table columns in [ResultSet]
     */
    private fun ResultSet?.returnedColumns(): List<Pair<Column<*>, Int>> {
        val columns = if (currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            autoIncColumns
        } else {
            this@InsertBlockingExecutable.statement.table.columns
        }
        return columns.mapNotNull { col ->
            try {
                this?.findColumn(col.name)?.let { col to it }
            } catch (_: SQLException) {
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

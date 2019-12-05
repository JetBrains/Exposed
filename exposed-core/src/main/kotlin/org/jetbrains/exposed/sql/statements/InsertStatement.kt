package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.RedshiftDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.sql.ResultSet
import java.sql.SQLException

/**
 * isIgnore is supported for mysql only
 */
open class InsertStatement<Key:Any>(val table: Table, val isIgnore: Boolean = false) : UpdateBuilder<Int>(StatementType.INSERT, listOf(table)) {
    open val flushCache = true
    var resultedValues: List<ResultRow>? = null
        private set

    infix operator fun <T> get(column: Column<T>): T {
        val row = resultedValues?.firstOrNull() ?: error("No key generated")
        return row[column]
    }

    fun <T> getOrNull(column: Column<T>): T? = resultedValues?.firstOrNull()?.getOrNull(column)

    private fun processResults(rs: ResultSet?, inserted: Int): List<ResultRow> {
        val autoGeneratedKeys = arrayListOf<MutableMap<Column<*>, Any?>>()

        if (inserted > 0) {
            val returnedColumns =
                (if (currentDialect.supportsOnlyIdentifiersInGeneratedKeys) autoIncColumns else table.columns).mapNotNull { col ->
                    try {
                        rs?.findColumn(col.name)?.let { col to it }
                    } catch (e: SQLException) {
                        null
                    }
                }

            val firstAutoIncColumn = autoIncColumns.firstOrNull() 
            if (firstAutoIncColumn != null || returnedColumns.isNotEmpty()) {
                while (rs?.next() == true) {
                    val returnedValues = returnedColumns.associateTo(mutableMapOf()) { it.first to rs.getObject(it.second) }
                    if (returnedValues.isEmpty() && firstAutoIncColumn != null)
                        returnedValues[firstAutoIncColumn] = rs.getObject(1)
                    autoGeneratedKeys.add(returnedValues)
                }

                if (inserted > 1 && firstAutoIncColumn != null && autoGeneratedKeys.isNotEmpty() && !currentDialect.supportsMultipleGeneratedKeys) {
                    // H2/SQLite only returns one last generated key...
                    (autoGeneratedKeys[0][firstAutoIncColumn] as? Number)?.toLong()?.let {
                        var id = it

                        while (autoGeneratedKeys.size < inserted) {
                            id -= 1
                            autoGeneratedKeys.add(0, mutableMapOf(firstAutoIncColumn to id))
                        }
                    }
                }

                /** FIXME: https://github.com/JetBrains/Exposed/issues/129
                 *  doesn't work with MySQL `INSERT ... ON DUPLICATE UPDATE`
                 */
//            assert(isIgnore || autoGeneratedKeys.isEmpty() || autoGeneratedKeys.size == inserted) {
//                "Number of autoincs (${autoGeneratedKeys.size}) doesn't match number of batch entries ($inserted)"
//            }
            }
        }

        arguments!!.forEachIndexed { itemIndx, pairs ->
            val map = autoGeneratedKeys.getOrNull(itemIndx) ?: hashMapOf<Column<*>, Any?>().apply {
                autoGeneratedKeys.add(itemIndx, this)
            }
            pairs.forEach { (col, value) ->
                if (value != DefaultValueMarker) {
                    if (col.columnType.isAutoInc)
                        map.getOrPut(col) { value }
                    else
                        map[col] = value
                }
            }

//            pairs.filter{ it.second != DefaultValueMarker }.forEach { (col, value) ->
//                map.getOrPut(col){ value }
//            }
        }
        return autoGeneratedKeys.map { ResultRow.createAndFillValues(it as Map<Expression<*>, Any?>) }
    }

    protected open fun valuesAndDefaults(values: Map<Column<*>, Any?> = this.values): Map<Column<*>, Any?> {
        val columnsWithNotNullDefault = targets.flatMap { it.columns }.filter {
            (it.dbDefaultValue != null || it.defaultValueFun != null) && it !in values.keys
        }
        return values + columnsWithNotNullDefault.map { it to (it.defaultValueFun?.invoke() ?: DefaultValueMarker) }
    }

    override fun prepareSQL(transaction: Transaction): String {
        val builder = QueryBuilder(true)
        val values = arguments!!.first()
        val sql = if(values.isEmpty()) ""
        else with(builder) {
            values.appendTo(prefix = "VALUES (", postfix = ")") { (col, value) ->
                registerArgument(col, value)
            }
            toString()
        }
        return transaction.db.dialect.functionProvider.insert(isIgnore, table, values.map { it.first }, sql, transaction)
    }

    protected open fun PreparedStatementApi.execInsertFunction() : Pair<Int, ResultSet?> {
        val inserted = if (arguments().count() > 1 || isAlwaysBatch) executeBatch().count() else executeUpdate()
        val rs = if (autoIncColumns.isNotEmpty()) { resultSet } else null
        return inserted to rs
    }

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        val (inserted, rs) = execInsertFunction()
        return inserted.apply {
            resultedValues = processResults(rs, this)
        }
    }

    protected val autoIncColumns = targets.flatMap { it.columns }.filter {
        it.columnType.isAutoInc || (it.columnType is EntityIDColumnType<*> && !currentDialect.supportsOnlyIdentifiersInGeneratedKeys)
    }

    override fun prepared(transaction: Transaction, sql: String): PreparedStatementApi = when {
        // https://github.com/JetBrains/Exposed/issues/711
        // Redshift is not supporting RETURNING feature
        currentDialect is RedshiftDialect ->
            transaction.connection.prepareStatement(sql, false)

        // https://github.com/pgjdbc/pgjdbc/issues/1168
        // Column names always escaped/quoted in RETURNING clause
        autoIncColumns.isNotEmpty() && currentDialect is PostgreSQLDialect ->
            transaction.connection.prepareStatement(sql, true)

        autoIncColumns.isNotEmpty() ->
            // http://viralpatel.net/blogs/oracle-java-jdbc-get-primary-key-insert-sql/
            transaction.connection.prepareStatement(sql, autoIncColumns.map { it.name.inProperCase() }.toTypedArray())

        else ->
            transaction.connection.prepareStatement(sql, true)
    }

    protected open var arguments: List<List<Pair<Column<*>, Any?>>>? = null
        get() = field ?: run {
            val nullableColumns = table.columns.filter { it.columnType.nullable }
            val valuesAndDefaults = valuesAndDefaults()
            val result = (valuesAndDefaults + (nullableColumns - valuesAndDefaults.keys).associate { it to null }).toList().sortedBy { it.first }
            listOf(result).apply { field = this }
        }

    override fun arguments() = arguments!!.map { args ->
        args.filter { (_, value) ->
            value != DefaultValueMarker  && value !is Expression<*>
        }.map { it.first.columnType to it.second }
    }
}

package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal typealias TableAndColumnName = Pair<String, String>

abstract class DataTypeProvider {
    open fun integerAutoincType() = "INT AUTO_INCREMENT"

    open fun integerType() = "INT"

    open fun longAutoincType() = "BIGINT AUTO_INCREMENT"

    open fun longType() = "BIGINT"

    open fun floatType() = "FLOAT"

    open fun doubleType() = "DOUBLE PRECISION"

    open fun uuidType() = "BINARY(16)"

    open fun dateTimeType() = "DATETIME"

    open fun blobType(): String = "BLOB"

    open fun binaryType(length: Int): String = "VARBINARY($length)"

    open abstract fun binaryType(): String

    open fun booleanType(): String = "BOOLEAN"

    open fun booleanToStatementString(bool: Boolean) = bool.toString()

    open fun uuidToDB(value: UUID) : Any =
            ByteBuffer.allocate(16).putLong(value.mostSignificantBits).putLong(value.leastSignificantBits).array()

    open fun booleanFromStringToBoolean(value: String): Boolean = value.toBoolean()

    open fun textType() = "TEXT"
    open val blobAsStream = false

    open fun processForDefaultValue(e: Expression<*>) : String = when {
        e is LiteralOp<*> -> "$e"
        currentDialect is MysqlDialect -> "$e"
        else -> "($e)"
    }
}

abstract class FunctionProvider {

    open val DEFAULT_VALUE_EXPRESSION = "DEFAULT VALUES"

    open fun<T:String?> substring(expr: Expression<T>, start: Expression<Int>,
                                  length: Expression<Int>, builder: QueryBuilder,
                                  prefix: String = "SUBSTRING") = builder {
        append(prefix, "(", expr, ", ", start, ", ", length, ")")
    }

    open fun nextVal(seq: Sequence, builder: QueryBuilder) = builder {
        append(seq.identifier,".NEXTVAL")
    }

    open fun random(seed: Int?): String = "RANDOM(${seed?.toString().orEmpty()})"

    open fun cast(expr: Expression<*>, type: IColumnType, builder: QueryBuilder) = builder {
        append("CAST(", expr, " AS ", type.sqlType(), ")")
    }

    open fun<T:String?> ExpressionWithColumnType<T>.match(pattern: String, mode: MatchMode? = null): Op<Boolean> = with(SqlExpressionBuilder) { this@match.like(pattern) }

    open fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        if (ignore) {
            transaction.throwUnsupportedException("There's no generic SQL for INSERT IGNORE. There must be vendor specific implementation")
        }

        val (columnsExpr, valuesExpr) = if (columns.isNotEmpty()) {
            columns.joinToString(prefix = "(", postfix = ")") { transaction.identity(it) } to expr
        } else "" to DEFAULT_VALUE_EXPRESSION

        return "INSERT INTO ${transaction.identity(table)} $columnsExpr $valuesExpr"
    }

    open fun update(targets: ColumnSet, columnsAndValues: List<Pair<Column<*>, Any?>>, limit: Int?, where: Op<Boolean>?, transaction: Transaction): String {
        return with(QueryBuilder(true)) {
            +"UPDATE "
            targets.describe(transaction, this)
            +" SET "
            columnsAndValues.appendTo(this) { (col, value) ->
                append("${transaction.identity(col)}=")
                registerArgument(col, value)
            }

            where?.let {
                +" WHERE "
                +it
            }
            limit?.let { +" LIMIT $it" }
            toString()
        }
    }

    open fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        if (ignore) {
            transaction.throwUnsupportedException("There's no generic SQL for DELETE IGNORE. There must be vendor specific implementation")
        }

        return buildString {
            append("DELETE FROM ")
            append(transaction.identity(table))
            if (where != null) {
                append(" WHERE ")
                append(where)
            }
            if (limit != null) {
                append(" LIMIT ")
                append(limit)
            }
        }
    }

    open fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String
        = transaction.throwUnsupportedException("There's no generic SQL for replace. There must be vendor specific implementation")

    open fun queryLimit(size: Int, offset: Int, alreadyOrdered: Boolean) = buildString {
        if (size > 0) {
            append("LIMIT $size")
            if (offset > 0) {
                append(" OFFSET $offset")
            }
        }
    }

    open fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("GROUP_CONCAT(")
        if (expr.distinct)
            append("DISTINCT ")
        append(expr.expr)
        if (expr.orderBy.isNotEmpty()) {
            expr.orderBy.toList().appendTo(prefix = " ORDER BY ") {
                append(it.first, " ", it.second.name)
            }
        }
        expr.separator?.let {
            append(" SEPARATOR '$it'")
        }
        append(")")
    }

    open fun <T:String?> regexp(expr1: Expression<T>, pattern: Expression<String>, caseSensitive: Boolean, queryBuilder: QueryBuilder) = queryBuilder {
        append("REGEXP_LIKE(")
        append(expr1)
        append(", ")
        append(pattern)
        append(", ")
        if (caseSensitive)
            append("'c'")
        else
            append("'i'")
        append(")")
    }

    interface MatchMode {
        fun mode() : String
    }

    open fun <T:String?> concat(separator: String, queryBuilder: QueryBuilder, vararg expr: Expression<T>) = queryBuilder {
        if (separator == "")
            append("CONCAT(")
        else {
            append("CONCAT_WS(")
            append("'")
            append(separator)
            append("',")
        }
        expr.toList().appendTo { +it }
        append(")")
    }

    open fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("YEAR(")
        append(expr)
        append(")")
    }

    open fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("MONTH(")
        append(expr)
        append(")")
    }

    open fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("DAY(")
        append(expr)
        append(")")
    }

    open fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("HOUR(")
        append(expr)
        append(")")
    }

    open fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("MINUTE(")
        append(expr)
        append(")")
    }

    open fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("SECOND(")
        append(expr)
        append(")")
    }
}

/**
 * type:
 * @see java.sql.Types
 */
data class ColumnMetadata(val name: String, val type: Int, val nullable: Boolean)

interface DatabaseDialect {
    val name: String
    val dataTypeProvider: DataTypeProvider
    val functionProvider: FunctionProvider

    fun getDatabase(): String

    fun allTablesNames(): List<String>
    /**
     * returns list of pairs (column name + nullable) for every table
     */
    fun tableColumns(vararg tables: Table): Map<Table, List<ColumnMetadata>> = emptyMap()

    /**
     * returns map of constraint for a table name/column name pair
     */
    fun columnConstraints(vararg tables: Table): Map<TableAndColumnName, List<ForeignKeyConstraint>> = emptyMap()

    /**
     * return set of indices for each table
     */
    fun existingIndices(vararg tables: Table): Map<Table, List<Index>> = emptyMap()

    fun tableExists(table: Table): Boolean

    fun checkTableMapping(table: Table) = true

    fun resetCaches()

    fun supportsSelectForUpdate(): Boolean
    val supportsMultipleGeneratedKeys: Boolean
    fun isAllowedAsColumnDefault(e: Expression<*>) = e is LiteralOp<*>

    val supportsIfNotExists: Boolean get() = true
    val needsSequenceToAutoInc: Boolean get() = false
    val needsQuotesWhenSymbolsInNames: Boolean get() = true
    fun catalog(transaction: Transaction): String = transaction.connection.catalog


    val defaultReferenceOption : ReferenceOption get() = ReferenceOption.RESTRICT

    val supportsOnlyIdentifiersInGeneratedKeys get() = false

    // Specific SQL statements

    fun createIndex(index: Index): String
    fun dropIndex(tableName: String, indexName: String): String
    fun modifyColumn(column: Column<*>) : String

    fun createSequence(identifier: String,
                       startWith: Int?,
                       incrementBy: Int?,
                       minValue: Int?,
                       maxValue: Int?,
                       cycle: Boolean?,
                       cache: Int?): String = buildString {
        append("CREATE SEQUENCE ")
        if (currentDialect.supportsIfNotExists) {
            append("IF NOT EXISTS ")
        }
        append(identifier)
        appendIfNotNull(" START WITH", startWith)
        appendIfNotNull(" INCREMENT BY", incrementBy)
        appendIfNotNull(" MINVALUE", minValue)
        appendIfNotNull(" MAXVALUE", maxValue)

        if (cycle == true) {
            append(" CYCLE")
        }

        appendIfNotNull(" CACHE", cache)
    }

    fun StringBuilder.appendIfNotNull(str: String, strToCheck: Any?) = apply {
        if (strToCheck != null) {
            this.append("$str $strToCheck")
        }
    }
}

abstract class VendorDialect(override val name: String,
                                      override val dataTypeProvider: DataTypeProvider,
                                      override val functionProvider: FunctionProvider) : DatabaseDialect {

    /* Cached values */
    private var _allTableNames: List<String>? = null
    val allTablesNames: List<String>
        get() {
            if (_allTableNames == null) {
                _allTableNames = allTablesNames()
            }
            return _allTableNames!!
        }

    /* Method always re-read data from DB. Using allTablesNames field is preferred way */
    override fun allTablesNames(): List<String> = TransactionManager.current().connection.metadata { tableNames }

    override fun getDatabase(): String = catalog(TransactionManager.current())

    override fun tableExists(table: Table) = allTablesNames.any { it == table.nameInDatabaseCase() }

    override fun tableColumns(vararg tables: Table): Map<Table, List<ColumnMetadata>> =
            TransactionManager.current().connection.metadata { columns(*tables) }

    protected fun String.quoteIdentifierWhenWrongCaseOrNecessary(tr: Transaction) =
            tr.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(this)

    protected val columnConstraintsCache = ConcurrentHashMap<String, List<ForeignKeyConstraint>>()

    protected open fun fillConstraintCacheForTables(tables: List<Table>) {
        columnConstraintsCache.putAll(TransactionManager.current().db.metadata { tableConstraints(tables) })
    }
    override fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> {
        val constraints = HashMap<Pair<String, String>, MutableList<ForeignKeyConstraint>>()

        val tablesToLoad = tables.filter { !columnConstraintsCache.containsKey(it.nameInDatabaseCase()) }

        fillConstraintCacheForTables(tablesToLoad)
        tables.forEach { table ->
            columnConstraintsCache[table.nameInDatabaseCase()].orEmpty().forEach {
                constraints.getOrPut(it.fromTable to it.fromColumn){arrayListOf()}.add(it)
            }

        }
        return constraints
    }

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>>
        = TransactionManager.current().db.metadata { existingIndices(*tables) }

    override fun resetCaches() {
        _allTableNames = null
        columnConstraintsCache.clear()
        TransactionManager.current().db.metadata { cleanCache() }
    }

    override fun createIndex(index: Index): String {
        val t = TransactionManager.current()
        val quotedTableName = t.identity(index.table)
        val quotedIndexName = t.db.identifierManager.cutIfNecessaryAndQuote(index.indexName)
        val columnsList = index.columns.joinToString(prefix = "(", postfix = ")") { t.identity(it) }
        return if (index.unique) {
            "ALTER TABLE $quotedTableName ADD CONSTRAINT $quotedIndexName UNIQUE $columnsList"
        } else {
            "CREATE INDEX $quotedIndexName ON $quotedTableName $columnsList"
        }

    }

    override fun dropIndex(tableName: String, indexName: String): String {
        val identifierManager = TransactionManager.current().db.identifierManager
        return "ALTER TABLE ${identifierManager.quoteIfNecessary(tableName)} DROP CONSTRAINT ${identifierManager.quoteIfNecessary(indexName)}"
    }

    private val supportsSelectForUpdate by lazy { TransactionManager.current().db.metadata { supportsSelectForUpdate } }
    override fun supportsSelectForUpdate() = supportsSelectForUpdate

    override val supportsMultipleGeneratedKeys: Boolean = true

    override fun modifyColumn(column: Column<*>): String = "MODIFY COLUMN ${column.descriptionDdl()}"

}

val currentDialect: DatabaseDialect get() = TransactionManager.current().db.dialect

internal val currentDialectIfAvailable : DatabaseDialect? get() =
    if (TransactionManager.isInitialized() && TransactionManager.currentOrNull() != null) {
        currentDialect
    } else null

internal fun String.inProperCase(): String =
    TransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this@inProperCase) ?: this

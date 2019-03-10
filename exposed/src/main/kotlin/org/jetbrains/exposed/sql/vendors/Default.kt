package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.nio.ByteBuffer
import java.sql.ResultSet
import java.util.*

open class DataTypeProvider {
    open fun shortAutoincType() = "INT AUTO_INCREMENT"

    open fun shortType() = "INT"

    open fun longAutoincType() = "BIGINT AUTO_INCREMENT"

    open fun longType() = "BIGINT"

    open fun floatType() = "FLOAT"

    open fun doubleType() = "DOUBLE PRECISION"

    open fun uuidType() = "BINARY(16)"

    open fun dateTimeType() = "DATETIME"

    open fun blobType(): String = "BLOB"

    open fun binaryType(length: Int): String = "VARBINARY($length)"

    open fun booleanType(): String = "BOOLEAN"

    open fun booleanToStatementString(bool: Boolean) = bool.toString()

    open fun uuidToDB(value: UUID) : Any =
            ByteBuffer.allocate(16).putLong(value.mostSignificantBits).putLong(value.leastSignificantBits).array()

    open fun booleanFromStringToBoolean(value: String): Boolean = value.toBoolean()

    open fun textType() = "TEXT"
    open val blobAsStream = false

    open fun processForDefaultValue(e: Expression<*>) : String = when (e) {
        is LiteralOp<*> -> e.toSQL(QueryBuilder(false))
        else -> "(${e.toSQL(QueryBuilder(false))})"
    }
}

abstract class FunctionProvider {

    open val DEFAULT_VALUE_EXPRESSION = "DEFAULT VALUES"

    open fun<T:String?> substring(expr: Expression<T>, start: Expression<Int>, length: Expression<Int>, builder: QueryBuilder) : String =
            "SUBSTRING(${expr.toSQL(builder)}, ${start.toSQL(builder)}, ${length.toSQL(builder)})"

    open fun random(seed: Int?): String = "RANDOM(${seed?.toString().orEmpty()})"

    open fun cast(expr: Expression<*>, type: IColumnType, builder: QueryBuilder) = "CAST(${expr.toSQL(builder)} AS ${type.sqlType()})"

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
        return buildString {
            val builder = QueryBuilder(true)
            append("UPDATE ${targets.describe(transaction, builder)}")
            append(" SET ")
            append(columnsAndValues.joinToString { (col, value) ->
                "${transaction.identity(col)}=" + builder.registerArgument(col, value)
            })

            where?.let { append(" WHERE " + it.toSQL(builder)) }
            limit?.let { append(" LIMIT $it")}
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

    open fun queryLimit(size: Int, offset: Int, alreadyOrdered: Boolean) = "LIMIT $size" + if (offset > 0) " OFFSET $offset" else ""

    open fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) = buildString {
        append("GROUP_CONCAT(")
        if (expr.distinct)
            append("DISTINCT ")
        append(expr.expr.toSQL(queryBuilder))
        if (expr.orderBy.isNotEmpty()) {
            expr.orderBy.joinTo(this, prefix = " ORDER BY ") {
                "${it.first.toSQL(queryBuilder)} ${it.second.name}"
            }
        }
        expr.separator?.let {
            append(" SEPARATOR '$it'")
        }
        append(")")
    }

    interface MatchMode {
        fun mode() : String
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
    fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> = emptyMap()

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

    // --> REVIEW
    val supportsIfNotExists: Boolean get() = true
    val needsSequenceToAutoInc: Boolean get() = false
    val needsQuotesWhenSymbolsInNames: Boolean get() = true
    val identifierLengthLimit: Int get() = 100
    fun catalog(transaction: Transaction): String = transaction.connection.catalog
    // <-- REVIEW

    val defaultReferenceOption : ReferenceOption get() = ReferenceOption.RESTRICT

    // Specific SQL statements

    fun createIndex(index: Index): String
    fun dropIndex(tableName: String, indexName: String): String
    fun modifyColumn(column: Column<*>) : String
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

    private val isUpperCaseIdentifiers by lazy { TransactionManager.current().db.metadata.storesUpperCaseIdentifiers() }
    private val isLowerCaseIdentifiers by lazy { TransactionManager.current().db.metadata.storesLowerCaseIdentifiers() }
    val String.inProperCase: String get() = when {
        isUpperCaseIdentifiers -> toUpperCase()
        isLowerCaseIdentifiers -> toLowerCase()
        else -> this
    }

    /* Method always re-read data from DB. Using allTablesNames field is preferred way */
    override fun allTablesNames(): List<String> {
        val result = ArrayList<String>()
        val tr = TransactionManager.current()
        val resultSet = tr.db.metadata.getTables(getDatabase(), null, "%", arrayOf("TABLE"))

        while (resultSet.next()) {
            result.add(resultSet.getString("TABLE_NAME").inProperCase)
        }
        resultSet.close()
        return result
    }

    override fun getDatabase(): String = catalog(TransactionManager.current())

    override fun tableExists(table: Table) = allTablesNames.any { it == table.nameInDatabaseCase() }

    protected fun ResultSet.extractColumns(tables: Array<out Table>, extract: (ResultSet) -> Pair<String, ColumnMetadata>): Map<Table, List<ColumnMetadata>> {
        val mapping = tables.associateBy { it.nameInDatabaseCase() }
        val result = HashMap<Table, MutableList<ColumnMetadata>>()

        while (next()) {
            val (tableName, columnMetadata) = extract(this)
            mapping[tableName]?.let { t ->
                result.getOrPut(t) { arrayListOf() } += columnMetadata
            }
        }
        return result
    }

    override fun tableColumns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        val rs = TransactionManager.current().db.metadata.getColumns(getDatabase(), null, "%", "%")
        val result = rs.extractColumns(tables) {
            it.getString("TABLE_NAME") to ColumnMetadata(it.getString("COLUMN_NAME"), it.getInt("DATA_TYPE"), it.getBoolean("NULLABLE"))
        }
        rs.close()
        return result
    }

    private val columnConstraintsCache = HashMap<String, List<ForeignKeyConstraint>>()

    protected fun String.quoteIdentifierWhenWrongCaseOrNecessary(tr: Transaction)
            = if (tr.db.shouldQuoteIdentifiers && inProperCase != this) "${tr.db.identityQuoteString}$this${tr.db.identityQuoteString}" else tr.quoteIfNecessary(this)

    @Synchronized
    override fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> {
        val constraints = HashMap<Pair<String, String>, MutableList<ForeignKeyConstraint>>()
        val tr = TransactionManager.current()

        tables.map{ it.nameInDatabaseCase() }.forEach { table ->
            columnConstraintsCache.getOrPut(table) {
                val rs = tr.db.metadata.getImportedKeys(getDatabase(), null, table)
                val tableConstraint = arrayListOf<ForeignKeyConstraint> ()
                while (rs.next()) {
                    val fromTableName = rs.getString("FKTABLE_NAME")!!
                    val fromColumnName = rs.getString("FKCOLUMN_NAME")!!.quoteIdentifierWhenWrongCaseOrNecessary(tr)
                    val constraintName = rs.getString("FK_NAME")!!
                    val targetTableName = rs.getString("PKTABLE_NAME")!!
                    val targetColumnName = rs.getString("PKCOLUMN_NAME")!!.quoteIdentifierWhenWrongCaseOrNecessary(tr)
                    val constraintUpdateRule = ReferenceOption.resolveRefOptionFromJdbc(rs.getInt("UPDATE_RULE"))
                    val constraintDeleteRule = ReferenceOption.resolveRefOptionFromJdbc(rs.getInt("DELETE_RULE"))
                    tableConstraint.add(
                        ForeignKeyConstraint(constraintName,
                                targetTableName, targetColumnName,
                                fromTableName, fromColumnName,
                                constraintUpdateRule, constraintDeleteRule)
                    )
                }
                rs.close()
                tableConstraint
            }.forEach { it ->
                constraints.getOrPut(it.fromTable to it.fromColumn){arrayListOf()}.add(it)
            }

        }
        return constraints
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    @Synchronized
    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        for(table in tables) {
            val tableName = table.nameInDatabaseCase()
            val transaction = TransactionManager.current()
            val metadata = transaction.db.metadata

            existingIndicesCache.getOrPut(table) {
                val pkNames = metadata.getPrimaryKeys(getDatabase(), null, tableName).let { rs ->
                    val names = arrayListOf<String>()
                    while(rs.next()) {
                        rs.getString("PK_NAME")?.let { names += it }
                    }
                    rs.close()
                    names
                }
                val rs = metadata.getIndexInfo(getDatabase(), null, tableName, false, false)

                val tmpIndices = hashMapOf<Pair<String, Boolean>, MutableList<String>>()

                while (rs.next()) {
                    rs.getString("INDEX_NAME")?.let {
                        val column = transaction.quoteIfNecessary(rs.getString("COLUMN_NAME")!!)
                        val isUnique = !rs.getBoolean("NON_UNIQUE")
                        tmpIndices.getOrPut(it to isUnique) { arrayListOf() }.add(column)
                    }
                }
                rs.close()
                val tColumns = table.columns.associateBy { transaction.identity(it) }
                tmpIndices.filterNot { it.key.first in pkNames }
                        .mapNotNull { (index, columns) ->
                            columns.mapNotNull { cn -> tColumns[cn] }.takeIf { c -> c.size == columns.size }?.let { c -> Index(c, index.second, index.first) }
                        }
            }
        }
        return HashMap(existingIndicesCache)
    }

    @Synchronized
    override fun resetCaches() {
        _allTableNames = null
        columnConstraintsCache.clear()
        existingIndicesCache.clear()
    }

    override fun createIndex(index: Index): String {
        val t = TransactionManager.current()
        val quotedTableName = t.identity(index.table)
        val quotedIndexName = t.quoteIfNecessary(t.cutIfNecessary(index.indexName))
        val columnsList = index.columns.joinToString(prefix = "(", postfix = ")") { t.identity(it) }
        return if (index.unique) {
            "ALTER TABLE $quotedTableName ADD CONSTRAINT $quotedIndexName UNIQUE $columnsList"
        } else {
            "CREATE INDEX $quotedIndexName ON $quotedTableName $columnsList"
        }

    }

    override fun dropIndex(tableName: String, indexName: String): String {
        val t = TransactionManager.current()
        return "ALTER TABLE ${t.quoteIfNecessary(tableName)} DROP CONSTRAINT ${t.quoteIfNecessary(indexName)}"
    }

    private val supportsSelectForUpdate by lazy { TransactionManager.current().db.metadata.supportsSelectForUpdate() }
    override fun supportsSelectForUpdate() = supportsSelectForUpdate

    override val supportsMultipleGeneratedKeys: Boolean = true

    override fun modifyColumn(column: Column<*>): String = "MODIFY COLUMN ${column.descriptionDdl()}"

}

internal val currentDialect: DatabaseDialect get() = TransactionManager.current().db.dialect

internal val currentDialectIfAvailable : DatabaseDialect? get() =
    if (TransactionManager.isInitialized() && TransactionManager.currentOrNull() != null) {
        currentDialect
    } else null

internal fun String.inProperCase(): String = (currentDialectIfAvailable as? VendorDialect)?.run {
    this@inProperCase.inProperCase
} ?: this

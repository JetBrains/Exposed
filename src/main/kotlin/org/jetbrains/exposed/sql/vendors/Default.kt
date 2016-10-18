package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet
import java.util.*

open class DataTypeProvider() {
    open fun shortAutoincType() = "INT AUTO_INCREMENT"

    open fun longAutoincType() = "BIGINT AUTO_INCREMENT"

    open fun uuidType() = "BINARY(16)"

    open fun dateTimeType() = "DATETIME"

    open fun blobType(): String = "BLOB"

    open fun binaryType(length: Int): String = "VARBINARY($length)"

    open fun booleanToStatementString(bool: Boolean) = bool.toString()

    open val blobAsStream = false
}

open class FunctionProvider() {

    open val substring = "SUBSTRING"

    open fun<T:String?> ExpressionWithColumnType<T>.match(pattern: String, mode: MatchMode? = null): Op<Boolean> = with(SqlExpressionBuilder) { this@match.like(pattern) }

    interface MatchMode {
        fun mode() : String
    }
}

interface DatabaseDialect {
    val name: String
    val dataTypeProvider: DataTypeProvider
    val functionProvider: FunctionProvider

    fun getDatabase(): String

    fun allTablesNames(): List<String>
    /**
     * returns list of pairs (column name + nullable) for every table
     */
    fun tableColumns(vararg tables: Table): Map<Table, List<Pair<String, Boolean>>> = emptyMap()

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
    val supportsExpressionsAsDefault: Boolean get() = false

    // Specific SQL statements

    fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String
    fun delete(ignore: Boolean, table: Table, where: String?, transaction: Transaction): String
    fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String

    fun createIndex(unique: Boolean, tableName: String, indexName: String, columns: List<String>): String
    fun dropIndex(tableName: String, indexName: String): String

    fun limit(size: Int, offset: Int = 0): String
}

internal abstract class VendorDialect(override val name: String,
                                      override val dataTypeProvider: DataTypeProvider,
                                      override val functionProvider: FunctionProvider = FunctionProvider()) : DatabaseDialect {

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
        val resultSet = TransactionManager.current().db.metadata.getTables(null, null, null, arrayOf("TABLE"))

        while (resultSet.next()) {
            result.add(resultSet.getString("TABLE_NAME").inProperCase)
        }
        return result
    }

    override fun getDatabase(): String = TransactionManager.current().connection.catalog

    override fun tableExists(table: Table) = allTablesNames.any { it == table.nameInDatabaseCase() }

    protected fun ResultSet.extractColumns(tables: Array<out Table>, extract: (ResultSet) -> Triple<String, String, Boolean>): Map<Table, List<Pair<String, Boolean>>> {
        val mapping = tables.associateBy { it.nameInDatabaseCase() }
        val result = HashMap<Table, MutableList<Pair<String, Boolean>>>()

        while (next()) {
            val (tableName, columnName, nullable) = extract(this)
            mapping[tableName]?.let { t ->
                result.getOrPut(t) { arrayListOf() } += columnName to nullable
            }
        }
        return result
    }
    override fun tableColumns(vararg tables: Table): Map<Table, List<Pair<String, Boolean>>> {
        val rs = TransactionManager.current().db.metadata.getColumns(getDatabase(), null, null, null)
        return rs.extractColumns(tables) {
            Triple(it.getString("TABLE_NAME"), it.getString("COLUMN_NAME")!!, it.getBoolean("NULLABLE"))
        }
    }

    private val columnConstraintsCache = HashMap<String, List<ForeignKeyConstraint>>()

    override @Synchronized fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> {
        val constraints = HashMap<Pair<String, String>, MutableList<ForeignKeyConstraint>>()
        for (table in tables.map{ it.nameInDatabaseCase() }) {
            columnConstraintsCache.getOrPut(table, {
                val rs = TransactionManager.current().db.metadata.getExportedKeys(getDatabase(), null, table)
                val tableConstraint = arrayListOf<ForeignKeyConstraint> ()
                while (rs.next()) {
                    val refereeTableName = rs.getString("FKTABLE_NAME")!!
                    val refereeColumnName = rs.getString("FKCOLUMN_NAME")!!
                    val constraintName = rs.getString("FK_NAME")!!
                    val refTableName = rs.getString("PKTABLE_NAME")!!
                    val refColumnName = rs.getString("PKCOLUMN_NAME")!!
                    val constraintDeleteRule = ReferenceOption.resolveRefOptionFromJdbc(rs.getInt("DELETE_RULE"))
                    tableConstraint.add(ForeignKeyConstraint(constraintName, refereeTableName, refereeColumnName, refTableName, refColumnName, constraintDeleteRule))
                }
                tableConstraint
            }).forEach { it ->
                constraints.getOrPut(it.refereeTable to it.refereeColumn, {arrayListOf()}).add(it)
            }

        }
        return constraints
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    override @Synchronized fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        for(table in tables) {
            val tableName = table.nameInDatabaseCase()
            val metadata = TransactionManager.current().db.metadata

            existingIndicesCache.getOrPut(table, {
                val pkNames = metadata.getPrimaryKeys(getDatabase(), null, tableName).let { rs ->
                    val names = arrayListOf<String>()
                    while(rs.next()) {
                        rs.getString("PK_NAME")?.let { names += it }
                    }
                    names
                }
                val rs = metadata.getIndexInfo(getDatabase(), null, tableName, false, false)

                val tmpIndices = hashMapOf<Pair<String, Boolean>, MutableList<String>>()

                while (rs.next()) {
                    val indexName = rs.getString("INDEX_NAME")!!
                    val column = rs.getString("COLUMN_NAME")!!
                    val isUnique = !rs.getBoolean("NON_UNIQUE")
                    tmpIndices.getOrPut(indexName to isUnique, { arrayListOf() }).add(column)
                }
                tmpIndices.filterNot { it.key.first in pkNames }.map { Index(it.key.first, tableName, it.value, it.key.second)}
            })
        }
        return HashMap(existingIndicesCache)
    }

    override @Synchronized fun resetCaches() {
        _allTableNames = null
        columnConstraintsCache.clear()
        existingIndicesCache.clear()
    }

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {
        throw UnsupportedOperationException("There's no generic SQL for replace. There must be vendor specific implementation")
    }

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        if (ignore) {
            throw UnsupportedOperationException("There's no generic SQL for INSERT IGNORE. There must be vendor specific implementation")
        }

        return "INSERT INTO ${transaction.identity(table)} (${columns.joinToString { transaction.identity(it).inProperCase }}) $expr"
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, transaction: Transaction): String {
        if (ignore) {
            throw UnsupportedOperationException("There's no generic SQL for DELETE IGNORE. There must be vendor specific implementation")
        }

        return buildString {
            append("DELETE FROM ")
            append(transaction.identity(table))
            if (where != null) {
                append(" WHERE ")
                append(where)
            }
        }
    }

    override fun createIndex(unique: Boolean, tableName: String, indexName: String, columns: List<String>): String {
        val t = TransactionManager.current()
        return buildString {
            append("CREATE ")
            if (unique) append("UNIQUE ")
            append("INDEX ${t.quoteIfNecessary(indexName)} ON ${t.quoteIfNecessary(tableName)} ")
            columns.joinTo(this, ", ", "(", ")") {
                t.quoteIfNecessary(it)
            }
        }
    }

    override fun dropIndex(tableName: String, indexName: String): String {
        val t = TransactionManager.current()
        return "ALTER TABLE ${t.quoteIfNecessary(tableName)} DROP CONSTRAINT ${t.quoteIfNecessary(indexName)}"
    }

    private val supportsSelectForUpdate by lazy { TransactionManager.current().db.metadata.supportsSelectForUpdate() }
    override fun supportsSelectForUpdate() = supportsSelectForUpdate

    override val supportsMultipleGeneratedKeys: Boolean = true

    override fun limit(size: Int, offset: Int) = "LIMIT $size" + if (offset > 0) " OFFSET $offset" else ""

}

internal val currentDialect: DatabaseDialect get() = TransactionManager.current().db.dialect

internal fun String.inProperCase(): String = TransactionManager.currentOrNull()?.let { tm ->
    (currentDialect as? VendorDialect)?.run {
        this@inProperCase.inProperCase
    }
} ?: this

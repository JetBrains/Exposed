package org.jetbrains.exposed.sql.vendors

import java.util.*
import org.jetbrains.exposed.sql.*

interface DatabaseMetadataDialect {

    fun getDatabase(): String

    fun allTablesNames(): List<String>
    /**
     * returns list of pairs (column name + nullable) for every table
     */
    fun tableColumns(): Map<String, List<Pair<String, Boolean>>> = emptyMap()

    /**
     * returns map of constraint for a table name/column name pair
     */
    fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> = emptyMap()

    /**
     * return set of indices for each table
     */
    fun existingIndices(vararg tables: Table): Map<String, List<Index>> = emptyMap()

    fun tableExists(table: Table): Boolean

    fun resetCaches()
}

interface DialectSpecificFunctions {
    fun<T:String?> ExpressionWithColumnType<T>.match(pattern: String, mode: MatchMode? = null): Op<Boolean> = with(SqlExpressionBuilder) { this@match.like(pattern) }
}

interface MatchMode {
    fun mode() : String
}

internal abstract class VendorDialect : DatabaseMetadataDialect, DialectSpecificFunctions {
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
    override fun allTablesNames(): List<String> {
        val result = ArrayList<String>()
        val resultSet = Transaction.current().db.metadata.getTables(null, null, null, arrayOf("TABLE"))

        while (resultSet.next()) {
            result.add(resultSet.getString("TABLE_NAME"))
        }
        return result
    }

    override fun getDatabase() = Transaction.current().connection.schema

    override fun tableExists(table: Table) = allTablesNames.any { it.equals(table.tableName, true) }

    override fun tableColumns(): Map<String, List<Pair<String, Boolean>>> {
        val tables = HashMap<String, List<Pair<String, Boolean>>>()

        val rs = Transaction.current().db.metadata.getColumns(getDatabase(), null, null, null)

        while (rs.next()) {
            val tableName = rs.getString("TABLE_NAME")!!
            val columnName = rs.getString("COLUMN_NAME")!!
            val nullable = rs.getBoolean("NULLABLE")
            tables[tableName] = (tables[tableName]?.plus(listOf(columnName to nullable)) ?: listOf(columnName to nullable))
        }
        return tables
    }

    private val columnConstraintsCache = HashMap<String, List<ForeignKeyConstraint>>()

    override @Synchronized fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> {
        val constraints = HashMap<Pair<String, String>, MutableList<ForeignKeyConstraint>>()
        for (table in tables.map{it.tableName}) {
            columnConstraintsCache.getOrPut(table, {
                val rs = Transaction.current().db.metadata.getExportedKeys(getDatabase(), null, table)
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

    private val existingIndicesCache = HashMap<String, List<Index>>()

    override @Synchronized fun existingIndices(vararg tables: Table): Map<String, List<Index>> {
        for(table in tables.map {it.tableName}) {
            existingIndicesCache.getOrPut(table, {
                val rs = Transaction.current().db.metadata.getIndexInfo(getDatabase(), null, table, false, false)

                val tmpIndices = hashMapOf<Pair<String, Boolean>, MutableList<String>>()

                while (rs.next()) {
                    val indexName = rs.getString("INDEX_NAME")!!
                    val column = rs.getString("COLUMN_NAME")!!
                    val isUnique = !rs.getBoolean("NON_UNIQUE")
                    tmpIndices.getOrPut(indexName to isUnique, { arrayListOf() }).add(column)
                }
                tmpIndices.filterNot { it.key.first == "PRIMARY" }.map { Index(it.key.first, table, it.value, it.key.second)}
            }
            )
        }
        return HashMap(existingIndicesCache)
    }

    override @Synchronized fun resetCaches() {
        _allTableNames = null
        columnConstraintsCache.clear()
        existingIndicesCache.clear()
    }
}

private object DefaultVendorDialect : VendorDialect()

fun DatabaseVendor.dialect() : DatabaseMetadataDialect = when (this) {
    DatabaseVendor.MySql -> MysqlDialect
    DatabaseVendor.H2 -> H2Dialect
    else -> DefaultVendorDialect
}

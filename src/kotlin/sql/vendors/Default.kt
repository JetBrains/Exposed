package kotlin.sql.vendors

import org.h2.constraint.Constraint
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates
import kotlin.sql
import kotlin.sql.*

trait DatabaseMetadataDialect {

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

    fun tableExists(table: Table) = allTablesNames().any { it.equals(table.tableName, true) }

    fun resetCaches()
}

private abstract class VendorDialect : DatabaseMetadataDialect {

    override fun allTablesNames(): List<String> {
        val result = ArrayList<String>()
        val resultSet = Session.get().connection.getMetaData().getTables(null, null, null, arrayOf("TABLE"))

        while (resultSet.next()) {
            result.add(resultSet.getString("TABLE_NAME"))
        }
        return result
    }

    override fun getDatabase() = Session.get().connection.getSchema()

    override fun tableColumns(): Map<String, List<Pair<String, Boolean>>> {
        val tables = HashMap<String, List<Pair<String, Boolean>>>()

        val rs = Session.get().connection.getMetaData().getColumns(getDatabase(), null, null, null)

        while (rs.next()) {
            val tableName = rs.getString("TABLE_NAME")!!
            val columnName = rs.getString("COLUMN_NAME")!!
            val nullable = rs.getBoolean("NULLABLE")
            tables[tableName] = (tables[tableName]?.plus(listOf(columnName to nullable)) ?: listOf(columnName to nullable))
        }
        return tables
    }

    private val columnConstraintsCache = HashMap<String, List<ForeignKeyConstraint>>()

    override synchronized fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> {
        val constraints = HashMap<Pair<String, String>, MutableList<ForeignKeyConstraint>>()
        for (table in tables.map{it.tableName}) {
            columnConstraintsCache.getOrPut(table, {
                val rs = Session.get().connection.getMetaData().getExportedKeys(getDatabase(), null, table)
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

    private val existingIndicesCache = ConcurrentHashMap<String, List<Index>>()

    override synchronized fun existingIndices(vararg tables: Table): Map<String, List<Index>> {
        for(table in tables.map {it.tableName}) {
            existingIndicesCache.getOrPut(table, {
                val rs = Session.get().connection.getMetaData().getIndexInfo(getDatabase(), null, table, false, false)

                val tmpIndices = hashMapOf<Pair<String, Boolean>, MutableList<String>>()

                while (rs.next()) {
                    val indexName = rs.getString("INDEX_NAME")!!
                    val column = rs.getString("COLUMN_NAME")!!
                    val isUnique = !rs.getBoolean("NON_UNIQUE")
                    tmpIndices.getOrPut(indexName to isUnique, { arrayListOf() }).add(column)
                }
                tmpIndices.filterNot { it.getKey().first == "PRIMARY" }.map { Index(it.getKey().first, table, it.getValue(), it.getKey().second)}
            }
            )
        }
        return HashMap(existingIndicesCache)
    }

    override fun resetCaches() {
        columnConstraintsCache.clear()
        existingIndicesCache.clear()
    }
}

private object DefaultVendorDialect : VendorDialect()

public fun DatabaseVendor.dialect() : DatabaseMetadataDialect = when (this) {
    DatabaseVendor.MySql -> MysqlDialect
    else -> DefaultVendorDialect
}

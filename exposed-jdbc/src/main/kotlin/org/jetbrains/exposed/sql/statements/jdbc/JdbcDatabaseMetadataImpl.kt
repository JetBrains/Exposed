package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.ColumnMetadata
import java.math.BigDecimal
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.util.*

class JdbcDatabaseMetadataImpl(database: String, val metadata: DatabaseMetaData) : ExposedDatabaseMetadata(database) {
    override val url: String by lazyMetadata { url }
    override val defaultIsolationLevel: Int by lazyMetadata { defaultTransactionIsolation }

    private val databaseName = database.takeIf { metadata.databaseProductName !== "Oracle" }
    private val oracleSchema = database.takeIf { metadata.databaseProductName == "Oracle" }

    override val tableNames: List<String> get() = with(metadata) {
        return getTables(database, null, "%", arrayOf("TABLE")).iterate {
            identifierManager.inProperCase(getString("TABLE_NAME"))
        }
    }

    private fun ResultSet.extractColumns(tables: Array<out Table>, extract: (ResultSet) -> Pair<String, ColumnMetadata>): Map<Table, List<ColumnMetadata>> {
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

    override fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        val rs =  metadata.getColumns(databaseName, oracleSchema, "%", "%")
        val result = rs.extractColumns(tables) {
            it.getString("TABLE_NAME") to ColumnMetadata(it.getString("COLUMN_NAME")/*.quoteIdentifierWhenWrongCaseOrNecessary(tr)*/, it.getInt("DATA_TYPE"), it.getBoolean("NULLABLE"))
        }
        rs.close()
        return result
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        for(table in tables) {
            val tableName = table.nameInDatabaseCase()
            val transaction = TransactionManager.current()

            existingIndicesCache.getOrPut(table) {
                val pkNames = metadata.getPrimaryKeys(databaseName, oracleSchema, tableName).let { rs ->
                    val names = arrayListOf<String>()
                    while(rs.next()) {
                        rs.getString("PK_NAME")?.let { names += it }
                    }
                    rs.close()
                    names
                }
                val rs = metadata.getIndexInfo(database, null, tableName, false, false)

                val tmpIndices = hashMapOf<Pair<String, Boolean>, MutableList<String>>()

                while (rs.next()) {
                    rs.getString("INDEX_NAME")?.let {
                        val column = transaction.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(rs.getString("COLUMN_NAME")!!)
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

    private val columnConstraintsCache = HashMap<String, List<ForeignKeyConstraint>>()

    @Synchronized
    override fun columnConstraints(vararg tables: Table): Map<Pair<String, String>, List<ForeignKeyConstraint>> {
        val constraints = HashMap<Pair<String, String>, MutableList<ForeignKeyConstraint>>()

        tables.map{ it.nameInDatabaseCase() }.forEach { table ->
            columnConstraintsCache.getOrPut(table) {
                val rs = metadata.getImportedKeys(databaseName, oracleSchema, table)
                rs.iterate {
                    val fromTableName = rs.getString("FKTABLE_NAME")!!
                    val fromColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(rs.getString("FKCOLUMN_NAME")!!)
                    val constraintName = rs.getString("FK_NAME")!!
                    val targetTableName = rs.getString("PKTABLE_NAME")!!
                    val targetColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(rs.getString("PKCOLUMN_NAME")!!)
                    val constraintUpdateRule = ReferenceOption.resolveRefOptionFromJdbc(rs.getInt("UPDATE_RULE"))
                    val constraintDeleteRule = ReferenceOption.resolveRefOptionFromJdbc(rs.getInt("DELETE_RULE"))
                    ForeignKeyConstraint(constraintName,
                            targetTableName, targetColumnName,
                            fromTableName, fromColumnName,
                            constraintUpdateRule, constraintDeleteRule)
                }
            }.forEach {
                constraints.getOrPut(it.fromTable to it.fromColumn){arrayListOf()}.add(it)
            }

        }
        return constraints
    }

    @Synchronized
    override fun cleanCache() {
        columnConstraintsCache.clear()
        existingIndicesCache.clear()
    }

    override val version: BigDecimal by lazyMetadata { BigDecimal("$databaseMajorVersion.$databaseMinorVersion")}
    override val supportsAlterTableWithAddColumn by lazyMetadata { supportsAlterTableWithAddColumn() }
    override val supportsMultipleResultSets by lazyMetadata { supportsMultipleResultSets() }
    override val supportsSelectForUpdate: Boolean by lazyMetadata { supportsSelectForUpdate() }

    override val databaseProductVersion by lazyMetadata { databaseProductVersion!! }

    private fun <T> lazyMetadata(body: DatabaseMetaData.() -> T) = lazy { metadata.body() }

    override val identifierManager: IdentifierManagerApi by lazyMetadata { JdbcIdentifierManager(this) }
}

fun <T> ResultSet.iterate(body: ResultSet.() -> T) : List<T> {
    val result = arrayListOf<T>()
    while(next()) {
        result.add(body())
    }
    close()
    return result
}
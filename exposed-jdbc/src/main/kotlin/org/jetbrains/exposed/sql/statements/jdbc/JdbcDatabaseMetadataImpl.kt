package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.*
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
        return getTables(databaseName, oracleSchema, "%", arrayOf("TABLE")).iterate {
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
            //@see java.sql.DatabaseMetaData.getColumns
            val columnMetadata = ColumnMetadata(it.getString("COLUMN_NAME")/*.quoteIdentifierWhenWrongCaseOrNecessary(tr)*/, it.getInt("DATA_TYPE"), it.getBoolean("NULLABLE"), it.getInt("COLUMN_SIZE").takeIf { it != 0 })
            it.getString("TABLE_NAME") to columnMetadata
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
                val rs = metadata.getIndexInfo(databaseName, oracleSchema, tableName, false, false)

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

    @Synchronized
    override fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>> {
        val allTables = SchemaUtils.sortTablesByReferences(tables).associateBy { it.nameInDatabaseCase() }
        return allTables.keys.associateWith { table ->
            metadata.getImportedKeys(databaseName, oracleSchema, table).iterate {
                val fromTableName = getString("FKTABLE_NAME")!!
                val fromColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(getString("FKCOLUMN_NAME")!!)
                val fromColumn = allTables.getValue(fromTableName).columns.first { it.nameInDatabaseCase() == fromColumnName }
                val constraintName = getString("FK_NAME")!!
                val targetTableName = getString("PKTABLE_NAME")!!
                val targetColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(getString("PKCOLUMN_NAME")!!)
                val targetColumn = allTables.getValue(targetTableName).columns.first { it.nameInDatabaseCase() == targetColumnName }
                val constraintUpdateRule = ReferenceOption.resolveRefOptionFromJdbc(getInt("UPDATE_RULE"))
                val constraintDeleteRule = ReferenceOption.resolveRefOptionFromJdbc(getInt("DELETE_RULE"))
                ForeignKeyConstraint(
                        target = targetColumn,
                        from = fromColumn,
                        onUpdate = constraintUpdateRule,
                        onDelete = constraintDeleteRule,
                        name = constraintName
                )
            }
        }
    }

    @Synchronized
    override fun cleanCache() {
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
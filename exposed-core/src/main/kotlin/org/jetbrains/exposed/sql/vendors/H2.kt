package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.text.SimpleDateFormat
import java.util.Date

private val Transaction.isMySQLMode: Boolean
    get() = (db.dialect as? H2Dialect)?.isMySQLMode() ?: false

internal object H2DataTypeProvider : DataTypeProvider() {
    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override fun uuidType(): String = "UUID"
    override fun dateTimeType(): String = "DATETIME(9)"
}

internal object H2FunctionProvider : FunctionProvider() {

    private fun dbReleaseDate(transaction: Transaction): Date {
        val releaseDate = transaction.db.metadata { databaseProductVersion.substringAfterLast('(').substringBeforeLast(')') }
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        return formatter.parse(releaseDate)
    }

    override fun insert(
        ignore: Boolean,
        table: Table,
        columns: List<Column<*>>,
        expr: String,
        transaction: Transaction
    ): String {
        val uniqueIdxCols = table.indices.filter { it.unique }.flatMap { it.columns.toList() }
        val primaryKeys = table.primaryKey?.columns?.toList() ?: emptyList()
        val uniqueCols = (uniqueIdxCols  + primaryKeys).distinct()
        val borderDate = Date(118, 2, 18)
        return when {
            // INSERT IGNORE support added in H2 version 1.4.197 (2018-03-18)
            ignore && uniqueCols.isNotEmpty() && transaction.isMySQLMode && dbReleaseDate(transaction) < borderDate -> {
                val def = super.insert(false, table, columns, expr, transaction)
                def + " ON DUPLICATE KEY UPDATE " + uniqueCols.joinToString { "${transaction.identity(it)}=VALUES(${transaction.identity(it)})" }
            }
            ignore && uniqueCols.isNotEmpty() && transaction.isMySQLMode -> {
                super.insert(false, table, columns, expr, transaction).replace("INSERT", "INSERT IGNORE")
            }
            else -> super.insert(ignore, table, columns, expr, transaction)
        }
    }

    override fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = with(QueryBuilder(true)) {
        if (limit != null) {
            transaction.throwUnsupportedException("H2 doesn't support LIMIT in UPDATE with join clause.")
        }
        val tableToUpdate = columnsAndValues.map { it.first.table }.distinct().singleOrNull()
        if (tableToUpdate == null) {
            transaction.throwUnsupportedException("H2 supports a join updates with a single table columns to update.")
        }
        if (targets.joinParts.any { it.joinType != JoinType.INNER }) {
            exposedLogger.warn("All tables in UPDATE statement will be joined with inner join")
        }
        +"MERGE INTO "
        tableToUpdate.describe(transaction, this)
        +" USING "

        if (targets.table != tableToUpdate)
            targets.table.describe(transaction, this)

        targets.joinParts.forEach {
            if (it.joinPart != tableToUpdate) {
                it.joinPart.describe(transaction, this)
            }
            + " ON "
            it.appendConditions(this)
        }
        +" WHEN MATCHED THEN UPDATE SET "
        columnsAndValues.appendTo(this) { (col, value) ->
            append("${transaction.identity(col)}=")
            registerArgument(col, value)
        }

        where?.let {
            + " WHERE "
            +it
        }
        toString()
    }

    override fun replace(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        transaction: Transaction
    ): String {
        if (data.isEmpty()) {
            return ""
        }

        val columns = data.map { it.first }

        val builder = QueryBuilder(true)

        val sql = data.appendTo(builder, prefix = "VALUES (", postfix = ")") { (col, value) -> registerArgument(col, value) }.toString()

        return super.insert(false, table, columns, sql, transaction).replaceFirst("INSERT", "MERGE")
    }
}

/**
 * H2 dialect implementation.
 */
open class H2Dialect : VendorDialect(dialectName, H2DataTypeProvider, H2FunctionProvider) {

    private var isMySQLMode : Boolean? = null

    internal fun isMySQLMode() : Boolean {
        return isMySQLMode
            ?: TransactionManager.currentOrNull()?.let { tr ->
                tr.exec("SELECT VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE NAME = 'MODE'") { rs ->
                    rs.next()
                    rs.getString("VALUE")?.equals("MySQL", ignoreCase = true)?.also {
                        isMySQLMode = it
                    } ?: false
                }
            } ?: false
    }

    override val name: String
        get() = when (TransactionManager.currentOrNull()?.isMySQLMode) {
            true -> "$dialectName (Mysql Mode)"
            else -> dialectName
        }

    override val supportsMultipleGeneratedKeys: Boolean = false
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean get() = !TransactionManager.current().isMySQLMode

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> =
        super.existingIndices(*tables).mapValues { entry -> entry.value.filterNot { it.indexName.startsWith("PRIMARY_KEY_") } }.filterValues { it.isNotEmpty() }

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun createIndex(index: Index): String {
        if (index.columns.any { it.columnType is TextColumnType }) {
            exposedLogger.warn("Index on ${index.table.tableName} for ${index.columns.joinToString { it.name }} can't be created in H2")
            return ""
        }
        if (index.indexType != null) {
            exposedLogger.warn("Index of type ${index.indexType} on ${index.table.tableName} for ${index.columns.joinToString { it.name }} can't be created in H2")
            return ""
        }
        return super.createIndex(index)
    }

    override fun createDatabase(name: String) = "CREATE SCHEMA IF NOT EXISTS ${name.inProperCase()}"

    override fun modifyColumn(column: Column<*>): String =
        super.modifyColumn(column).replace("MODIFY COLUMN", "ALTER COLUMN")

    override fun dropDatabase(name: String) = "DROP SCHEMA IF EXISTS ${name.inProperCase()}"

    companion object {
        /** H2 dialect name */
        const val dialectName: String = "h2"
    }
}

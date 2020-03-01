package org.jetbrains.exposed.sql.vendors

import org.h2.engine.Mode
import org.h2.jdbc.JdbcConnection
import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Wrapper
import java.text.SimpleDateFormat
import java.util.Date

private val Transaction.isMySQLMode: Boolean
    get() {
        val h2Connection = (connection.connection as? JdbcConnection)
            ?: (connection.connection as? Wrapper)?.takeIf { it.isWrapperFor(JdbcConnection::class.java) }?.unwrap(JdbcConnection::class.java)

        return h2Connection?.let { !it.isClosed && it.settings.mode.enum == Mode.ModeEnum.MySQL } == true
    }

internal object H2DataTypeProvider : DataTypeProvider() {
    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override fun uuidType(): String = "UUID"
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
        val uniqueCols = columns.filter { it.indexInPK != null || it in uniqueIdxCols }
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

    override fun replace(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        transaction: Transaction
    ): String {
        if (!transaction.isMySQLMode) {
            transaction.throwUnsupportedException("REPLACE is only supported in MySQL compatibility mode for H2")
        }

        val builder = QueryBuilder(true)
        data.appendTo(builder) { registerArgument(it.first.columnType, it.second) }
        val values = builder.toString()

        val preparedValues = data.map { transaction.identity(it.first) to it.first.columnType.valueToString(it.second) }

        return "INSERT INTO ${transaction.identity(table)} (${preparedValues.joinToString { it.first }}) VALUES ($values) ON DUPLICATE KEY UPDATE ${preparedValues.joinToString { "${it.first}=${it.second}" }}"
    }
}

/**
 * H2 dialect implementation.
 */
open class H2Dialect : VendorDialect(dialectName, H2DataTypeProvider, H2FunctionProvider) {

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
        return super.createIndex(index)
    }

    override fun createDatabase(name: String) = "CREATE SCHEMA IF NOT EXISTS ${name.inProperCase()}"

    override fun dropDatabase(name: String) = "DROP SCHEMA IF EXISTS ${name.inProperCase()}"

    companion object {
        /** H2 dialect name */
        const val dialectName: String = "h2"
    }
}

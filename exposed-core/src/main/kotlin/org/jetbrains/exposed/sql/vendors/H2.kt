package org.jetbrains.exposed.sql.vendors

import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

private val Transaction.isMySQLMode: Boolean
    get() = (db.dialect as? H2Dialect)?.isMySQLMode ?: false

internal object H2DataTypeProvider : DataTypeProvider() {
    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override fun uuidType(): String = "UUID"
    override fun dateTimeType(): String = "DATETIME(9)"
}

internal object H2FunctionProvider : FunctionProvider() {
    override fun nextVal(seq: Sequence, builder: QueryBuilder) =
        when ((TransactionManager.current().db.dialect as H2Dialect).majorVersion) {
            H2Dialect.H2MajorVersion.One -> super.nextVal(seq, builder)
            H2Dialect.H2MajorVersion.Two -> builder {
                append("NEXT VALUE FOR ${seq.identifier}")
            }
        }

    override fun insert(
        ignore: Boolean,
        table: Table,
        columns: List<Column<*>>,
        expr: String,
        transaction: Transaction
    ): String {
        val uniqueCols = mutableSetOf<Column<*>>()
        table.indices.filter { it.unique }.flatMapTo(uniqueCols) { it.columns }
        table.primaryKey?.columns?.let { primaryKeys ->
            uniqueCols += primaryKeys
        }
        val version = (transaction.db.dialect as H2Dialect).version
        return when {
            // INSERT IGNORE support added in H2 version 1.4.197 (2018-03-18)
            ignore && uniqueCols.isNotEmpty() && transaction.isMySQLMode && version < "1.4.197" -> {
                val def = super.insert(false, table, columns, expr, transaction)
                def + " ON DUPLICATE KEY UPDATE " + uniqueCols.joinToString { "${transaction.identity(it)}=VALUES(${transaction.identity(it)})" }
            }
            ignore && uniqueCols.isNotEmpty() && transaction.isMySQLMode -> {
                super.insert(false, table, columns, expr, transaction).replace("INSERT", "INSERT IGNORE")
            }
            ignore -> transaction.throwUnsupportedException("INSERT IGNORE supported only on H2 v1.4.197+ with MODE=MYSQL.")
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
            ?: transaction.throwUnsupportedException("H2 supports a join updates with a single table columns to update.")
        if (targets.joinParts.any { it.joinType != JoinType.INNER }) {
            exposedLogger.warn("All tables in UPDATE statement will be joined with inner join")
        }
        +"MERGE INTO "
        tableToUpdate.describe(transaction, this)
        +" USING "

        if (targets.table != tableToUpdate) {
            targets.table.describe(transaction, this)
        }

        targets.joinParts.forEach {
            if (it.joinPart != tableToUpdate) {
                it.joinPart.describe(transaction, this)
            }
            +" ON "
            it.appendConditions(this)
        }
        +" WHEN MATCHED THEN UPDATE SET "
        columnsAndValues.appendTo(this) { (col, value) ->
            append("${transaction.fullIdentity(col)}=")
            registerArgument(col, value)
        }

        where?.let {
            +" WHERE "
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
    internal enum class H2MajorVersion {
        One, Two
    }

    internal val version by lazy {
        exactH2Version(TransactionManager.current())
    }

    internal val majorVersion: H2MajorVersion by lazy {
        when {
            version.startsWith("1.") -> H2MajorVersion.One
            version.startsWith("2.") -> H2MajorVersion.Two
            else -> error("Unsupported H2 version: $version")
        }
    }

    val isSecondVersion get() = majorVersion == H2MajorVersion.Two

    private fun exactH2Version(transaction: Transaction): String = transaction.db.metadata { databaseProductVersion.substringBefore(" (") }

    internal val isMySQLMode: Boolean by lazy {
        val (settingNameField, settingValueField) = when (majorVersion) {
            H2MajorVersion.One -> "NAME" to "VALUE"
            H2MajorVersion.Two -> "SETTING_NAME" to "SETTING_VALUE"
        }
        @Language("H2")
        val mySQLModeQuery = "SELECT $settingValueField FROM INFORMATION_SCHEMA.SETTINGS WHERE $settingNameField = 'MODE'"
        val mySQLMode = TransactionManager.current().exec(mySQLModeQuery) { rs ->
            rs.next()
            rs.getString(settingValueField)
        }
        mySQLMode.equals("MySQL", ignoreCase = true)
    }

    override val name: String
        get() = when (TransactionManager.currentOrNull()?.isMySQLMode) {
            true -> "$dialectName (Mysql Mode)"
            else -> dialectName
        }

    override val supportsMultipleGeneratedKeys: Boolean = false
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean get() = !TransactionManager.current().isMySQLMode

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> =
        super.existingIndices(*tables).mapValues { entry -> entry.value.filterNot { it.indexName.startsWith("PRIMARY_KEY_") } }
            .filterValues { it.isNotEmpty() }

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun createIndex(index: Index): String {
        if (index.columns.any { it.columnType is TextColumnType }) {
            exposedLogger.warn("Index on ${index.table.tableName} for ${index.columns.joinToString { it.name }} can't be created in H2")
            return ""
        }
        if (index.indexType != null) {
            exposedLogger.warn(
                "Index of type ${index.indexType} on ${index.table.tableName} for ${index.columns.joinToString { it.name }} can't be created in H2"
            )
            return ""
        }
        return super.createIndex(index)
    }

    override fun createDatabase(name: String) = "CREATE SCHEMA IF NOT EXISTS ${name.inProperCase()}"

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> =
        super.modifyColumn(column, columnDiff).map { it.replace("MODIFY COLUMN", "ALTER COLUMN") }

    override fun dropDatabase(name: String) = "DROP SCHEMA IF EXISTS ${name.inProperCase()}"

    companion object {
        /** H2 dialect name */
        const val dialectName: String = "h2"
    }
}

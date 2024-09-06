package org.jetbrains.exposed.sql.vendors

import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.DatabaseApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.DatabaseMetaData
import java.util.*

internal object H2DataTypeProvider : DataTypeProvider() {
    override fun binaryType(): String = "VARBINARY"

    override fun uuidType(): String = "UUID"
    override fun uuidToDB(value: UUID): Any = value.toString()
    override fun dateTimeType(): String = "DATETIME(9)"

    override fun timestampWithTimeZoneType(): String = "TIMESTAMP(9) WITH TIME ZONE"

    override fun jsonBType(): String = "JSON"

    override fun hexToDb(hexString: String): String = "X'$hexString'"
}

internal object H2FunctionProvider : FunctionProvider() {
    private val DatabaseDialect.isH2Oracle: Boolean
        get() = h2Mode == H2Dialect.H2CompatibilityMode.Oracle

    override fun nextVal(seq: Sequence, builder: QueryBuilder) =
        when ((TransactionManager.current().db.dialect as H2Dialect).majorVersion) {
            H2Dialect.H2MajorVersion.One -> super.nextVal(seq, builder)
            H2Dialect.H2MajorVersion.Two -> builder {
                append("NEXT VALUE FOR ${seq.identifier}")
            }
        }

    override fun <T> arraySlice(expression: Expression<T>, lower: Int?, upper: Int?, queryBuilder: QueryBuilder) {
        queryBuilder {
            append("ARRAY_SLICE(", expression, ",$lower,$upper)")
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
        val h2Dialect = transaction.db.dialect as H2Dialect
        val version = h2Dialect.version
        val isMySQLMode = h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.MySQL
        return when {
            // INSERT IGNORE support added in H2 version 1.4.197 (2018-03-18)
            ignore && uniqueCols.isNotEmpty() && isMySQLMode && version < "1.4.197" -> {
                val def = super.insert(false, table, columns, expr, transaction)
                def + " ON DUPLICATE KEY UPDATE " + uniqueCols.joinToString { "${transaction.identity(it)}=VALUES(${transaction.identity(it)})" }
            }
            ignore && uniqueCols.isNotEmpty() && isMySQLMode -> {
                super.insert(false, table, columns, expr, transaction).replace("INSERT", "INSERT IGNORE")
            }
            ignore -> transaction.throwUnsupportedException("INSERT IGNORE supported only on H2 v1.4.197+ with MODE=MYSQL.")
            else -> super.insert(false, table, columns, expr, transaction)
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
        if (where != null && !transaction.db.dialect.isH2Oracle) {
            transaction.throwUnsupportedException("H2 doesn't support WHERE in UPDATE with join clause.")
        }
        val tableToUpdate = columnsAndValues.map { it.first.table }.distinct().singleOrNull()
            ?: transaction.throwUnsupportedException(
                "H2 doesn't support UPDATE with join clause that uses columns from multiple tables."
            )
        val joinPart = targets.joinParts.singleOrNull()
            ?: transaction.throwUnsupportedException(
                "H2 doesn't support UPDATE with join clause that uses multiple tables to join."
            )
        targets.checkJoinTypes(StatementType.UPDATE)

        appendMergeIntoUsingJoinClause(tableToUpdate, targets, joinPart, transaction)
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

    private fun QueryBuilder.appendMergeIntoUsingJoinClause(
        target: Table,
        join: Join,
        joinPart: Join.JoinPart,
        transaction: Transaction
    ) {
        +"MERGE INTO "
        target.describe(transaction, this)
        +" USING "
        (joinPart.joinPart.takeIf { it != target } ?: join.table).describe(transaction, this)
        +" ON "
        joinPart.appendConditions(this)
    }

    override fun delete(
        ignore: Boolean,
        targets: Join,
        targetTables: List<Table>,
        where: Op<Boolean>?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (ignore) {
            transaction.throwUnsupportedException("H2 doesn't support IGNORE in DELETE from join relation")
        }
        if (limit != null) {
            transaction.throwUnsupportedException("H2 doesn't support LIMIT in DELETE from join relation")
        }
        val tableToDelete = targetTables.singleOrNull()
            ?: transaction.throwUnsupportedException(
                "H2 doesn't support DELETE from join relation with multiple tables to delete from"
            )
        val joinPart = targets.joinParts.singleOrNull()
            ?: transaction.throwUnsupportedException(
                "H2 doesn't support DELETE from join relation that uses multiple tables to join"
            )
        targets.checkJoinTypes(StatementType.DELETE)

        return with(QueryBuilder(true)) {
            appendMergeIntoUsingJoinClause(tableToDelete, targets, joinPart, transaction)
            +" WHEN MATCHED"
            where?.let {
                +" AND "
                +it
            }
            +" THEN DELETE"
            toString()
        }
    }

    /**
     * Implementation of [FunctionProvider.locate]
     * Note: search is case-sensitive
     * */
    override fun <T : String?> locate(
        queryBuilder: QueryBuilder,
        expr: Expression<T>,
        substring: String
    ) = queryBuilder {
        append("LOCATE(\'", substring, "\',", expr, ")")
    }

    override fun explain(
        analyze: Boolean,
        options: String?,
        internalStatement: String,
        transaction: Transaction
    ): String {
        if (options != null) {
            transaction.throwUnsupportedException("H2 does not support options other than ANALYZE in EXPLAIN queries.")
        }
        return super.explain(analyze, null, internalStatement, transaction)
    }

    override fun <T> date(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("CAST(", expr, " AS DATE)")
    }

    override fun <T> time(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("FORMATDATETIME(", expr, ", 'HH:mm:ss.SSSSSSSSS')")
    }
}

/**
 * H2 dialect implementation.
 */
open class H2Dialect : VendorDialect(dialectName, H2DataTypeProvider, H2FunctionProvider) {

    override fun toString(): String = "H2Dialect[$dialectName, $h2Mode]"

    enum class H2MajorVersion {
        One, Two
    }

    internal val version by lazy {
        exactH2Version(TransactionManager.current())
    }

    val majorVersion: H2MajorVersion by lazy {
        when {
            version.startsWith("1.") -> H2MajorVersion.One
            version.startsWith("2.") -> H2MajorVersion.Two
            else -> error("Unsupported H2 version: $version")
        }
    }

    /** Indicates whether the H2 Database Engine version is greater than or equal to 2.0. */
    val isSecondVersion get() = majorVersion == H2MajorVersion.Two

    private fun exactH2Version(transaction: Transaction): String = transaction.db.metadata { databaseProductVersion.substringBefore(" (") }

    /** H2 database compatibility modes that emulate the behavior of other specific databases. */
    enum class H2CompatibilityMode {
        MySQL, MariaDB, SQLServer, Oracle, PostgreSQL
    }

    /** The specific database name that an H2 compatibility mode delegates to. */
    val delegatedDialectNameProvider: DialectNameProvider? by lazy {
        when (h2Mode) {
            H2CompatibilityMode.MySQL -> MysqlDialect
            H2CompatibilityMode.MariaDB -> MariaDBDialect
            H2CompatibilityMode.PostgreSQL -> PostgreSQLDialect
            H2CompatibilityMode.Oracle -> OracleDialect
            H2CompatibilityMode.SQLServer -> SQLServerDialect
            else -> null
        }
    }

    private var delegatedDialect: DatabaseDialect? = null

    private fun resolveDelegatedDialect(): DatabaseDialect? {
        return delegatedDialect ?: delegatedDialectNameProvider?.dialectName?.lowercase()?.let {
            val dialect = DatabaseApi.dialects[it]?.invoke() ?: error("Can't resolve dialect for $it")
            delegatedDialect = dialect
            dialect
        }
    }

    /** The regular H2 mode implementation of [FunctionProvider] instead of a delegated mode implementation. */
    val originalFunctionProvider: FunctionProvider = H2FunctionProvider

    override val functionProvider: FunctionProvider by lazy {
        resolveDelegatedDialect()?.takeIf { it !is MysqlDialect }?.functionProvider ?: originalFunctionProvider
    }

    /** The regular H2 mode implementation of [DataTypeProvider] instead of a delegated mode implementation. */
    val originalDataTypeProvider: DataTypeProvider = H2DataTypeProvider

    override val dataTypeProvider: DataTypeProvider by lazy {
        resolveDelegatedDialect()?.takeIf { it !is MysqlDialect }?.dataTypeProvider ?: originalDataTypeProvider
    }

    /** The H2 database compatibility mode retrieved from metadata. */
    val h2Mode: H2CompatibilityMode? by lazy {
        val (settingNameField, settingValueField) = when (majorVersion) {
            H2MajorVersion.One -> "NAME" to "VALUE"
            H2MajorVersion.Two -> "SETTING_NAME" to "SETTING_VALUE"
        }

        @Language("H2")
        val fetchModeQuery = "SELECT $settingValueField FROM INFORMATION_SCHEMA.SETTINGS WHERE $settingNameField = 'MODE'"
        val modeValue = TransactionManager.current().exec(fetchModeQuery) { rs ->
            rs.next()
            rs.getString(settingValueField)
        }
        when {
            modeValue == null -> null
            modeValue.equals("MySQL", ignoreCase = true) -> H2CompatibilityMode.MySQL
            modeValue.equals("MariaDB", ignoreCase = true) -> H2CompatibilityMode.MariaDB
            modeValue.equals("MSSQLServer", ignoreCase = true) -> H2CompatibilityMode.SQLServer
            modeValue.equals("Oracle", ignoreCase = true) -> H2CompatibilityMode.Oracle
            modeValue.equals("PostgreSQL", ignoreCase = true) -> H2CompatibilityMode.PostgreSQL
            else -> null
        }
    }

    override val name: String by lazy {
        when (h2Mode) {
            null -> dialectName
            else -> "$dialectName (${h2Mode!!.name} Mode)"
        }
    }

    override val supportsMultipleGeneratedKeys: Boolean by lazy { resolveDelegatedDialect()?.supportsMultipleGeneratedKeys ?: false }
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean by lazy { resolveDelegatedDialect()?.supportsOnlyIdentifiersInGeneratedKeys ?: true }
    override val supportsIfNotExists: Boolean by lazy { resolveDelegatedDialect()?.supportsIfNotExists ?: super.supportsIfNotExists }
    override val supportsCreateSequence: Boolean by lazy { resolveDelegatedDialect()?.supportsCreateSequence ?: super.supportsCreateSequence }
    override val needsSequenceToAutoInc: Boolean by lazy { resolveDelegatedDialect()?.needsSequenceToAutoInc ?: super.needsSequenceToAutoInc }
    override val defaultReferenceOption: ReferenceOption by lazy { resolveDelegatedDialect()?.defaultReferenceOption ?: super.defaultReferenceOption }
    override val supportsSequenceAsGeneratedKeys: Boolean by lazy {
        resolveDelegatedDialect()?.supportsSequenceAsGeneratedKeys ?: super.supportsSequenceAsGeneratedKeys
    }
    override val supportsTernaryAffectedRowValues: Boolean by lazy {
        resolveDelegatedDialect()?.supportsTernaryAffectedRowValues ?: super.supportsTernaryAffectedRowValues
    }
    override val supportsCreateSchema: Boolean by lazy { resolveDelegatedDialect()?.supportsCreateSchema ?: super.supportsCreateSchema }
    override val supportsSubqueryUnions: Boolean by lazy { resolveDelegatedDialect()?.supportsSubqueryUnions ?: super.supportsSubqueryUnions }
    override val supportsDualTableConcept: Boolean by lazy { resolveDelegatedDialect()?.supportsDualTableConcept ?: super.supportsDualTableConcept }
    override val supportsOrderByNullsFirstLast: Boolean by lazy { resolveDelegatedDialect()?.supportsOrderByNullsFirstLast ?: super.supportsOrderByNullsFirstLast }
    override val supportsWindowFrameGroupsMode: Boolean by lazy { resolveDelegatedDialect()?.supportsWindowFrameGroupsMode ?: super.supportsWindowFrameGroupsMode }
//    override val likePatternSpecialChars: Map<Char, Char?> by lazy { resolveDelegatedDialect()?.likePatternSpecialChars ?: super.likePatternSpecialChars }

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> =
        super.existingIndices(*tables).mapValues { entry -> entry.value.filterNot { it.indexName.startsWith("PRIMARY_KEY_") } }
            .filterValues { it.isNotEmpty() }

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun createIndex(index: Index): String {
        if (
            (majorVersion == H2MajorVersion.One || h2Mode == H2CompatibilityMode.Oracle) &&
            index.columns.any { it.columnType is TextColumnType }
        ) {
            exposedLogger.warn("Index on ${index.table.tableName} for ${index.columns.joinToString { it.name }} can't be created on CLOB in H2")
            return ""
        }
        if (index.indexType != null) {
            exposedLogger.warn(
                "Index of type ${index.indexType} on ${index.table.tableName} for ${index.columns.joinToString { it.name }} can't be created in H2"
            )
            return ""
        }
        if (index.functions != null) {
            exposedLogger.warn(
                "Functional index on ${index.table.tableName} using ${index.functions.joinToString { it.toString() }} can't be created in H2"
            )
            return ""
        }
        return super.createIndex(index)
    }

    override fun createDatabase(name: String) = "CREATE SCHEMA IF NOT EXISTS ${name.inProperCase()}"

    override fun listDatabases(): String = "SHOW SCHEMAS"

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> =
        super.modifyColumn(column, columnDiff).map { it.replace("MODIFY COLUMN", "ALTER COLUMN") }

    override fun dropDatabase(name: String) = "DROP SCHEMA IF EXISTS ${name.inProperCase()}"

    override fun resolveRefOptionFromJdbc(refOption: Int): ReferenceOption {
        val modeDelegatesRefOption = h2Mode == H2CompatibilityMode.Oracle || h2Mode == H2CompatibilityMode.SQLServer
        return when {
            refOption == DatabaseMetaData.importedKeyRestrict && modeDelegatesRefOption -> ReferenceOption.NO_ACTION
            refOption == DatabaseMetaData.importedKeyRestrict -> ReferenceOption.RESTRICT
            else -> super.resolveRefOptionFromJdbc(refOption)
        }
    }

    override fun sequences(): List<String> {
        val sequences = mutableListOf<String>()
        TransactionManager.current().exec("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES") { rs ->
            while (rs.next()) {
                val result = rs.getString("SEQUENCE_NAME")
                val sequenceName = if (h2Mode == H2CompatibilityMode.Oracle) {
                    val q = if (result.contains('.') && !result.isAlreadyQuoted()) "\"" else ""
                    "$q$result$q"
                } else {
                    result
                }
                sequences.add(sequenceName)
            }
        }
        return sequences
    }

    companion object : DialectNameProvider("H2")
}

/** The current H2 database compatibility mode or `null` if the current database is not H2. */
val DatabaseDialect.h2Mode: H2Dialect.H2CompatibilityMode? get() = (this as? H2Dialect)?.h2Mode

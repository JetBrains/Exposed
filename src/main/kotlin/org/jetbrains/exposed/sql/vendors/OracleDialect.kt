package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal object OracleDataTypeProvider : DataTypeProvider() {

    override fun shortAutoincType() = "NUMBER(12)"

    override fun shortType() = "NUMBER(12)"

    override fun longAutoincType() = "NUMBER(19)"

    override fun longType() = "NUMBER(19)"

    override fun dateTimeType() = "TIMESTAMP"

    override fun uuidType() = "RAW(16)"

    override fun textType() = "LONG"

    override fun blobType() = "BLOB"

    override fun binaryType(length: Int): String = "BLOB"

    override fun booleanType() = "CHAR(1)"

    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"

    override fun booleanFromStringToBoolean(value: String): Boolean = try {
        value.toLong() != 0L
    } catch (ex: NumberFormatException) {
        error("Unexpected value of type Boolean: $value")
    }

    override val blobAsStream = true
}

internal object OracleFunctionProvider : FunctionProvider() {

    override val substring = "SUBSTR"
}

internal object OracleDialect : VendorDialect("oracle", OracleDataTypeProvider, OracleFunctionProvider) {

    override val supportsMultipleGeneratedKeys = false
    override val supportsIfNotExists = false
    override val needsSequenceToAutoInc = true
    override val needsQuotesWhenSymbolsInNames = false
    override val identifierLengthLimit = 30

    override fun catalog(transaction: Transaction) : String = transaction.connection.metaData.userName

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        val autoInc = table.columns.find { it.columnType.autoinc }

        autoInc?.let {
            val autoincSeq = autoInc.columnType.autoincSeq ?:
                    throw UnsupportedOperationException("You must provide auto-increment sequence name as argument to autoIncrement()")
            val nextValExpr = expr.replace("VALUES (", "VALUES ($autoincSeq.NEXTVAL, ")
            return "INSERT INTO ${transaction.identity(table)} (${autoInc.name}, ${columns.map { transaction.identity(it) }.joinToString()}) $nextValExpr"
        } ?: return super.insert(ignore, table, columns, expr, transaction)
    }

    override fun limit(size: Int, offset: Int) = if (offset > 0) " OFFSET $offset" else "" + " FETCH FIRST $size ROWS ONLY"

    override fun tableColumns(vararg tables: Table): Map<Table, List<Pair<String, Boolean>>> {

        val rs = TransactionManager.current().connection.createStatement().executeQuery(
                "SELECT DISTINCT TABLE_NAME, COLUMN_NAME, NULLABLE FROM DBA_TAB_COLS WHERE OWNER = '${OracleDialect.getDatabase()}'")
        return rs.extractColumns(tables) {
            Triple(it.getString("TABLE_NAME")!!, it.getString("COLUMN_NAME")!!, it.getBoolean("NULLABLE"))
        }
    }
}
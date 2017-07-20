package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

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

    override fun substring(expr: Expression<String?>, start: ExpressionWithColumnType<Int>, length: ExpressionWithColumnType<Int>, builder: QueryBuilder): String =
            super.substring(expr, start, length, builder).replace("SUBSTRING", "SUBSTR")

    /* seed is ignored. You have to use dbms_random.seed function manually */
    override fun random(seed: Int?): String = "dbms_random.value"
}

internal object OracleDialect : VendorDialect("oracle", OracleDataTypeProvider, OracleFunctionProvider) {

    override val supportsMultipleGeneratedKeys = false
    override val supportsIfNotExists = false
    override val needsSequenceToAutoInc = true
    override val needsQuotesWhenSymbolsInNames = false
    override val identifierLengthLimit = 30

    override val defaultReferenceOption: ReferenceOption get() = ReferenceOption.NO_ACTION

    override fun catalog(transaction: Transaction) : String = transaction.connection.metaData.userName

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        return table.autoIncColumn?.let {
            val newExpr = if (expr.isBlank()) {
                "VALUES (${it.autoIncSeqName!!}.NEXTVAL)"
            } else {
                expr.replace("VALUES (", "VALUES (${it.autoIncSeqName!!}.NEXTVAL, ")
            }

            super.insert(ignore, table, listOf(it) + columns, newExpr, transaction)
        } ?: super.insert(ignore, table, columns, expr, transaction)
    }

    override fun limit(size: Int, offset: Int) = if (offset > 0) " OFFSET $offset" else " FETCH FIRST $size ROWS ONLY"

    override fun tableColumns(vararg tables: Table): Map<Table, List<Pair<String, Boolean>>> {

        val rs = TransactionManager.current().connection.createStatement().executeQuery(
                "SELECT DISTINCT TABLE_NAME, COLUMN_NAME, NULLABLE FROM DBA_TAB_COLS WHERE OWNER = '${getDatabase()}'")
        return rs.extractColumns(tables) {
            Triple(it.getString("TABLE_NAME")!!, it.getString("COLUMN_NAME")!!, it.getBoolean("NULLABLE"))
        }
    }

    override fun allTablesNames(): List<String> {
        val result = ArrayList<String>()
        val tr = TransactionManager.current()
        val resultSet = tr.db.metadata.getTables(null, getDatabase(), null, arrayOf("TABLE"))

        while (resultSet.next()) {
            result.add(resultSet.getString("TABLE_NAME").inProperCase)
        }
        return result
    }
}
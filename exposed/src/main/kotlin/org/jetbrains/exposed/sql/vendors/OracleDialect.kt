package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
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

    override fun textType() = "CLOB"

    override fun blobType() = "BLOB"

    override fun binaryType(length: Int): String = "BLOB"

    override fun booleanType() = "CHAR(1)"

    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"

    override fun booleanFromStringToBoolean(value: String): Boolean = try {
        value.toLong() != 0L
    } catch (ex: NumberFormatException) {
        error("Unexpected value of type Boolean: $value")
    }

    override fun processForDefaultValue(e: Expression<*>): String = when {
        e is LiteralOp<*> && e.columnType is DateColumnType -> "DATE ${super.processForDefaultValue(e)}"
        else -> super.processForDefaultValue(e)
    }

    override val blobAsStream = true
}

internal object OracleFunctionProvider : FunctionProvider() {

    override fun<T:String?> substring(expr: Expression<T>, start: Expression<Int>, length: Expression<Int>, builder: QueryBuilder): String =
            super.substring(expr, start, length, builder).replace("SUBSTRING", "SUBSTR")

    override fun update(targets: ColumnSet, columnsAndValues: List<Pair<Column<*>, Any?>>, limit: Int?, where: Op<Boolean>?, transaction: Transaction): String {
        val def =  super.update(targets, columnsAndValues, null, where, transaction)
        return when {
            limit != null && where != null -> "$def AND ROWNUM <= $limit"
            limit != null -> "$def WHERE ROWNUM <= $limit"
            else -> def
        }
    }

    /* seed is ignored. You have to use dbms_random.seed function manually */
    override fun random(seed: Int?): String = "dbms_random.value"

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        return table.autoIncColumn?.takeIf { it !in columns }?.let {
            val newExpr = if (expr.isBlank()) {
                "VALUES (${it.autoIncSeqName!!}.NEXTVAL)"
            } else {
                expr.replace("VALUES (", "VALUES (${it.autoIncSeqName!!}.NEXTVAL, ")
            }

            super.insert(ignore, table, listOf(it) + columns, newExpr, transaction)
        } ?: super.insert(ignore, table, columns, expr, transaction)
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        if (limit != null) transaction.throwUnsupportedException("LIMIT is not supported in DELETE in Oracle")
        return super.delete(ignore, table, where, limit, transaction)
    }

    override fun queryLimit(size: Int, offset: Int, alreadyOrdered: Boolean)
        = (if (offset > 0) " OFFSET $offset ROWS" else "") + " FETCH FIRST $size ROWS ONLY"

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder): String = buildString {
        if (expr.orderBy.size != 1)
            TransactionManager.current().throwUnsupportedException("LISTAGG requires single order by clause")
        append("LISTAGG(")
        append(expr.expr.toSQL(queryBuilder))
        expr.separator?.let {
            append(", '$it'")
        }
        append(") WITHIN GROUP (ORDER BY ")
        val (col, order) = expr.orderBy.single()
        append("${col.toSQL(queryBuilder)} ${order.name}")
        append(")")
    }
}

open class OracleDialect : VendorDialect(dialectName, OracleDataTypeProvider, OracleFunctionProvider) {

    override val supportsMultipleGeneratedKeys = false
    override val supportsIfNotExists = false
    override val needsSequenceToAutoInc = true
    override val needsQuotesWhenSymbolsInNames = false
    override val identifierLengthLimit = 30

    override val defaultReferenceOption: ReferenceOption get() = ReferenceOption.NO_ACTION

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun catalog(transaction: Transaction) : String = transaction.connection.metaData.userName

    override fun allTablesNames(): List<String> {
        val result = ArrayList<String>()
        val tr = TransactionManager.current()
        val resultSet = tr.db.metadata.getTables(null, getDatabase(), null, arrayOf("TABLE"))

        while (resultSet.next()) {
            result.add(resultSet.getString("TABLE_NAME").inProperCase)
        }
        resultSet.close()
        return result
    }

    override fun modifyColumn(column: Column<*>) =
        super.modifyColumn(column).replace("MODIFY COLUMN", "MODIFY")

    override fun tableColumns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        val rs = TransactionManager.current().db.metadata.getColumns(null, getDatabase(), "%", "%")
        val result = rs.extractColumns(tables) {
            it.getString("TABLE_NAME") to ColumnMetadata(it.getString("COLUMN_NAME"), it.getInt("DATA_TYPE"), it.getBoolean("NULLABLE"))
        }
        rs.close()
        return result
    }

    companion object {
        const val dialectName = "oracle"
    }
}
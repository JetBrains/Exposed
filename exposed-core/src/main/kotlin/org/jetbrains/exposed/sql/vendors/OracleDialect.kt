package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal object OracleDataTypeProvider : DataTypeProvider() {

    override fun integerAutoincType() = "NUMBER(12)"

    override fun integerType() = "NUMBER(12)"

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
        e is LiteralOp<*> && e.columnType is IDateColumnType -> "DATE ${super.processForDefaultValue(e)}"
        else -> super.processForDefaultValue(e)
    }

    override val blobAsStream = true
}

internal object OracleFunctionProvider : FunctionProvider() {

    override fun<T:String?> substring(expr: Expression<T>, start: Expression<Int>, length: Expression<Int>, builder: QueryBuilder, prefix: String) =
            super.substring(expr, start, length, builder, "SUBSTR")

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

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) = queryBuilder {
        if (expr.orderBy.size != 1)
            TransactionManager.current().throwUnsupportedException("LISTAGG requires single order by clause")
        append("LISTAGG(")
        append(expr.expr)
        expr.separator?.let {
            append(", '$it'")
        }
        append(") WITHIN GROUP (ORDER BY ")
        val (col, order) = expr.orderBy.single()
        append(col, " ", order.name, ")")
    }

    override fun <T : String?> concat(separator: String, queryBuilder: QueryBuilder, vararg expr: Expression<T>) = queryBuilder {
        if (separator == "")
            expr.toList().appendTo(separator = " || ") { +it }
        else
            expr.toList().appendTo(separator = " || '$separator' || ") { +it }
    }

    override fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("Extract(YEAR FROM")
        append(expr)
        append(")")
    }

    override fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("Extract(MONTH FROM")
        append(expr)
        append(")")
    }

    override fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("Extract(DAY FROM")
        append(expr)
        append(")")
    }

    override fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("Extract(HOUR FROM")
        append(expr)
        append(")")
    }

    override fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("Extract(MINUTE FROM")
        append(expr)
        append(")")
    }

    override fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("Extract(SECOND FROM")
        append(expr)
        append(")")
    }
}

open class OracleDialect : VendorDialect(dialectName, OracleDataTypeProvider, OracleFunctionProvider) {

    override val supportsMultipleGeneratedKeys = false
    override val supportsIfNotExists = false
    override val needsSequenceToAutoInc = true
    override val needsQuotesWhenSymbolsInNames = false
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true

    override val defaultReferenceOption: ReferenceOption = ReferenceOption.NO_ACTION

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun modifyColumn(column: Column<*>) =
        super.modifyColumn(column).replace("MODIFY COLUMN", "MODIFY")

    companion object {
        const val dialectName = "oracle"
    }
}
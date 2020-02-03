package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal object OracleDataTypeProvider : DataTypeProvider() {
    override fun integerType(): String = "NUMBER(12)"
    override fun integerAutoincType(): String = "NUMBER(12)"
    override fun longType(): String = "NUMBER(19)"
    override fun longAutoincType(): String = "NUMBER(19)"
    override fun textType(): String = "CLOB"
    override fun binaryType(): String = "BLOB"
    override fun binaryType(length: Int): String {
        exposedLogger.warn("The length of the binary column is not required.")
        return binaryType()
    }

    override val blobAsStream = true
    override fun blobType(): String = "BLOB"
    override fun uuidType(): String = "RAW(16)"
    override fun dateTimeType(): String = "TIMESTAMP"
    override fun booleanType(): String = "CHAR(1)"
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
}

internal object OracleFunctionProvider : FunctionProvider() {

    /**
     * SQL function that generates a random value uniformly distributed between 0 (inclusive) and 1 (exclusive).
     *
     * **Note:** Oracle ignores the [seed]. You have to use the `dbms_random.seed` function manually.
     */
    override fun random(seed: Int?): String = "dbms_random.value"

    override fun <T : String?> substring(
        expr: Expression<T>,
        start: Expression<Int>,
        length: Expression<Int>,
        builder: QueryBuilder,
        prefix: String
    ): Unit = super.substring(expr, start, length, builder, "SUBSTR")

    override fun <T : String?> concat(
        separator: String,
        queryBuilder: QueryBuilder,
        vararg expr: Expression<T>
    ): Unit = queryBuilder {
        if (separator == "") {
            expr.toList().appendTo(separator = " || ") { +it }
        } else {
            expr.toList().appendTo(separator = " || '$separator' || ") { +it }
        }
    }

    override fun <T : String?> groupConcat(
        expr: GroupConcat<T>,
        queryBuilder: QueryBuilder
    ): Unit = queryBuilder {
        if (expr.orderBy.size != 1) {
            TransactionManager.current().throwUnsupportedException("SQLServer supports only single column in ORDER BY clause in LISTAGG")
        }
        append("LISTAGG(")
        append(expr.expr)
        expr.separator?.let {
            append(", '$it'")
        }
        append(") WITHIN GROUP (ORDER BY ")
        val (col, order) = expr.orderBy.single()
        append(col, " ", order.name, ")")
    }

    override fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(YEAR FROM ")
        append(expr)
        append(")")
    }

    override fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(MONTH FROM ")
        append(expr)
        append(")")
    }

    override fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(DAY FROM ")
        append(expr)
        append(")")
    }

    override fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(HOUR FROM ")
        append(expr)
        append(")")
    }

    override fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(MINUTE FROM ")
        append(expr)
        append(")")
    }

    override fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(SECOND FROM ")
        append(expr)
        append(")")
    }

    override fun insert(
        ignore: Boolean,
        table: Table,
        columns: List<Column<*>>,
        expr: String,
        transaction: Transaction
    ): String {
        return table.autoIncColumn?.takeIf { it !in columns }?.let {
            val newExpr = if (expr.isBlank()) {
                "VALUES (${it.autoIncSeqName!!}.NEXTVAL)"
            } else {
                expr.replace("VALUES (", "VALUES (${it.autoIncSeqName!!}.NEXTVAL, ")
            }

            super.insert(ignore, table, listOf(it) + columns, newExpr, transaction)
        } ?: super.insert(ignore, table, columns, expr, transaction)
    }

    override fun update(
        targets: ColumnSet,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        val def = super.update(targets, columnsAndValues, null, where, transaction)
        return when {
            limit != null && where != null -> "$def AND ROWNUM <= $limit"
            limit != null -> "$def WHERE ROWNUM <= $limit"
            else -> def
        }
    }

    override fun delete(
        ignore: Boolean,
        table: Table,
        where: String?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("Oracle doesn't support LIMIT in DELETE clause.")
        }
        return super.delete(ignore, table, where, limit, transaction)
    }

    override fun queryLimit(size: Int, offset: Int, alreadyOrdered: Boolean): String {
        return (if (offset > 0) " OFFSET $offset ROWS" else "") + " FETCH FIRST $size ROWS ONLY"
    }
}

/**
 * Oracle dialect implementation.
 */
open class OracleDialect : VendorDialect(dialectName, OracleDataTypeProvider, OracleFunctionProvider) {
    override val supportsIfNotExists: Boolean = false
    override val needsSequenceToAutoInc: Boolean = true
    override val defaultReferenceOption: ReferenceOption = ReferenceOption.NO_ACTION
    override val needsQuotesWhenSymbolsInNames: Boolean = false
    override val supportsMultipleGeneratedKeys: Boolean = false
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun modifyColumn(column: Column<*>): String = super.modifyColumn(column).replace("MODIFY COLUMN", "MODIFY")

    override fun createDatabase(name: String): String = "CREATE DATABASE ${name.inProperCase()}"

    override fun dropDatabase(name: String): String = "DROP DATABASE ${name.inProperCase()}"

    companion object {
        /** Oracle dialect name */
        const val dialectName: String = "oracle"
    }
}

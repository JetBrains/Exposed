package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

internal object SQLServerDataTypeProvider : DataTypeProvider() {
    override fun shortAutoincType() = "INT IDENTITY(1,1)"

    override fun longAutoincType() = "BIGINT IDENTITY(1,1)"

    override fun blobType() = "VARBINARY(MAX)"

    override val blobAsStream: Boolean = true

    override fun booleanType() = "BIT"

    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"

    override fun dateTimeType() = "DATETIME2"

    override fun uuidType() = "uniqueidentifier"

    override fun uuidToDB(value: UUID) = value.toString()
}

internal object SQLServerFunctionProvider : FunctionProvider() {
    override fun random(seed: Int?) = if (seed != null) "RAND(${seed})" else "RAND(CHECKSUM(NEWID()))"
    override fun queryLimit(size: Int, offset: Int, alreadyOrdered: Boolean): String {
        return if (!alreadyOrdered) {
            " ORDER BY(SELECT NULL) "
        } else {
            ""
        } + " OFFSET $offset ROWS FETCH NEXT $size ROWS ONLY"
    }

    override fun update(targets: ColumnSet, columnsAndValues: List<Pair<Column<*>, Any?>>, limit: Int?, where: Op<Boolean>?, transaction: Transaction): String {
        val def = super.update(targets, columnsAndValues, null, where, transaction)
        return if (limit != null) def.replaceFirst("UPDATE", "UPDATE TOP($limit)") else def
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        val def = super.delete(ignore, table, where, null, transaction)
        return if (limit != null) def.replaceFirst("DELETE", "DELETE TOP($limit)") else def
    }

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        val tr = TransactionManager.current()
        return when {
            expr.separator == null ->
                tr.throwUnsupportedException("SQLServer requires explicit separator in STRING_AGG")
            expr.orderBy.size > 1 ->
                tr.throwUnsupportedException("SQLServer supports only single column in ORDER BY clause in STRING_AGG")
            else -> queryBuilder {
                append("STRING_AGG(")
                append(expr.expr)
                append(", '${expr.separator}')")
                expr.orderBy.singleOrNull()?.let { (col, order) ->
                    append(" WITHIN GROUP (ORDER BY ", col, " ", order.name, ")")
                }
            }
        }
    }

    override fun <T : String?> regexp(expr1: Expression<T>, pattern: Expression<String>, caseSensitive: Boolean, queryBuilder: QueryBuilder) {
        TransactionManager.current().throwUnsupportedException("SQLServer doesn't provide built in REGEXP expression, use LIKE instead")
    }
}

open class SQLServerDialect : VendorDialect(dialectName, SQLServerDataTypeProvider, SQLServerFunctionProvider) {
    override val supportsIfNotExists = false
    override val needsQuotesWhenSymbolsInNames = false

    override val defaultReferenceOption: ReferenceOption get() = ReferenceOption.NO_ACTION

    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true

    override fun modifyColumn(column: Column<*>) =
        super.modifyColumn(column).replace("MODIFY COLUMN", "ALTER COLUMN")

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    companion object {
        const val dialectName = "sqlserver"
    }
}
package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

internal object SQLServerDataTypeProvider : DataTypeProvider() {
    override fun integerAutoincType(): String = "INT IDENTITY(1,1)"
    override fun longAutoincType(): String = "BIGINT IDENTITY(1,1)"
    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override val blobAsStream: Boolean = true
    override fun blobType(): String = "VARBINARY(MAX)"
    override fun uuidType(): String = "uniqueidentifier"
    override fun uuidToDB(value: UUID): Any = value.toString()
    override fun dateTimeType(): String = "DATETIME2"
    override fun booleanType(): String = "BIT"
    override fun booleanToStatementString(bool: Boolean): String = if (bool) "1" else "0"
}

internal object SQLServerFunctionProvider : FunctionProvider() {
    override fun nextVal(seq: Sequence, builder: QueryBuilder) = builder {
        append("NEXT VALUE FOR ", seq.identifier)
    }

    override fun random(seed: Int?): String = if (seed != null) "RAND(${seed})" else "RAND(CHECKSUM(NEWID()))"

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        val tr = TransactionManager.current()
        return when {
            expr.separator == null -> tr.throwUnsupportedException("SQLServer requires explicit separator in STRING_AGG.")
            expr.orderBy.size > 1 -> tr.throwUnsupportedException("SQLServer supports only single column in ORDER BY clause in STRING_AGG.")
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

    override fun <T : String?> regexp(
        expr1: Expression<T>,
        pattern: Expression<String>,
        caseSensitive: Boolean,
        queryBuilder: QueryBuilder
    ): Unit = TransactionManager.current().throwUnsupportedException("SQLServer doesn't provide built in REGEXP expression, use LIKE instead.")

    override fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(YEAR, ", expr, ")")
    }

    override fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(MONTH, ", expr, ")")
    }

    override fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(DAY, ", expr, ")")
    }

    override fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(HOUR, ", expr, ")")
    }

    override fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(SECOND, ", expr, ")")
    }

    override fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(MINUTE, ", expr, ")")
    }

    override fun update(target: Table, columnsAndValues: List<Pair<Column<*>, Any?>>, limit: Int?, where: Op<Boolean>?, transaction: Transaction): String {
        val def = super.update(target, columnsAndValues, null, where, transaction)
        return if (limit != null) def.replaceFirst("UPDATE", "UPDATE TOP($limit)") else def
    }

    override fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = with(QueryBuilder(true)) {
        val tableToUpdate = columnsAndValues.map { it.first.table }.distinct().singleOrNull()
        if (tableToUpdate == null) {
            transaction.throwUnsupportedException("SQLServer supports a join updates with a single table columns to update.")
        }
        if (targets.joinParts.any { it.joinType != JoinType.INNER }) {
            exposedLogger.warn("All tables in UPDATE statement will be joined with inner join")
        }
        if (limit != null)
            +"UPDATE TOP($limit)"
        else
            +"UPDATE "
        tableToUpdate.describe(transaction, this)
        +" SET "
        columnsAndValues.appendTo(this) { (col, value) ->
            append("${transaction.identity(col)}=")
            registerArgument(col, value)
        }
        +" FROM "
        if (targets.table != tableToUpdate)
            targets.table.describe(transaction, this)

        targets.joinParts.appendTo(this, ",") {
            if (it.joinPart != tableToUpdate)
                it.joinPart.describe(transaction, this)
        }
        +" WHERE "
        targets.joinParts.appendTo(this, " AND ") {
            it.appendConditions(this)
        }
        where?.let {
            + " AND "
            +it
        }
        limit?.let { +" LIMIT $it" }
        toString()
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        val def = super.delete(ignore, table, where, null, transaction)
        return if (limit != null) def.replaceFirst("DELETE", "DELETE TOP($limit)") else def
    }

    override fun queryLimit(size: Int, offset: Int, alreadyOrdered: Boolean): String {
        return (if (alreadyOrdered) "" else " ORDER BY(SELECT NULL)") + " OFFSET $offset ROWS FETCH NEXT $size ROWS ONLY"
    }
}

/**
 * SQLServer dialect implementation.
 */
open class SQLServerDialect : VendorDialect(dialectName, SQLServerDataTypeProvider, SQLServerFunctionProvider) {
    override val supportsIfNotExists: Boolean = false
    override val defaultReferenceOption: ReferenceOption get() = ReferenceOption.NO_ACTION
    override val needsQuotesWhenSymbolsInNames: Boolean = false
    override val supportsSequenceAsGeneratedKeys: Boolean = false
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun modifyColumn(column: Column<*>): String =
        super.modifyColumn(column).replace("MODIFY COLUMN", "ALTER COLUMN")

    override fun createDatabase(name: String): String = "CREATE DATABASE ${name.inProperCase()}"

    override fun dropDatabase(name: String) = "DROP DATABASE ${name.inProperCase()}"

    companion object {
        /** SQLServer dialect name */
        const val dialectName: String = "sqlserver"
    }
}

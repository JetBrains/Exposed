package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

internal object PostgreSQLDataTypeProvider : DataTypeProvider() {
    override fun integerAutoincType(): String = "SERIAL"
    override fun longAutoincType(): String = "BIGSERIAL"
    override fun uuidType(): String = "uuid"
    override fun binaryType(): String = "bytea"
    override fun binaryType(length: Int): String {
        exposedLogger.warn("The length of the binary column is not required.")
        return binaryType()
    }

    override val blobAsStream: Boolean = true
    override fun blobType(): String = "bytea"
    override fun uuidToDB(value: UUID): Any = value
    override fun dateTimeType(): String = "TIMESTAMP"
}

internal object PostgreSQLFunctionProvider : FunctionProvider() {

    override fun nextVal(seq: Sequence, builder: QueryBuilder): Unit = builder {
        append("NEXTVAL('", seq.identifier, "')")
    }

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        val tr = TransactionManager.current()
        return when {
            expr.orderBy.isNotEmpty() -> tr.throwUnsupportedException("PostgreSQL doesn't support ORDER BY in STRING_AGG function.")
            expr.distinct -> tr.throwUnsupportedException("PostgreSQL doesn't support DISTINCT in STRING_AGG function.")
            expr.separator == null -> tr.throwUnsupportedException("PostgreSQL requires explicit separator in STRING_AGG function.")
            else -> queryBuilder { append("STRING_AGG(", expr.expr, ", '", expr.separator, "')") }
        }
    }

    override fun <T : String?> regexp(
        expr1: Expression<T>,
        pattern: Expression<String>,
        caseSensitive: Boolean,
        queryBuilder: QueryBuilder
    ): Unit = queryBuilder {
        append(expr1)
        if (caseSensitive) {
            append(" ~ ")
        } else {
            append(" ~* ")
        }
        append(pattern)
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

    private const val onConflictIgnore = "ON CONFLICT DO NOTHING"

    override fun insert(
        ignore: Boolean,
        table: Table,
        columns: List<Column<*>>,
        expr: String,
        transaction: Transaction
    ): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) "$def $onConflictIgnore" else def
    }

    override fun update(
        target: Table,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in UPDATE clause.")
        }
        return super.update(target, columnsAndValues, limit, where, transaction)
    }

    override fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = with(QueryBuilder(true)) {
        if (limit != null) {
            transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in UPDATE clause.")
        }
        val tableToUpdate = columnsAndValues.map { it.first.table }.distinct().singleOrNull()
        if (tableToUpdate == null) {
            transaction.throwUnsupportedException("PostgreSQL supports a join updates with a single table columns to update.")
        }
        if (targets.joinParts.any { it.joinType != JoinType.INNER }) {
            exposedLogger.warn("All tables in UPDATE statement will be joined with inner join")
        }
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

    override fun replace(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        transaction: Transaction
    ): String {
        val builder = QueryBuilder(true)
        val sql = if (data.isEmpty()) {
            ""
        } else {
            data.appendTo(builder, prefix = "VALUES (", postfix = ")") { (col, value) -> registerArgument(col, value) }.toString()
        }

        val columns = data.map { it.first }

        val def = super.insert(false, table, columns, sql, transaction)

        val uniqueCols = columns.filter { it.indexInPK != null }.sortedBy { it.indexInPK }
        if (uniqueCols.isEmpty()) {
            transaction.throwUnsupportedException("PostgreSQL replace table must supply at least one primary key.")
        }
        val conflictKey = uniqueCols.joinToString { transaction.identity(it) }
        return def + "ON CONFLICT ($conflictKey) DO UPDATE SET " + columns.joinToString { "${transaction.identity(it)}=EXCLUDED.${transaction.identity(it)}" }
    }

    override fun delete(
        ignore: Boolean,
        table: Table,
        where: String?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in DELETE clause.")
        }
        return super.delete(ignore, table, where, limit, transaction)
    }
}

/**
 * PostgreSQL dialect implementation.
 */
open class PostgreSQLDialect : VendorDialect(dialectName, PostgreSQLDataTypeProvider, PostgreSQLFunctionProvider) {
    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun modifyColumn(column: Column<*>): String = buildString {
        val colName = TransactionManager.current().identity(column)
        append("ALTER COLUMN $colName TYPE ${column.columnType.sqlType()},")
        append("ALTER COLUMN $colName ")
        if (column.columnType.nullable)
            append("DROP ")
        else
            append("SET ")
        append("NOT NULL")
        column.dbDefaultValue?.let {
            append(", ALTER COLUMN $colName SET DEFAULT ${PostgreSQLDataTypeProvider.processForDefaultValue(it)}")
        }
    }

    override fun createDatabase(name: String): String = "CREATE DATABASE ${name.inProperCase()}"

    override fun dropDatabase(name: String): String = "DROP DATABASE ${name.inProperCase()}"

    companion object {
        /** PostgreSQL dialect name */
        const val dialectName: String = "postgresql"
    }
}

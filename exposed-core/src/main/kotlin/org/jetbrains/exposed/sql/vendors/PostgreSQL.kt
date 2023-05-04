package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

internal object PostgreSQLDataTypeProvider : DataTypeProvider() {
    override fun byteType(): String = "SMALLINT"
    override fun floatType(): String = "REAL"
    override fun integerAutoincType(): String = "SERIAL"
    override fun longAutoincType(): String = "BIGSERIAL"
    override fun uuidType(): String = "uuid"
    override fun binaryType(): String = "bytea"
    override fun binaryType(length: Int): String {
        exposedLogger.warn("The length of the binary column is not required.")
        return binaryType()
    }
    override fun blobType(): String = "bytea"
    override fun uuidToDB(value: UUID): Any = value
    override fun dateTimeType(): String = "TIMESTAMP"
    override fun ubyteType(): String = "SMALLINT"
}

internal object PostgreSQLFunctionProvider : FunctionProvider() {

    override fun nextVal(seq: Sequence, builder: QueryBuilder): Unit = builder {
        append("NEXTVAL('", seq.identifier, "')")
    }

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        val tr = TransactionManager.current()
        return when (expr.separator) {
            null -> tr.throwUnsupportedException("PostgreSQL requires explicit separator in STRING_AGG function.")
            else -> queryBuilder {
                append("STRING_AGG(")
                if (expr.distinct) append(" DISTINCT ")
                append(expr.expr, ", '", expr.separator, "'")
                if (expr.orderBy.isNotEmpty()) {
                    expr.orderBy.appendTo(prefix = " ORDER BY ") {
                        append(it.first, " ", it.second.name)
                    }
                }
                append(")")
            }
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
        append("POSITION(\'", substring, "\' IN ", expr, ")")
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
            ?: transaction.throwUnsupportedException("PostgreSQL supports a join updates with a single table columns to update.")
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

        appendJoinPartForUpdateClause(tableToUpdate, targets, transaction)

        where?.let {
            +" AND "
            +it
        }
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

        val uniqueCols = table.primaryKey?.columns
        if (uniqueCols.isNullOrEmpty()) {
            transaction.throwUnsupportedException("PostgreSQL replace table must supply at least one primary key.")
        }
        val conflictKey = uniqueCols.joinToString { transaction.identity(it) }
        return def + "ON CONFLICT ($conflictKey) DO UPDATE SET " + columns.joinToString {
            "${transaction.identity(it)}=EXCLUDED.${transaction.identity(it)}"
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
            transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in DELETE clause.")
        }
        return super.delete(ignore, table, where, limit, transaction)
    }
}

/**
 * PostgreSQL dialect implementation.
 */
open class PostgreSQLDialect : VendorDialect(dialectName, PostgreSQLDataTypeProvider, PostgreSQLFunctionProvider) {
    override val supportsOrderByNullsFirstLast: Boolean = true

    override val requiresAutoCommitOnCreateDrop: Boolean = true

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> = listOf(buildString {
        val tr = TransactionManager.current()
        append("ALTER TABLE ${tr.identity(column.table)} ")
        val colName = tr.identity(column)
        append("ALTER COLUMN $colName TYPE ${column.columnType.sqlType()}")

        if (columnDiff.nullability) {
            append(", ALTER COLUMN $colName ")
            if (column.columnType.nullable) {
                append("DROP ")
            } else {
                append("SET ")
            }
            append("NOT NULL")
        }
        if (columnDiff.defaults) {
            column.dbDefaultValue?.let {
                append(", ALTER COLUMN $colName SET DEFAULT ${PostgreSQLDataTypeProvider.processForDefaultValue(it)}")
            } ?: run {
                append(",  ALTER COLUMN $colName DROP DEFAULT")
            }
        }
    })

    override fun createDatabase(name: String): String = "CREATE DATABASE ${name.inProperCase()}"

    override fun dropDatabase(name: String): String = "DROP DATABASE ${name.inProperCase()}"

    override fun setSchema(schema: Schema): String = "SET search_path TO ${schema.identifier}"

    override fun createIndexWithType(name: String, table: String, columns: String, type: String): String {
        return "CREATE INDEX $name ON $table USING $type $columns"
    }

    companion object : DialectNameProvider("postgresql")
}

/**
 * PostgreSQL dialect implementation using the pgjdbc-ng jdbc driver.
 *
 * The driver accepts basic URLs in the following format : jdbc:pgsql://localhost:5432/db
 */
open class PostgreSQLNGDialect : PostgreSQLDialect() {
    override val requiresAutoCommitOnCreateDrop: Boolean = true

    companion object : DialectNameProvider("pgsql")
}

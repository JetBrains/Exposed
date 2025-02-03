package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal object MariaDBDataTypeProvider : MysqlDataTypeProvider() {
    override fun timestampWithTimeZoneType(): String {
        throw UnsupportedByDialectException("This vendor does not support timestamp with time zone data type", currentDialect)
    }

    override fun processForDefaultValue(e: Expression<*>): String = when {
        e is LiteralOp<*> -> (e.columnType as IColumnType<Any?>).valueAsDefaultString(e.value)
        e is Function<*> || currentDialect is MariaDBDialect -> "$e"
        else -> "($e)"
    }
}

internal object MariaDBFunctionProvider : MysqlFunctionProvider() {
    override fun nextVal(seq: Sequence, builder: QueryBuilder) = builder {
        append("NEXTVAL(", seq.identifier, ")")
    }

    override fun <T : String?> regexp(
        expr1: Expression<T>,
        pattern: Expression<String>,
        caseSensitive: Boolean,
        queryBuilder: QueryBuilder
    ): Unit = queryBuilder {
        append(expr1, " REGEXP ", pattern)
    }

    override fun <T : String?> locate(
        queryBuilder: QueryBuilder,
        expr: Expression<T>,
        substring: String
    ) = queryBuilder {
        append("LOCATE(\'", substring, "\',", expr, ")")
    }

    override fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        val sql = super.update(targets, columnsAndValues, null, where, transaction)
        return if (limit != null) "$sql LIMIT $limit" else sql
    }

    override fun explain(
        analyze: Boolean,
        options: String?,
        internalStatement: String,
        transaction: Transaction
    ): String {
        val sql = super.explain(analyze, options, internalStatement, transaction)
        return if (analyze) {
            sql.substringAfter("EXPLAIN ")
        } else {
            sql
        }
    }

    override fun returning(
        mainSql: String,
        returning: List<Expression<*>>,
        transaction: Transaction
    ): String {
        return with(QueryBuilder(true)) {
            +"$mainSql RETURNING "
            returning.appendTo { +it }
            toString()
        }
    }

    override fun isUpsertAliasSupported(dialect: DatabaseDialect): Boolean = false
}

/**
 * MariaDB dialect implementation.
 */
open class MariaDBDialect : MysqlDialect() {
    override val name: String = dialectName
    override val dataTypeProvider: DataTypeProvider = MariaDBDataTypeProvider
    override val functionProvider: FunctionProvider = MariaDBFunctionProvider
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true
    override val supportsSetDefaultReferenceOption: Boolean = false
    override val supportsCreateSequence: Boolean by lazy {
        TransactionManager.current().db.isVersionCovers(SEQUENCE_MIN_MAJOR_VERSION, SEQUENCE_MIN_MINOR_VERSION)
    }

    // actually MariaDb supports it but jdbc driver prepares statement without RETURNING clause
    override val supportsSequenceAsGeneratedKeys: Boolean = false

    @Suppress("MagicNumber")
    override val sequenceMaxValue: Long by lazy {
        if (TransactionManager.current().db.isVersionCovers(11, 5)) {
            super.sequenceMaxValue
        } else {
            Long.MAX_VALUE - 1
        }
    }

    /** Returns `true` if the MariaDB database version is greater than or equal to 5.3. */
    @Suppress("MagicNumber")
    override fun isFractionDateTimeSupported(): Boolean = TransactionManager.current().db.isVersionCovers(5, 3)

    override fun isTimeZoneOffsetSupported(): Boolean = false

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean {
        if (e is LiteralOp<*>) return true
        if (fullVersion >= "10.2.1") {
            return true
        }

        // This check is quite optimistic, it will not allow to create a varchar columns with "CURRENT_DATE" default value for example
        // Comparing to the previous variant with white list of functions the new variant does not reject valid values,
        // it could be checked on the test UpsertTests::testUpsertWithColumnExpressions()
        return e.toString().trim() !in notAcceptableDefaults
    }

    override fun createIndex(index: Index): String {
        if (index.functions != null) {
            exposedLogger.warn(
                "Functional index on ${index.table.tableName} using ${index.functions.joinToString { it.toString() }} can't be created in MariaDB"
            )
            return ""
        }
        return super.createIndex(index)
    }

    companion object : DialectNameProvider("MariaDB") {
        private const val SEQUENCE_MIN_MAJOR_VERSION = 10
        private const val SEQUENCE_MIN_MINOR_VERSION = 3
    }
}

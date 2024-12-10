package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

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
}

/**
 * MariaDB dialect implementation.
 */
class MariaDBDialect : MysqlDialect() {
    override val name: String = dialectName
    override val functionProvider: FunctionProvider = MariaDBFunctionProvider
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true
    override val supportsSetDefaultReferenceOption: Boolean = false
    override val supportsCreateSequence: Boolean by lazy {
        TransactionManager.current().db.isVersionCovers(SEQUENCE_MIN_MAJOR_VERSION, SEQUENCE_MIN_MINOR_VERSION)
    }

    // actually MariaDb supports it but jdbc driver prepares statement without RETURNING clause
    override val supportsSequenceAsGeneratedKeys: Boolean = false

    override fun createIndex(index: Index): String {
        if (index.functions != null) {
            exposedLogger.warn(
                "Functional index on ${index.table.tableName} using ${index.functions.joinToString { it.toString() }} can't be created in MariaDB"
            )
            return ""
        }
        return super.createIndex(index)
    }

    override fun sequences(): List<String> {
        val sequences = mutableListOf<String>()
        TransactionManager.current().exec("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES") { rs ->
            while (rs.next()) {
                sequences.add(rs.getString("SEQUENCE_NAME"))
            }
        }
        return sequences
    }

    companion object : DialectNameProvider("MariaDB") {
        const val SEQUENCE_MIN_MAJOR_VERSION = 11
        const val SEQUENCE_MIN_MINOR_VERSION = 5
    }
}

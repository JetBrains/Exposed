package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*

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
}

/**
 * MariaDB dialect implementation.
 */
class MariaDBDialect : MysqlDialect() {
    override val name: String = dialectName
    override val functionProvider: FunctionProvider = MariaDBFunctionProvider
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true
    override val supportsSetDefaultReferenceOption: Boolean = false

    override fun createIndex(index: Index): String {
        if (index.functions != null) {
            exposedLogger.warn(
                "Functional index on ${index.table.tableName} using ${index.functions.joinToString { it.toString() }} can't be created in MariaDB"
            )
            return ""
        }
        return super.createIndex(index)
    }

    companion object : DialectNameProvider("MariaDB")
}

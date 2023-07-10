package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Sequence
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.exposedLogger

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
}

/**
 * MariaDB dialect implementation.
 */
class MariaDBDialect : MysqlDialect() {
    override val name: String = dialectName
    override val functionProvider: FunctionProvider = MariaDBFunctionProvider
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true

    override fun createIndex(index: Index): String {
        if (index.functions != null) {
            exposedLogger.warn(
                "Functional index on ${index.table.tableName} using ${index.functions.second.joinToString { it.toString() }} can't be created in MariaDB"
            )
            return ""
        }
        return super.createIndex(index)
    }

    companion object : DialectNameProvider("mariadb")
}

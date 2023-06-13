package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Sequence
import org.jetbrains.exposed.sql.append

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

    companion object : DialectNameProvider("mariadb")
}

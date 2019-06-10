package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder

internal object MariaDBFunctionProvider :  MysqlFunctionProvider() {
    override fun <T : String?> regexp(expr1: Expression<T>, pattern: Expression<String>, caseSensitive: Boolean, queryBuilder: QueryBuilder): String {
        return "${expr1.toSQL(queryBuilder)} REGEXP ${pattern.toSQL(queryBuilder)}"
    }
}

class MariaDBDialect : MysqlDialect() {
    override val functionProvider : FunctionProvider = MariaDBFunctionProvider
    override val name: String = dialectName
    override val supportsOnlyIdentifiersInGeneratedKeys = true
    companion object {
        const val dialectName = "mariadb"
    }
}
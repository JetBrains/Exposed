package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Sequence

internal object MariaDBFunctionProvider :  MysqlFunctionProvider() {
    override fun <T : String?> regexp(expr1: Expression<T>, pattern: Expression<String>, caseSensitive: Boolean, queryBuilder: QueryBuilder) {
        queryBuilder{ append(expr1, " REGEXP ", pattern) }
    }

    override fun nextVal(seq: Sequence, builder: QueryBuilder) = builder {
        append("NEXTVAL(", seq.identifier, ")")
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
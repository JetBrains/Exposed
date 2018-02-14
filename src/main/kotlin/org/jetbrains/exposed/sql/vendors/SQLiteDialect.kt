package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.QueryBuilder

internal object SQLiteDataTypeProvider : DataTypeProvider() {
    override fun shortAutoincType(): String = "INTEGER AUTO_INCREMENT"
    override fun longAutoincType(): String = "INTEGER AUTO_INCREMENT"
    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
    override fun dateTimeType(): String  = "NUMERIC"
    override val blobAsStream: Boolean = true
}

internal object SQLiteFunctionProvider : FunctionProvider() {
    override fun<T:String?> substring(expr: Expression<T>, start: ExpressionWithColumnType<Int>, length: ExpressionWithColumnType<Int>, builder: QueryBuilder): String =
            super.substring(expr, start, length, builder).replace("SUBSTRING", "substr")
}

internal class SQLiteDialect : VendorDialect(dialectName, SQLiteDataTypeProvider, SQLiteFunctionProvider) {
    override val supportsMultipleGeneratedKeys: Boolean = false
    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun getDatabase(): String = ""

    override fun createIndex(index: Index): String {
        val originalCreateIndex = super.createIndex(index.copy(unique = false))
        return if (index.unique) originalCreateIndex.replace("INDEX", "UNIQUE INDEX")
        else originalCreateIndex
    }

    companion object {
        const val dialectName = "sqlite"
    }
}
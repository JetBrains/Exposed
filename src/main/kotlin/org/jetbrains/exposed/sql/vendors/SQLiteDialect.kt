package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.QueryBuilder

internal object SQLiteDataTypeProvider : DataTypeProvider() {
    override fun shortAutoincType(): String = "INTEGER AUTO_INCREMENT"
    override fun longAutoincType(): String = "INTEGER AUTO_INCREMENT"
    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
    override fun dateTimeType(): String  = "NUMERIC"
    override val blobAsStream: Boolean = true
}

internal object SQLiteFunctionProvider : FunctionProvider() {
    override fun substring(expr: Expression<String?>, start: ExpressionWithColumnType<Int>, length: ExpressionWithColumnType<Int>, builder: QueryBuilder): String =
            super.substring(expr, start, length, builder).replace("SUBSTRING", "substr")
}

internal object SQLiteDialect : VendorDialect("sqlite", SQLiteDataTypeProvider, SQLiteFunctionProvider) {
    override val supportsMultipleGeneratedKeys: Boolean = false
    override fun getDatabase(): String = ""
}
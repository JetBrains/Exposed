package org.jetbrains.exposed.sql.vendors

internal object SQLiteDataTypeProvider : DataTypeProvider() {
    override fun shortAutoincType(): String = "INTEGER AUTO_INCREMENT"
    override fun longAutoincType(): String = "INTEGER AUTO_INCREMENT"
    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
    override fun dateTimeType(): String  = "NUMERIC"
    override val blobAsStream: Boolean = true
}

internal object SQLiteFunctionProvider : FunctionProvider() {
    override val substring: String = "substr"
}

internal object SQLiteDialect : VendorDialect("sqlite", SQLiteDataTypeProvider, SQLiteFunctionProvider) {
    override val supportsMultipleGeneratedKeys: Boolean = false
    override fun getDatabase(): String = ""
}
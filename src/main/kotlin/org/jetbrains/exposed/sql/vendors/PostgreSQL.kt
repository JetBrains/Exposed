package org.jetbrains.exposed.sql.vendors

internal object PostgreSQLDataTypeProvider : DataTypeProvider() {

    override fun shortAutoincType(): String = "SERIAL"

    override fun longAutoincType(): String = "SERIAL"

    override fun dateTimeType(): String = "TIMESTAMP"

    override fun uuidType(): String = "bytea"

    override fun blobType(): String = "bytea"

    override fun binaryType(length: Int): String = "bytea"

    override val blobAsStream: Boolean = true
}

internal object PostgreSQLDialect : VendorDialect("postgresql", PostgreSQLDataTypeProvider) {
    override val supportsExpressionsAsDefault: Boolean = true
}
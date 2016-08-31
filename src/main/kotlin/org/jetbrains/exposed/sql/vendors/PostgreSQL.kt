package org.jetbrains.exposed.sql.vendors

internal object PostgreSQLDialect : VendorDialect("postgresql") {

    override fun shortAutoincType(): String = "SERIAL"

    override fun longAutoincType(): String = "SERIAL"

    override fun dateTimeType(): String = "TIMESTAMP"

    override fun uuidType(): String = "bytea"

    override fun blobType(): String = "bytea"

    override fun binaryType(length: Int): String = "bytea"

    override fun bitValue(value: Int): String {
        return "CAST($value AS BIT)"
    }

    override val extraKeywords: List<String>
        get() = listOf("user")
}
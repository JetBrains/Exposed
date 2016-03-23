package org.jetbrains.exposed.sql.vendors

internal object PostgreSQLDialect : VendorDialect("postgresql") {

    override fun shortAutoincType(): String = "SERIAL"

    override fun longAutoincType(): String = "SERIAL"

    override fun dateTimeType(): String = "TIMESTAMP"

    override fun uuidType(): String = "bytea"

    override fun blobType(): String = "bytea"

}
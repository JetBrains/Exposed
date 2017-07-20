package org.jetbrains.exposed.sql.vendors

import java.util.*

internal object PostgreSQLDataTypeProvider : DataTypeProvider() {

    override fun shortAutoincType(): String = "SERIAL"

    override fun longAutoincType(): String = "BIGSERIAL"

    override fun dateTimeType(withTimezone: Boolean): String = if (withTimezone) "TIMESTAMP WITH TIME ZONE" else "TIMESTAMP"

    override fun uuidType(): String = "uuid"

    override fun blobType(): String = "bytea"

    override fun binaryType(length: Int): String = "bytea"

    override fun uuidToDB(value: UUID): Any = value

    override val blobAsStream: Boolean = true
}

internal object PostgreSQLDialect : VendorDialect("postgresql", PostgreSQLDataTypeProvider) {
    override val supportsExpressionsAsDefault: Boolean = true
}
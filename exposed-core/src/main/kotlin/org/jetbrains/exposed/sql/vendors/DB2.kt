package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*

internal object DB2DataTypeProvider : DataTypeProvider() {
    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override fun dateTimeType(): String = "TIMESTAMP"

    override fun ulongType(): String = "BIGINT"

    override fun textType(): String = "VARCHAR(32704)"
}

internal object DB2FunctionProvider : FunctionProvider() {

    override fun random(seed: Int?) = "RAND(${seed?.toString().orEmpty()})"
}

/**
 * DB2 dialect implementation.
 */
class DB2Dialect : VendorDialect(dialectName, DB2DataTypeProvider, DB2FunctionProvider) {
    override val name: String = dialectName
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true


    companion object {
        /** DB2 dialect name */
        const val dialectName: String = "db2"
    }
}

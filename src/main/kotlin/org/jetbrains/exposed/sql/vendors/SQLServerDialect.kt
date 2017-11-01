package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ReferenceOption

internal object SQLServerDataTypeProvider : DataTypeProvider() {
    override fun shortAutoincType(): String {
        return "INT IDENTITY(1,1)"
    }

    override fun longAutoincType(): String {
        return "BIGINT IDENTITY(1,1)"
    }

    override fun blobType(): String {
        return "VARBINARY(MAX)"
    }

    override val blobAsStream: Boolean = true

    override fun booleanType() = "BIT"

    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
}

internal object SQLServerFunctionProvider : FunctionProvider() {

    override fun random(seed: Int?) = if (seed != null) "RAND(${seed})" else "RAND(CHECKSUM(NEWID()))"
}

internal class SQLServerDialect : VendorDialect(dialectName, SQLServerDataTypeProvider, SQLServerFunctionProvider) {
    override val supportsIfNotExists = false
    override val needsQuotesWhenSymbolsInNames = false

    override val defaultReferenceOption: ReferenceOption get() = ReferenceOption.NO_ACTION

    override fun limit(size: Int, offset: Int, alreadyOrdered: Boolean): String {
        return if (!alreadyOrdered) {
            " ORDER BY(SELECT NULL) "
        } else {
            ""
        } + " OFFSET $offset ROWS FETCH NEXT $size ROWS ONLY"
    }

    override fun modifyColumn(column: Column<*>) =
        super.modifyColumn(column).replace("MODIFY COLUMN", "ALTER COLUMN")

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    companion object {
        const val dialectName = "sqlserver"
    }
}
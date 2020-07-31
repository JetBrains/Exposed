package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.ColumnMetadata
import java.math.BigDecimal

abstract class ExposedDatabaseMetadata(val database: String) {

    abstract val url: String
    abstract val version: BigDecimal

    abstract val databaseDialectName: String
    abstract val databaseProductVersion: String

    abstract val defaultIsolationLevel: Int

    abstract val supportsAlterTableWithAddColumn : Boolean
    abstract val supportsMultipleResultSets : Boolean
    abstract val supportsSelectForUpdate : Boolean

    @Deprecated(
        message = "it's temporary solution which will be replaced in a future releases. Do not use it in your code",
        level = DeprecationLevel.WARNING
    )
    abstract val currentScheme: String
    abstract fun resetCurrentScheme()
    abstract val tableNames: Map<String, List<String>>
    abstract val schemaNames: List<String>

    abstract fun columns(vararg tables: Table) : Map<Table, List<ColumnMetadata>>

    abstract fun existingIndices(vararg tables: Table): Map<Table, List<Index>>

    abstract fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>>

    abstract fun cleanCache()

    abstract val identifierManager: IdentifierManagerApi
}
package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.ColumnMetadata
import java.math.BigDecimal

abstract class ExposedDatabaseMetadata(val database: String) {

    abstract val url: String
    abstract val version: BigDecimal

    abstract val defaultIsolationLevel: Int

    abstract val supportsAlterTableWithAddColumn : Boolean
    abstract val supportsMultipleResultSets : Boolean
    abstract val supportsSelectForUpdate : Boolean
    abstract val databaseProductVersion: String

    abstract val tableNames: List<String>

    abstract fun columns(vararg tables: Table) : Map<Table, List<ColumnMetadata>>

    abstract fun existingIndices(vararg tables: Table): Map<Table, List<Index>>

    abstract fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>>

    abstract fun cleanCache()

    abstract val identifierManager: IdentifierManagerApi
}
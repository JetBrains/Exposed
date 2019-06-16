package org.jetbrains.exposed.sql.statements.api

import java.math.BigDecimal

abstract class ExposedDatabaseMetadata {

    abstract val url: String
    abstract val version: BigDecimal

    abstract val supportsAlterTableWithAddColumn : Boolean
    abstract val supportsMultipleResultSets : Boolean
    abstract val supportsSelectForUpdate : Boolean
    abstract val databaseProductVersion: String

    internal abstract val identifierManager: IdentifierManagerApi
}
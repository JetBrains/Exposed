package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import java.math.BigDecimal
import java.sql.DatabaseMetaData

class JdbcDatabaseMetadataImpl(val metadata: DatabaseMetaData) : ExposedDatabaseMetadata() {
    override val url: String by lazyMetadata {  url }

    override val version: BigDecimal by lazyMetadata { BigDecimal("$databaseMajorVersion.$databaseMinorVersion")}
    override val supportsAlterTableWithAddColumn by lazyMetadata { supportsAlterTableWithAddColumn() }
    override val supportsMultipleResultSets by lazyMetadata { supportsMultipleResultSets() }
    override val supportsSelectForUpdate: Boolean by lazyMetadata { supportsSelectForUpdate() }

    override val databaseProductVersion by lazyMetadata { databaseProductVersion!! }

    private fun <T> lazyMetadata(body: DatabaseMetaData.() -> T) = lazy { metadata.body() }

    override val identifierManager: IdentifierManagerApi = JdbcIdentifierManager(metadata)

}
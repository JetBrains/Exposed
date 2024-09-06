package org.jetbrains.exposed.sql.vendors

import io.r2dbc.spi.IsolationLevel

@Suppress("TooManyFunctions")
interface MetadataProvider {
    fun identifierQuoteString(): String

    fun storesUpperCaseIdentifiers(): Boolean

    fun storesUpperCaseQuotedIdentifiers(): Boolean

    fun storesLowerCaseIdentifiers(): Boolean

    fun storesLowerCaseQuotedIdentifiers(): Boolean

    fun supportsMixedCaseIdentifiers(): Boolean

    fun supportsMixedCaseQuotedIdentifiers(): Boolean

    fun sqlKeywords(): String

    fun extraNameCharacters(): String

    fun maxColumnNameLength(): Int

    fun supportsAlterTableWithAddColumn(): Boolean

    fun supportsMultipleResultSets(): Boolean

    fun supportsSelectForUpdate(): Boolean

    fun getDefaultTransactionIsolation(): IsolationLevel

    // QUERY SQL

    // ??? - how can these be retrieved? saving details on connect()?
    fun getUrl(): String

    // ??? - save?
    fun getUsername(): String

    // ??? - database?
    fun getCatalog(): String

    fun setCatalog(value: String): String

    fun getSchema(): String

    fun setSchema(value: String): String

    fun getCatalogs(): String

    fun getSchemas(): String

    // ???
    fun getReadOnlyMode(): String

    // ???
    fun setReadOnlyMode(value: Boolean): String

    fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String?, type: String): String

    fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String

    fun getPrimaryKeys(catalog: String, schema: String, table: String): String

    fun getIndexInfo(catalog: String, schema: String, table: String): String

    fun getImportedKeys(catalog: String, schema: String, table: String): String
}

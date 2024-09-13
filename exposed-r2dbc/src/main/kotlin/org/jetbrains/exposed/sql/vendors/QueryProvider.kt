package org.jetbrains.exposed.sql.vendors

/** Common interface for all R2DBC databases to query and retrieve metadata. */
@Suppress("TooManyFunctions")
interface QueryProvider {
    /** Metadata property provider for this database. */
    val propertyProvider: PropertyProvider

    /** SQL data type provider for this database. */
    val typeProvider: SqlTypeProvider

    // ??? - how can these be retrieved? with a query or saving details on connect()?
    fun getUrl(): String

    // ??? - how can these be retrieved? with a query or saving details on connect()?
    fun getUsername(): String

    // ??? - how can these be retrieved? with a query or saving details on connect()?

    fun getReadOnlyMode(): String

    // ??? - how can these be retrieved? with a query or saving details on connect()?
    fun setReadOnlyMode(value: Boolean): String

    /** Returns an SQL query to retrieve the name of the current catalog used by the connection. */
    fun getCatalog(): String

    /** Returns an SQL query to set the specified [value] as the current catalog used by the connection. */
    fun setCatalog(value: String): String

    /** Returns an SQL query to retrieve the name of the current schema used by the connection. */
    fun getSchema(): String

    /** Returns an SQL query to set the specified [value] as the current schema used by the connection. */
    fun setSchema(value: String): String

    /** Returns an SQL query to retrieve the names of all available catalogs. */
    fun getCatalogs(): String

    /** Returns an SQL query to retrieve the names of all available schema. */
    fun getSchemas(): String

    /** Returns an SQL query to retrieve details about all available tables in the specified [catalog]. */
    fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String): String

    /** Returns an SQL query to retrieve the names of all available catalogs. */
    fun getSequences(): String

    /** Returns an SQL query to retrieve details about all columns from the tables that match the specified pattern. */
    fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String

    /** Returns an SQL query to retrieve details about primary keys from the tables that match the specified pattern. */
    fun getPrimaryKeys(catalog: String, schema: String, table: String): String

    /** Returns an SQL query to retrieve details about all indices from the tables that match the specified pattern. */
    fun getIndexInfo(catalog: String, schema: String, table: String): String

    /** Returns an SQL query to retrieve details about foreign key columns from the tables that match the specified pattern. */
    fun getImportedKeys(catalog: String, schema: String, table: String): String
}

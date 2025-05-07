package org.jetbrains.exposed.r2dbc.sql.vendors.metadata

/** Base class responsible for providing SQL strings for all supported metadata queries in a database. */
@Suppress("TooManyFunctions")
internal interface QueryProvider {
    /** Metadata property provider for this database. */
    val propertyProvider: PropertyProvider

    /** SQL data type provider for this database. */
    val typeProvider: SqlTypeProvider

    /** Returns an SQL query to retrieve the username as known to the connected database. */
    fun getUsername(): String

    /** Returns an SQL query to retrieve whether the connection is in read-only mode. */
    fun getReadOnlyMode(): String

    /** Returns an SQL query to set the specified value as the read-only mode. */
    fun setReadOnlyMode(value: Boolean): String

    /** Returns an SQL query to retrieve the name of the mode of the connected database. */
    fun getDatabaseMode(): String

    /** Returns an SQL query to retrieve the name of the current catalog used by the connection. */
    fun getCatalog(): String

    /** Returns an SQL query to set the specified [value] as the current catalog used by the connection. */
    fun setCatalog(value: String): String

    /** Returns an SQL query to retrieve the name of the current schema used by the connection. */
    fun getSchema(): String

    /** Returns an SQL query to retrieve the names of all available catalogs. */
    fun getCatalogs(): String

    /** Returns an SQL query to retrieve the names of all available schema. */
    fun getSchemas(): String

    /** Returns an SQL query to retrieve details about all available tables that match the specified pattern. */
    fun getTables(catalog: String, schemaPattern: String): String

    /** Returns an SQL query to retrieve the names of all sequences in the database. */
    fun getAllSequences(): String

    /** Returns an SQL query to retrieve details about all sequences from the tables that match the specified pattern. */
    fun getSequences(catalog: String, schemaPattern: String, table: String): String

    /** Returns an SQL query to retrieve details about all columns from the tables that match the specified pattern. */
    fun getColumns(catalog: String, schemaPattern: String, table: String): String

    /** Returns an SQL query to retrieve details about primary keys from the tables that match the specified pattern. */
    fun getPrimaryKeys(catalog: String, schemaPattern: String, table: String): String

    /** Returns an SQL query to retrieve details about all indices from the tables that match the specified pattern. */
    fun getIndexInfo(catalog: String, schemaPattern: String, table: String): String

    /** Returns an SQL query to retrieve details about foreign key columns from the tables that match the specified pattern. */
    fun getImportedKeys(catalog: String, schemaPattern: String, table: String): String

    /** Returns an SQL query to retrieve details about all check constraints from the tables that match the specified pattern. */
    fun getCheckConstraints(catalog: String, schemaPattern: String, table: String): String
}

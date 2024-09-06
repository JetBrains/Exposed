package org.jetbrains.exposed.sql.vendors

import io.r2dbc.spi.IsolationLevel

class OracleMetadata : MetadataProvider {
    override fun identifierQuoteString(): String = "\""

    override fun storesUpperCaseIdentifiers(): Boolean = true

    override fun storesUpperCaseQuotedIdentifiers(): Boolean = false

    override fun storesLowerCaseIdentifiers(): Boolean = false

    override fun storesLowerCaseQuotedIdentifiers(): Boolean = false

    override fun supportsMixedCaseIdentifiers(): Boolean = false

    override fun supportsMixedCaseQuotedIdentifiers(): Boolean = true

    override fun sqlKeywords(): String {
        return "ACCESS,ADD,ALTER,AUDIT,CLUSTER,COLUMN,COMMENT,COMPRESS,CONNECT,DATE,DROP,EXCLUSIVE,FILE,IDENTIFIED,IMMEDIATE,INCREMENT," +
            "INDEX,INITIAL,INTERSECT,LEVEL,LOCK,LONG,MAXEXTENTS,MINUS,MODE,NOAUDIT,NOCOMPRESS,NOWAIT,NUMBER,OFFLINE,ONLINE,PCTFREE,PRIOR," +
            "all_PL_SQL_reserved_ words"
    }

    override fun extraNameCharacters(): String = "$#"

    override fun maxColumnNameLength(): Int = 128

    override fun supportsAlterTableWithAddColumn(): Boolean = true

    override fun supportsMultipleResultSets(): Boolean = false

    override fun supportsSelectForUpdate(): Boolean = true

    override fun getDefaultTransactionIsolation(): IsolationLevel = IsolationLevel.READ_COMMITTED

    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getCatalog(): String {
        TODO("Not yet implemented")
    }

    override fun setCatalog(value: String): String {
        TODO("Not yet implemented")
    }

    override fun getSchema(): String {
        TODO("Not yet implemented")
    }

    override fun setSchema(value: String): String {
        return "ALTER SESSION SET CURRENT_SCHEMA = $value"
    }

    // what should be done to simulate retrieving an empty result set?
    override fun getCatalogs(): String {
        return ""
    }

    override fun getSchemas(): String {
        TODO("Not yet implemented")
    }

    override fun getReadOnlyMode(): String {
        TODO("Not yet implemented")
    }

    override fun setReadOnlyMode(value: Boolean): String {
        TODO("Not yet implemented")
    }

    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String?, type: String): String {
        TODO("Not yet implemented")
    }

    override fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String {
        TODO("Not yet implemented")
    }

    override fun getPrimaryKeys(catalog: String, schema: String, table: String): String {
        TODO("Not yet implemented")
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        TODO("Not yet implemented")
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        TODO("Not yet implemented")
    }
}

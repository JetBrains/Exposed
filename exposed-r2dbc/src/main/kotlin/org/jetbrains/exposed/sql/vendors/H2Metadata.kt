package org.jetbrains.exposed.sql.vendors

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode

class H2Metadata : MetadataProvider {
    override fun identifierQuoteString(): String = "\""

    override fun storesUpperCaseIdentifiers(): Boolean = currentDialect.h2Mode !in lowerCaseFoldModes

    override fun storesUpperCaseQuotedIdentifiers(): Boolean = false

    override fun storesLowerCaseIdentifiers(): Boolean = currentDialect.h2Mode in lowerCaseFoldModes

    override fun storesLowerCaseQuotedIdentifiers(): Boolean = false

    override fun supportsMixedCaseIdentifiers(): Boolean = false

    override fun supportsMixedCaseQuotedIdentifiers(): Boolean = true

    private val lowerCaseFoldModes = listOf(
        H2CompatibilityMode.MariaDB, H2CompatibilityMode.PostgreSQL, H2CompatibilityMode.Oracle
    )

    override fun sqlKeywords(): String {
        val base = "CURRENT_CATALOG,CURRENT_SCHEMA,GROUPS,IF,ILIKE,KEY,OFFSET,QUALIFY,REGEXP,ROWNUM,_ROWID_"
        return when (currentDialect.h2Mode) {
            H2CompatibilityMode.MySQL -> "$base,LIMIT"
            H2CompatibilityMode.MariaDB -> "$base,LIMIT"
            H2CompatibilityMode.SQLServer -> "$base,TOP"
            H2CompatibilityMode.Oracle -> "$base,MINUS"
            H2CompatibilityMode.PostgreSQL -> "$base,LIMIT"
            null -> "$base,LIMIT,MINUS,TOP"
        }
    }

    override fun extraNameCharacters(): String = ""

    override fun maxColumnNameLength(): Int = 0

    override fun supportsAlterTableWithAddColumn(): Boolean = true

    override fun supportsMultipleResultSets(): Boolean = false

    override fun supportsSelectForUpdate(): Boolean = true

    override fun getDefaultTransactionIsolation(): IsolationLevel = IsolationLevel.READ_COMMITTED

    // QUERY SQL

    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getCatalog(): String {
        return "CALL DATABASE()"
    }

    override fun setCatalog(value: String): String {
        return ""
    }

    override fun getSchema(): String {
        TODO("Not yet implemented")
    }

    override fun setSchema(value: String): String {
        TODO("Not yet implemented")
    }

    override fun getCatalogs(): String {
        return "SELECT DISTINCT CATALOG_NAME TABLE_CAT FROM INFORMATION_SCHEMA.SCHEMATA ORDER BY CATALOG_NAME"
    }

    override fun getSchemas(): String {
        return "SELECT SCHEMA_NAME TABLE_SCHEM FROM INFORMATION_SCHEMA.SCHEMATA ORDER BY SCHEMA_NAME"
    }

    override fun getReadOnlyMode(): String {
        return "CALL READONLY()"
    }

    override fun setReadOnlyMode(value: Boolean): String {
        return ""
    }

    // what if type is sequences?
    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String?, type: String): String {
        return buildString {
            append("SELECT TABLE_CATALOG TABLE_CAT, TABLE_SCHEMA TABLE_SCHEM, TABLE_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLES WHERE TRUE ")
            if (!catalog.isNullOrEmpty()) {
                append("AND TABLE_CATALOG LIKE '$catalog' ")
            }
            if (!schemaPattern.isNullOrEmpty()) {
                append("AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            }
            if (!tableNamePattern.isNullOrEmpty()) {
                append("AND TABLE_NAME LIKE '$tableNamePattern' ")
            }
            append("AND TABLE_TYPE = 'BASE TABLE' ")
            append("ORDER BY TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String {
        return buildString {
            append("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_SIZE, DECIMAL_DIGITS, NULLABLE, COLUMN_DEF, ")
            append("CASE WHEN SEQUENCE_NAME IS NULL THEN CAST(NO AS VARCHAR) ELSE CAST(YES AS VARCHAR) END IS_AUTOINCREMENT ")
            append("FROM INFORMATION_SCHEMA.COLUMNS ")
            append("WHERE TABLE_CATALOG LIKE $catalog AND TABLE_SCHEMA LIKE $schemaPattern ")
            append("AND TABLE_NAME LIKE $tableNamePattern AND COLUMN_NAME LIKE %) ")
            append("ORDER BY TABLE_NAME")
        }
    }

    override fun getPrimaryKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT COLUMN_NAME, COALESCE(CONSTRAINT_NAME, INDEX_NAME) PK_NAME ")
            append("FROM INFORMATION_SCHEMA.INDEXES ")
            append("WHERE TABLE_CATALOG LIKE $catalog AND TABLE_SCHEMA LIKE $schema AND TABLE_NAME = $table ")
            append("AND PRIMARY_KEY = TRUE ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT NON_UNIQUE, INDEX_NAME, COLUMN_NAME, FILTER_CONDITION ")
            append("FROM INFORMATION_SCHEMA.INDEXES ")
            append("WHERE TABLE_CATALOG LIKE $catalog AND TABLE_SCHEMA LIKE $schema AND TABLE_NAME = $table ")
            append("AND (NON_UNIQUE=FALSE) ORDER BY NON_UNIQUE, INDEX_NAME")
        }
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT PKTABLE_NAME PKTABLE_NAME, PKCOLUMN_NAME, FKTABLE_NAME, FKCOLUMN_NAME, ")
            append("UPDATE_RULE, DELETE_RULE, FK_NAME ")
            append("FROM INFORMATION_SCHEMA.CROSS_REFERENCES ")
            append("WHERE FKTABLE_CATALOG LIKE $catalog AND FKTABLE_SCHEMA LIKE $schema ")
            append("AND FKTABLE_NAME = $table ORDER BY PKTABLE_NAME, FK_NAME")
        }
    }
}

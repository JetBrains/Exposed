package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode

internal object H2PropertyProvider : PropertyProvider() {
    override val storesUpperCaseIdentifiers: Boolean
        get() = currentDialect.h2Mode !in lowerCaseFoldModes

    override val storesLowerCaseIdentifiers: Boolean
        get() = currentDialect.h2Mode in lowerCaseFoldModes

    override val supportsMultipleResultSets: Boolean
        get() = false

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

    private val lowerCaseFoldModes = listOf(
        H2CompatibilityMode.MariaDB, H2CompatibilityMode.PostgreSQL, H2CompatibilityMode.Oracle
    )
}

@Suppress("MagicNumber")
internal object H2TypeProvider : SqlTypeProvider() {
    override val referenceOptions: Map<ReferenceOption, Int> by lazy {
        mapOf(
            ReferenceOption.CASCADE to 0,
            ReferenceOption.RESTRICT to 1,
            ReferenceOption.NO_ACTION to 1,
            ReferenceOption.SET_NULL to 2,
            ReferenceOption.SET_DEFAULT to 4
        )
    }

    override val arrayType: DataType
        get() = super.arrayType.copy(precision = "MAXIMUM_CARDINALITY")

    override val booleanType: DataType
        get() = super.booleanType.copy(precision = "1")

    // problematic: DOUBLE PRECISION used by both float and double with same size but different ids...
    override val additionalTypes: Set<DataType>
        get() = setOf(
            DataType("BINARY LARGE OBJECT", 2004, maxPrecision),
            DataType("BINARY VARYING", -3, characterPrecision),
            DataType("CHARACTER", 1, characterPrecision),
            DataType("CHARACTER LARGE OBJECT", 2005, maxPrecision),
            DataType("CHARACTER VARYING", 12, characterPrecision),
            DataType("JSON", 1111, characterPrecision),
            DataType("UUID", -2, "16"),
        )
}

class H2Metadata : MetadataProvider(H2PropertyProvider, H2TypeProvider) {
    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getReadOnlyMode(): String {
        return "CALL READONLY()"
    }

    // how to simulate doing nothing?
    override fun setReadOnlyMode(value: Boolean): String {
        return ""
    }

    override fun getCatalog(): String {
        return "SELECT CURRENT_CATALOG TABLE_CAT"
    }

    // how to simulate doing nothing?
    override fun setCatalog(value: String): String {
        return ""
    }

    override fun getSchema(): String {
        return "SELECT CURRENT_SCHEMA TABLE_SCHEM"
    }

    override fun setSchema(value: String): String {
        TODO("Not yet implemented")
    }

    override fun getCatalogs(): String {
        return buildString {
            append("SELECT DISTINCT CATALOG_NAME TABLE_CAT ")
            append("FROM INFORMATION_SCHEMA.SCHEMATA ")
            append("ORDER BY TABLE_CAT")
        }
    }

    override fun getSchemas(): String {
        return buildString {
            append("SELECT SCHEMA_NAME TABLE_SCHEM ")
            append("FROM INFORMATION_SCHEMA.SCHEMATA ")
            append("ORDER BY SCHEMA_NAME")
        }
    }

    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String): String {
        return buildString {
            append("SELECT TABLE_CATALOG TABLE_CAT, TABLE_SCHEMA TABLE_SCHEM, TABLE_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLES ")
            append("WHERE TABLE_NAME LIKE '$tableNamePattern' ")
            if (!catalog.isNullOrEmpty()) {
                append("AND TABLE_CATALOG LIKE '$catalog' ")
            }
            if (!schemaPattern.isNullOrEmpty()) {
                append("AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            }
            append("AND TABLE_TYPE = 'BASE TABLE' ")
            append("ORDER BY TABLE_CAT, TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getSequences(): String {
        return buildString {
            append("SELECT SEQUENCE_NAME ")
            append("FROM INFORMATION_SCHEMA.SEQUENCES ")
            append("ORDER BY SEQUENCE_NAME")
        }
    }

    override fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String {
        return buildString {
            append("SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_DEFAULT COLUMN_DEF, ")
            append("IS_IDENTITY IS_AUTOINCREMENT, COALESCE(NUMERIC_SCALE, DATETIME_PRECISION) DECIMAL_DIGITS, ")
            append("CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END NULLABLE, ")
            typeProvider.appendDataPrecisions("DATA_TYPE", "COLUMN_SIZE", this)
            append(", ")
            typeProvider.appendDataTypes("DATA_TYPE", "DATA_TYPE", this)
            append(" ")
            append("FROM INFORMATION_SCHEMA.COLUMNS ")
            append("WHERE TABLE_CATALOG LIKE '$catalog' AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME LIKE '$tableNamePattern' AND COLUMN_NAME LIKE '%' ")
            append("ORDER BY TABLE_NAME, ORDINAL_POSITION")
        }
    }

    override fun getPrimaryKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT ic.COLUMN_NAME COLUMN_NAME, tc.CONSTRAINT_NAME PK_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ")
            append("INNER JOIN INFORMATION_SCHEMA.INDEX_COLUMNS ic ")
            append("ON tc.INDEX_NAME = ic.INDEX_NAME AND tc.TABLE_NAME = ic.TABLE_NAME ")
            append("WHERE tc.TABLE_CATALOG LIKE '$catalog' AND tc.TABLE_SCHEMA LIKE '$schema' ")
            append("AND tc.TABLE_NAME LIKE '$table' AND tc.CONSTRAINT_TYPE = 'PRIMARY KEY' ")
            append("ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT INDEX_NAME, COLUMN_NAME, NULL FILTER_CONDITION, ORDINAL_POSITION, ")
            append("CASE WHEN IS_UNIQUE = FALSE THEN TRUE ELSE FALSE END NON_UNIQUE ")
            append("FROM INFORMATION_SCHEMA.INDEX_COLUMNS ")
            append("WHERE TABLE_CATALOG LIKE '$catalog' AND TABLE_SCHEMA LIKE '$schema' AND TABLE_NAME LIKE '$table' ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME, ORDINAL_POSITION")
        }
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT tc.TABLE_NAME FKTABLE_NAME, tc.CONSTRAINT_NAME FK_NAME, ")
            typeProvider.appendReferenceOptions("rc.UPDATE_RULE", "UPDATE_RULE", this)
            append(", ")
            typeProvider.appendReferenceOptions("rc.DELETE_RULE", "DELETE_RULE", this)
            append(", ")
            append("kcu.ORDINAL_POSITION, kcu.COLUMN_NAME FKCOLUMN_NAME, ")
            append("ccu.TABLE_SCHEMA PKTABLE_SCHEM, ccu.TABLE_NAME PKTABLE_NAME, ccu.COLUMN_NAME PKCOLUMN_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ")
            append("JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc ")
            append("ON tc.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME ")
            append("JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ")
            append("ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME ")
            append("JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu ")
            append("ON rc.UNIQUE_CONSTRAINT_SCHEMA = ccu.CONSTRAINT_SCHEMA AND rc.UNIQUE_CONSTRAINT_NAME = ccu.CONSTRAINT_NAME ")
            append("WHERE tc.TABLE_CATALOG LIKE '$catalog' AND tc.TABLE_SCHEMA LIKE '$schema' ")
            append("AND tc.TABLE_NAME LIKE '$table' AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY' ")
            append("ORDER BY PKTABLE_SCHEM, PKTABLE_NAME, FK_NAME, ORDINAL_POSITION")
        }
    }
}

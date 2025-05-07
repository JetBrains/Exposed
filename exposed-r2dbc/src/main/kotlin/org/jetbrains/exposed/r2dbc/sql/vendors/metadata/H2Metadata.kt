package org.jetbrains.exposed.r2dbc.sql.vendors.metadata

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.h2Mode

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
        val options = mutableMapOf(
            ReferenceOption.CASCADE to 0,
            ReferenceOption.SET_NULL to 2,
            ReferenceOption.SET_DEFAULT to 4
        )
        if (currentDialect.h2Mode in noActionPreferred) {
            options[ReferenceOption.NO_ACTION] = 1
            options[ReferenceOption.RESTRICT] = 1
        } else {
            options[ReferenceOption.RESTRICT] = 1
            options[ReferenceOption.NO_ACTION] = 1
        }
        options
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

    private val noActionPreferred = listOf(
        H2CompatibilityMode.SQLServer, H2CompatibilityMode.Oracle
    )
}

internal class H2Metadata : MetadataProvider(H2PropertyProvider, H2TypeProvider) {
    override fun getUsername(): String {
        return "SELECT CURRENT_USER AS USER_NAME"
    }

    override fun getReadOnlyMode(): String {
        return "SELECT READONLY() AS READ_ONLY"
    }

    // only possible with url ACCESS_DATA_MODE=r?
    override fun setReadOnlyMode(value: Boolean): String = ""

    override fun getDatabaseMode(): String {
        return buildString {
            append("SELECT SETTING_VALUE AS DB_MODE ")
            append("FROM INFORMATION_SCHEMA.SETTINGS ")
            append("WHERE SETTING_NAME = 'MODE'")
        }
    }

    override fun getCatalog(): String {
        return "SELECT CURRENT_CATALOG AS TABLE_CAT"
    }

    override fun setCatalog(value: String): String = ""

    override fun getSchema(): String {
        return "SELECT CURRENT_SCHEMA AS TABLE_SCHEM"
    }

    override fun getCatalogs(): String = ""

    override fun getSchemas(): String {
        return buildString {
            append("SELECT SCHEMA_NAME AS TABLE_SCHEM ")
            append("FROM INFORMATION_SCHEMA.SCHEMATA ")
            append("ORDER BY TABLE_SCHEM")
        }
    }

    override fun getTables(catalog: String, schemaPattern: String): String {
        return buildString {
            append("SELECT TABLE_CATALOG AS TABLE_CAT, TABLE_SCHEMA AS TABLE_SCHEM, TABLE_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLES ")
            append("WHERE TABLE_CATALOG = '$catalog' ")
            append("AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_TYPE = 'BASE TABLE' ")
            append("ORDER BY TABLE_CAT, TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getAllSequences(): String {
        return buildString {
            append("SELECT SEQUENCE_NAME ")
            append("FROM INFORMATION_SCHEMA.SEQUENCES ")
            append("ORDER BY SEQUENCE_NAME")
        }
    }

    override fun getSequences(catalog: String, schemaPattern: String, table: String): String = ""

    override fun getColumns(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT COLUMN_NAME, ")
            typeProvider.appendDataTypes("DATA_TYPE", "DATA_TYPE", this)
            append(", ")
            typeProvider.appendDataPrecisions("DATA_TYPE", "COLUMN_SIZE", this)
            append(", ")
            append("DATA_TYPE AS DATA_TYPE_OG, ")
            append("COALESCE(NUMERIC_SCALE, DATETIME_PRECISION) AS DECIMAL_DIGITS, ")
            append("CASE WHEN IS_NULLABLE = 'YES' THEN TRUE ELSE FALSE END AS NULLABLE, ")
            append("COLUMN_DEFAULT AS COLUMN_DEF, ORDINAL_POSITION, IS_IDENTITY AS IS_AUTOINCREMENT ")
            append("FROM INFORMATION_SCHEMA.COLUMNS ")
            append("WHERE TABLE_CATALOG = '$catalog' ")
            append("AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME = '$table' ")
            append("ORDER BY ORDINAL_POSITION")
        }
    }

    override fun getPrimaryKeys(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT ic.COLUMN_NAME AS COLUMN_NAME, tc.CONSTRAINT_NAME AS PK_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS AS tc ")
            append("INNER JOIN INFORMATION_SCHEMA.INDEX_COLUMNS AS ic ")
            append("ON tc.INDEX_NAME = ic.INDEX_NAME AND tc.TABLE_NAME = ic.TABLE_NAME ")
            append("WHERE tc.TABLE_CATALOG = '$catalog' ")
            append("AND tc.TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND tc.TABLE_NAME = '$table' ")
            append("AND tc.CONSTRAINT_TYPE = 'PRIMARY KEY' ")
            append("ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT CASE WHEN IS_UNIQUE = FALSE THEN TRUE ELSE FALSE END AS NON_UNIQUE, ")
            append("INDEX_NAME, ORDINAL_POSITION, COLUMN_NAME, NULL AS FILTER_CONDITION ")
            append("FROM INFORMATION_SCHEMA.INDEX_COLUMNS ")
            append("WHERE TABLE_CATALOG = '$catalog' ")
            append("AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME = '$table' ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME, ORDINAL_POSITION")
        }
    }

    override fun getImportedKeys(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT ccu.TABLE_NAME AS PKTABLE_NAME, ccu.COLUMN_NAME AS PKCOLUMN_NAME, ")
            append("tc.TABLE_NAME AS FKTABLE_NAME, kcu.COLUMN_NAME AS FKCOLUMN_NAME, ")
            append("kcu.ORDINAL_POSITION AS KEY_SEQ, ")
            typeProvider.appendReferenceOptions("rc.UPDATE_RULE", "UPDATE_RULE", this)
            append(", ")
            typeProvider.appendReferenceOptions("rc.DELETE_RULE", "DELETE_RULE", this)
            append(", ")
            append("tc.CONSTRAINT_NAME AS FK_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS AS tc ")
            append("JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS AS rc ")
            append("ON tc.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME ")
            append("JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE AS kcu ")
            append("ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME ")
            append("JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE AS ccu ")
            append("ON rc.UNIQUE_CONSTRAINT_SCHEMA = ccu.CONSTRAINT_SCHEMA AND rc.UNIQUE_CONSTRAINT_NAME = ccu.CONSTRAINT_NAME ")
            append("WHERE tc.TABLE_CATALOG = '$catalog' ")
            append("AND tc.TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND tc.TABLE_NAME = '$table' ")
            append("AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY' ")
            append("ORDER BY PKTABLE_NAME, KEY_SEQ")
        }
    }

    override fun getCheckConstraints(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT tc.CONSTRAINT_NAME AS CONSTRAINT_NAME, cc.CHECK_CLAUSE AS CHECK_CLAUSE ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS AS tc ")
            append("JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS AS cc ")
            append("ON tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME ")
            append("WHERE tc.CONSTRAINT_TYPE = 'CHECK' ")
            append("AND tc.TABLE_CATALOG = '$catalog' ")
            append("AND tc.TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND tc.TABLE_NAME = '$table' ")
            // TODO check & optimize
//            append("ORDER BY COLUMN_NAME")
        }
    }
}

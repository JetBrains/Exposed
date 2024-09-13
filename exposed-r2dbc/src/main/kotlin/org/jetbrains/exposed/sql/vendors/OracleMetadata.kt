package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.ReferenceOption

@Suppress("MagicNumber")
internal object OraclePropertyProvider : PropertyProvider() {
    override val storesUpperCaseIdentifiers: Boolean
        get() = true

    override val extraNameCharacters: String
        get() = "$#"

    override val supportsMultipleResultSets: Boolean
        get() = false

    override val maxColumnNameLength: Int
        get() = 128

    override fun sqlKeywords(): String {
        return "ACCESS,ADD,ALTER,AUDIT,CLUSTER,COLUMN,COMMENT,COMPRESS,CONNECT,DATE,DROP,EXCLUSIVE,FILE,IDENTIFIED,IMMEDIATE,INCREMENT," +
            "INDEX,INITIAL,INTERSECT,LEVEL,LOCK,LONG,MAXEXTENTS,MINUS,MODE,NOAUDIT,NOCOMPRESS,NOWAIT,NUMBER,OFFLINE,ONLINE,PCTFREE,PRIOR," +
            "all_PL_SQL_reserved_ words"
    }
}

@Suppress("MagicNumber")
internal object OracleTypeProvider : SqlTypeProvider() {
    override val referenceOptions: Map<ReferenceOption, Int> by lazy {
        mapOf(
            ReferenceOption.CASCADE to 0,
            ReferenceOption.RESTRICT to 1,
            ReferenceOption.NO_ACTION to 1,
            ReferenceOption.SET_NULL to 2,
            ReferenceOption.SET_DEFAULT to 1
        )
    }

    override val numericPrecision: String
        get() = "COALESCE(DATA_PRECISION, 38)"

    override val characterPrecision: String
        get() = "CHAR_LENGTH"

    override val blobType: DataType
        get() = super.blobType.copy(precision = "DATA_LENGTH")

    override val clobType: DataType
        get() = super.clobType.copy(precision = "DATA_LENGTH")

    override val dateType: DataType
        get() = super.dateType.copy(code = 93)

    override val additionalTypes: Set<DataType>
        get() = setOf(
            DataType("NUMBER", 2, numericPrecision),
            DataType("VARCHAR2", 12, characterPrecision),
            DataType("RAW", -3, "DATA_LENGTH"),
            DataType("TIMESTAMP(6)", 93, characterPrecision),
            DataType("TIMESTAMP(6) WITH TIME ZONE", -101, characterPrecision),
        )
}

class OracleMetadata : MetadataProvider(OraclePropertyProvider, OracleTypeProvider) {
    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getReadOnlyMode(): String {
        TODO("Not yet implemented")
    }

    override fun setReadOnlyMode(value: Boolean): String {
        TODO("Not yet implemented")
    }

    // what should be done to simulate retrieving an empty result set without pinging database?
    override fun getCatalog(): String {
        return "SELECT 1 AS TABLE_CAT FROM DUAL WHERE 1 = 0"
    }

    override fun setCatalog(value: String): String {
        TODO("Not yet implemented")
    }

    override fun getSchema(): String {
        return "SELECT SYS_CONTEXT('userenv','current_schema') AS TABLE_SCHEM FROM DUAL"
    }

    override fun setSchema(value: String): String {
        return "ALTER SESSION SET CURRENT_SCHEMA = $value"
    }

    // what should be done to simulate retrieving an empty result set without pinging database?
    override fun getCatalogs(): String {
        return "SELECT 1 AS TABLE_CAT FROM DUAL WHERE 1 = 0"
    }

    override fun getSchemas(): String {
        return buildString {
            append("SELECT USERNAME AS TABLE_SCHEM ")
            append("FROM ALL_USERS ")
            append("ORDER BY TABLE_SCHEM")
        }
    }

    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String): String {
        return buildString {
            append("SELECT NULL AS TABLE_CAT, OWNER AS TABLE_SCHEM, TABLE_NAME ")
            append("FROM ALL_TABLES ")
            append("WHERE TABLE_NAME LIKE '$tableNamePattern' ")
            if (!schemaPattern.isNullOrEmpty()) {
                append("AND OWNER LIKE '$schemaPattern' ")
            }
            append("ORDER BY TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getSequences(): String {
        return buildString {
            append("SELECT SEQUENCE_NAME ")
            append("FROM USER_SEQUENCES ")
            append("ORDER BY SEQUENCE_NAME")
        }
    }

    override fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String {
        return buildString {
            append("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_ID, DATA_DEFAULT AS COLUMN_DEF, ")
            append("CASE WHEN IDENTITY_COLUMN = 'YES' THEN 1 ELSE 0 END AS IS_AUTOINCREMENT, ")
            append("DATA_SCALE AS DECIMAL_DIGITS, ")
            append("CASE WHEN NULLABLE = 'Y' THEN 1 ELSE 0 END AS NULLABLE, ")
            typeProvider.appendDataPrecisions("DATA_TYPE", "COLUMN_SIZE", this)
            append(", ")
            typeProvider.appendDataTypes("DATA_TYPE", "DATA_TYPE", this)
            append(" ")
            append("FROM ALL_TAB_COLUMNS ")
            append("WHERE OWNER LIKE '$schemaPattern' ")
            append("AND TABLE_NAME LIKE '$tableNamePattern' AND COLUMN_NAME LIKE '%' ")
            append("ORDER BY TABLE_NAME, COLUMN_ID")
        }
    }

    override fun getPrimaryKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT acc.COLUMN_NAME AS COLUMN_NAME, ac.CONSTRAINT_NAME AS PK_NAME ")
            append("FROM ALL_CONSTRAINTS ac ")
            append("INNER JOIN ALL_CONS_COLUMNS acc ")
            append("ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER ")
            append("WHERE ac.OWNER LIKE '$schema' AND ac.TABLE_NAME LIKE '$table' ")
            append("AND ac.CONSTRAINT_TYPE = 'P' ")
            append("ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT ai.INDEX_NAME AS INDEX_NAME, aic.COLUMN_NAME AS COLUMN_NAME, ")
            append("NULL AS FILTER_CONDITION, aic.COLUMN_POSITION AS ORDINAL_POSITION, ")
            append("CASE WHEN ai.UNIQUENESS = 'NONUNIQUE' THEN 1 ELSE 0 END AS NON_UNIQUE ")
            append("FROM ALL_INDEXES ai ")
            append("INNER JOIN ALL_IND_COLUMNS aic ")
            append("ON ai.INDEX_NAME = aic.INDEX_NAME AND ai.OWNER = aic.INDEX_OWNER ")
            append("WHERE ai.OWNER LIKE '$schema' AND ai.TABLE_NAME LIKE '$table' ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME, ORDINAL_POSITION")
        }
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT ac.TABLE_NAME AS FKTABLE_NAME, ac.CONSTRAINT_NAME AS FK_NAME, NULL AS UPDATE_RULE, ")
            typeProvider.appendReferenceOptions("ac.DELETE_RULE", "DELETE_RULE", this)
            append(", ")
            append("acc.POSITION AS ORDINAL_POSITION, acc.COLUMN_NAME AS FKCOLUMN_NAME, ")
            append("ac.R_OWNER AS PKTABLE_SCHEM, acc_pk.TABLE_NAME AS PKTABLE_NAME, acc_pk.COLUMN_NAME AS PKCOLUMN_NAME ")
            append("FROM ALL_CONSTRAINTS ac ")
            append("JOIN ALL_CONS_COLUMNS acc ")
            append("ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER ")
            append("JOIN ALL_CONS_COLUMNS acc_pk ")
            append("ON ac.R_CONSTRAINT_NAME = acc_pk.CONSTRAINT_NAME AND ac.R_OWNER = acc_pk.OWNER ")
            append("WHERE ac.OWNER LIKE '$schema' AND ac.TABLE_NAME LIKE '$table' ")
            append("AND ac.CONSTRAINT_TYPE = 'R' ")
            append("ORDER BY PKTABLE_SCHEM, PKTABLE_NAME, FK_NAME, ORDINAL_POSITION")
        }
    }
}

package org.jetbrains.exposed.r2dbc.sql.vendors.metadata

import org.jetbrains.exposed.sql.ReferenceOption

@Suppress("MagicNumber")
internal object OraclePropertyProvider : PropertyProvider() {
    override val storesUpperCaseIdentifiers: Boolean
        get() = true

    override val extraNameCharacters: String
        get() = "$#"

    override val supportsAlterTableWithDropColumn: Boolean
        get() = false

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
            ReferenceOption.NO_ACTION to 1,
            ReferenceOption.RESTRICT to 1,
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

internal class OracleMetadata : MetadataProvider(OraclePropertyProvider, OracleTypeProvider) {
    override fun getUsername(): String {
        return buildString {
            append("SELECT SYS_CONTEXT('userenv','current_user') AS USER_NAME ")
            append("FROM DUAL")
        }
    }

    override fun getReadOnlyMode(): String = ""

    override fun setReadOnlyMode(value: Boolean): String {
        return if (value) {
            "SET TRANSACTION READ WRITE"
        } else {
            "SET TRANSACTION READ ONLY"
        }
    }

    override fun getDatabaseMode(): String = ""

    override fun getCatalog(): String = ""

    override fun setCatalog(value: String): String = ""

    override fun getSchema(): String {
        return buildString {
            append("SELECT SYS_CONTEXT('userenv','current_schema') AS TABLE_SCHEM ")
            append("FROM DUAL")
        }
    }

    override fun getCatalogs(): String = ""

    override fun getSchemas(): String {
        return buildString {
            append("SELECT USERNAME AS TABLE_SCHEM ")
            append("FROM ALL_USERS ")
            append("ORDER BY TABLE_SCHEM")
        }
    }

    override fun getTables(catalog: String, schemaPattern: String): String {
        return buildString {
            append("SELECT NULL AS TABLE_CAT, OWNER AS TABLE_SCHEM, TABLE_NAME ")
            append("FROM ALL_TABLES ")
            append("WHERE OWNER LIKE '$schemaPattern' ")
            append("ORDER BY TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getAllSequences(): String {
        return buildString {
            append("SELECT SEQUENCE_NAME ")
            append("FROM USER_SEQUENCES ")
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
            append("DATA_SCALE AS DECIMAL_DIGITS, ")
            append("CASE WHEN NULLABLE = 'Y' THEN 'TRUE' ELSE 'FALSE' END AS NULLABLE, ")
            append("DATA_DEFAULT AS COLUMN_DEF, COLUMN_ID AS ORDINAL_POSITION, ")
            append("CASE WHEN IDENTITY_COLUMN = 'YES' THEN 'TRUE' ELSE 'FALSE' END AS IS_AUTOINCREMENT ")
            append("FROM ALL_TAB_COLUMNS ")
            append("WHERE OWNER LIKE '$schemaPattern' ")
            append("AND TABLE_NAME = '$table' ")
            append("ORDER BY ORDINAL_POSITION")
        }
    }

    override fun getPrimaryKeys(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT acc.COLUMN_NAME AS COLUMN_NAME, ac.CONSTRAINT_NAME AS PK_NAME ")
            append("FROM ALL_CONSTRAINTS ac ")
            append("INNER JOIN ALL_CONS_COLUMNS acc ")
            append("ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER AND ac.TABLE_NAME = acc.TABLE_NAME ")
            append("WHERE ac.OWNER LIKE '$schemaPattern' ")
            append("AND ac.TABLE_NAME = '$table' ")
            append("AND ac.CONSTRAINT_TYPE = 'P' ")
            append("ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT CASE WHEN ai.UNIQUENESS = 'NONUNIQUE' THEN 'TRUE' ELSE 'FALSE' END AS NON_UNIQUE, ")
            append("ai.INDEX_NAME AS INDEX_NAME, aic.COLUMN_POSITION AS ORDINAL_POSITION, ")
            append("aic.COLUMN_NAME AS COLUMN_NAME, NULL AS FILTER_CONDITION ")
            append("FROM ALL_INDEXES ai ")
            append("INNER JOIN ALL_IND_COLUMNS aic ")
            append("ON ai.INDEX_NAME = aic.INDEX_NAME AND ai.OWNER = aic.INDEX_OWNER AND ai.TABLE_NAME = aic.TABLE_NAME ")
            append("WHERE ai.OWNER LIKE '$schemaPattern' ")
            append("AND ai.TABLE_NAME = '$table' ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME, ORDINAL_POSITION")
        }
    }

    override fun getImportedKeys(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT acc_pk.TABLE_NAME AS PKTABLE_NAME, acc_pk.COLUMN_NAME AS PKCOLUMN_NAME, ")
            append("ac.TABLE_NAME AS FKTABLE_NAME, acc.COLUMN_NAME AS FKCOLUMN_NAME, ")
            append("acc.POSITION AS KEY_SEQ, NULL AS UPDATE_RULE, ")
            typeProvider.appendReferenceOptions("ac.DELETE_RULE", "DELETE_RULE", this)
            append(", ")
            append("ac.CONSTRAINT_NAME AS FK_NAME ")
            append("FROM ALL_CONSTRAINTS ac ")
            append("JOIN ALL_CONS_COLUMNS acc ")
            append("ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER ")
            append("JOIN ALL_CONS_COLUMNS acc_pk ")
            append("ON ac.R_CONSTRAINT_NAME = acc_pk.CONSTRAINT_NAME AND ac.R_OWNER = acc_pk.OWNER ")
            append("WHERE ac.OWNER LIKE '$schemaPattern' ")
            append("AND ac.TABLE_NAME = '$table' ")
            append("AND ac.CONSTRAINT_TYPE = 'R' ")
            append("ORDER BY PKTABLE_NAME, KEY_SEQ")
        }
    }
}

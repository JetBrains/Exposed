package org.jetbrains.exposed.v1.r2dbc.sql.vendors.metadata

import org.jetbrains.exposed.v1.sql.ReferenceOption

@Suppress("MagicNumber")
internal object SQLServerPropertyProvider : PropertyProvider() {
    override val supportsMixedCaseIdentifiers: Boolean
        get() = true

    override val extraNameCharacters: String
        get() = "$#@"

    override val supportsSelectForUpdate: Boolean
        get() = false

    override val maxColumnNameLength: Int
        get() = 128

    override fun sqlKeywords(): String {
        return "ADD,ALL,ALTER,AND,ANY,AS,ASC,AUTHORIZATION,BACKUP,BEGIN,BETWEEN,BREAK,BROWSE,BULK,BY,CASCADE,CASE,CHECK,CHECKPOINT," +
            "CLOSE,CLUSTERED,COALESCE,COLLATE,COLUMN,COMMIT,COMPUTE,CONSTRAINT,CONTAINS,CONTAINSTABLE,CONTINUE,CONVERT,CREATE,CROSS,CURRENT," +
            "CURRENT_DATE,CURRENT_TIME,CURRENT_TIMESTAMP,CURRENT_USER,CURSOR,DATABASE,DBCC,DEALLOCATE,DECLARE,DEFAULT,DELETE,DENY,DESC," +
            "DISK,DISTINCT,DISTRIBUTED,DOUBLE,DROP,DUMP,ELSE,END,ERRLVL,ESCAPE,EXCEPT,EXEC,EXECUTE,EXISTS,EXIT,EXTERNAL,FETCH,FILE,FILLFACTOR," +
            "FOR,FOREIGN,FREETEXT,FREETEXTTABLE,FROM,FULL,FUNCTION,GOTO,GRANT,GROUP,HAVING,HOLDLOCK,IDENTITY,IDENTITY_INSERT,IDENTITYCOL,IF,IN," +
            "INDEX,INNER,INSERT,INTERSECT,INTO,IS,JOIN,KEY,KILL,LEFT,LIKE,LINENO,LOAD,MERGE,NATIONAL,NOCHECK,NONCLUSTERED,NOT,NULL,NULLIF,OF,OFF," +
            "OFFSETS,ON,OPEN,OPENDATASOURCE,OPENQUERY,OPENROWSET,OPENXML,OPTION,OR,ORDER,OUTER,OVER,PERCENT,PIVOT,PLAN,PRECISION,PRIMARY," +
            "PRINT,PROC,PROCEDURE,PUBLIC,RAISERROR,READ,READTEXT,RECONFIGURE,REFERENCES,REPLICATION,RESTORE,RESTRICT,RETURN,REVERT,REVOKE,RIGHT," +
            "ROLLBACK,ROWCOUNT,ROWGUIDCOL,RULE,SAVE,SCHEMA,SECURITYAUDIT,SELECT,SEMANTICKEYPHRASETABLE,SEMANTICSIMILARITYDETAILSTABLE,SEMANTICSIMILARITYTABLE," +
            "SESSION_USER,SET,SETUSER,SHUTDOWN,SOME,STATISTICS,SYSTEM_USER,TABLE,TABLESAMPLE,TEXTSIZE,THEN,TO,TOP,TRAN,TRANSACTION,TRIGGER," +
            "TRUNCATE,TRY_CONVERT,TSEQUAL,UNION,UNIQUE,UNPIVOT,UPDATE,UPDATETEXT,USE,USER,VALUES,VARYING,VIEW,WAITFOR,WHEN,WHERE,WHILE,WITH,WITHIN GROUP,WRITETEXT"
    }
}

@Suppress("MagicNumber")
internal object SQLServerTypeProvider : SqlTypeProvider() {
    override val referenceOptions: Map<ReferenceOption, Int> by lazy {
        mapOf(
            ReferenceOption.CASCADE to 0,
            ReferenceOption.NO_ACTION to 3,
            ReferenceOption.RESTRICT to 3,
            ReferenceOption.SET_NULL to 2,
            ReferenceOption.SET_DEFAULT to 4
        )
    }

    override val bitType: DataType
        get() = super.bitType.copy(precision = "1")

    override val floatType: DataType
        get() = super.floatType.copy(code = 8)

    override val timeType: DataType
        get() = super.timeType.copy(precision = "16")

    override val varbinaryType: DataType
        get() = super.varbinaryType.copy(precision = "COALESCE(NULLIF($characterPrecision,-1),$maxPrecision)")

    override val varcharType: DataType
        get() = super.varcharType.copy(precision = "COALESCE(NULLIF($characterPrecision,-1),$maxPrecision)")

    override val additionalTypes: Set<DataType>
        get() = setOf(
            DataType("INT", 4, numericPrecision),
            DataType("DATETIME2", 93, "27"),
            DataType("DATETIMEOFFSET", -155, "34"),
            DataType("UNIQUEIDENTIFIER", 1, "36"),
        )
}

internal class SQLServerMetadata : MetadataProvider(SQLServerPropertyProvider, SQLServerTypeProvider) {
    override fun getUsername(): String {
        return "SELECT SUSER_NAME() AS USER_NAME"
    }

    override fun getReadOnlyMode(): String {
        return "SELECT DATABASEPROPERTYEX(DB_NAME(), 'Updateability') AS READ_ONLY"
    }

    override fun setReadOnlyMode(value: Boolean): String {
        return if (value) {
            "ALTER DATABASE CURRENT SET READ_WRITE"
        } else {
            "ALTER DATABASE CURRENT SET READ_ONLY"
        }
    }

    override fun getDatabaseMode(): String = ""

    override fun getCatalog(): String {
        return "SELECT DB_NAME() AS TABLE_CAT"
    }

    override fun setCatalog(value: String): String = ""

    override fun getSchema(): String {
        return "SELECT SCHEMA_NAME() AS TABLE_SCHEM"
    }

    override fun getCatalogs(): String = ""

    override fun getSchemas(): String {
        return buildString {
            append("SELECT name AS TABLE_SCHEM ")
            append("FROM sys.schemas ")
            append("ORDER BY TABLE_SCHEM")
        }
    }

    override fun getTables(catalog: String, schemaPattern: String): String {
        return buildString {
            append("SELECT NULL AS TABLE_CAT, SCHEMA_NAME(schema_id) AS TABLE_SCHEM, name AS TABLE_NAME ")
            append("FROM sys.tables ")
            append("WHERE SCHEMA_NAME(schema_id) LIKE '$schemaPattern' ")
            append("AND type = 'U' ")
            append("ORDER BY TABLE_SCHEM, TABLE_NAME")
        }
    }

    override fun getAllSequences(): String {
        return buildString {
            append("SELECT name AS SEQUENCE_NAME ")
            append("FROM sys.sequences ")
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
            append("NUMERIC_SCALE AS DECIMAL_DIGITS, ")
            append("CASE WHEN IS_NULLABLE = 'YES' THEN 'TRUE' ELSE 'FALSE' END AS NULLABLE, ")
            append("COLUMN_DEFAULT AS COLUMN_DEF, ORDINAL_POSITION, ")
            append("CASE WHEN ")
            append("COLUMNPROPERTY(OBJECT_ID(TABLE_SCHEMA + '.' + TABLE_NAME), COLUMN_NAME, 'IsIdentity') = 1 THEN 'YES' ELSE 'NO' ")
            append("END AS IS_AUTOINCREMENT ")
            append("FROM INFORMATION_SCHEMA.COLUMNS ")
            append("WHERE TABLE_CATALOG = '$catalog' ")
            append("AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME = '$table' ")
            append("ORDER BY ORDINAL_POSITION")
        }
    }

    override fun getPrimaryKeys(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT ccu.COLUMN_NAME AS COLUMN_NAME, tc.CONSTRAINT_NAME AS PK_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS AS tc ")
            append("INNER JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE AS ccu ")
            append("ON tc.CONSTRAINT_NAME = ccu.CONSTRAINT_NAME AND tc.TABLE_NAME = ccu.TABLE_NAME ")
            append("WHERE tc.TABLE_CATALOG = '$catalog' ")
            append("AND tc.TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND tc.TABLE_NAME = '$table' ")
            append("AND tc.CONSTRAINT_TYPE = 'PRIMARY KEY' ")
            append("ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT CASE WHEN i.is_unique = 0 THEN 'TRUE' ELSE 'FALSE' END AS NON_UNIQUE, ")
            append("i.name AS INDEX_NAME, ic.key_ordinal AS ORDINAL_POSITION, ")
            append("COL_NAME(ic.object_id, ic.column_id) AS COLUMN_NAME, i.filter_definition AS FILTER_CONDITION ")
            append("FROM sys.indexes AS i ")
            append("INNER JOIN sys.index_columns AS ic ")
            append("ON i.object_id = ic.object_id AND i.index_id = ic.index_id ")
            append("WHERE i.object_id = OBJECT_ID('$table') ")
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

    override fun getCheckConstraints(catalog: String, schemaPattern: String, table: String): String = ""
}

package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.ReferenceOption

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
            ReferenceOption.RESTRICT to 3,
            ReferenceOption.NO_ACTION to 3,
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

class SQLServerMetadata : MetadataProvider(SQLServerPropertyProvider, SQLServerTypeProvider) {
    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getReadOnlyMode(): String {
        return "SELECT false"
    }

    override fun setReadOnlyMode(value: Boolean): String {
        TODO("Not yet implemented")
    }

    override fun getCatalog(): String {
        return "SELECT DB_NAME() AS TABLE_CAT"
    }

    override fun setCatalog(value: String): String {
        TODO("Not yet implemented")
    }

    override fun getSchema(): String {
        return "SELECT SCHEMA_NAME() AS TABLE_SCHEM"
    }

    override fun setSchema(value: String): String {
        return "ALTER USER WITH DEFAULT_SCHEMA = $value"
    }

    override fun getCatalogs(): String {
        return buildString {
            append("SELECT name AS TABLE_CAT ")
            append("FROM sys.databases ")
            append("ORDER BY TABLE_CAT")
        }
    }

    override fun getSchemas(): String {
        return buildString {
            append("SELECT sys.schemas.name AS TABLE_SCHEM ")
            append("FROM sys.schemas ")
            append("ORDER BY 1")
        }
    }

    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String): String {
        return buildString {
            append("SELECT TABLE_CATALOG AS TABLE_CAT, TABLE_SCHEMA AS TABLE_SCHEM, TABLE_NAME ")
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
            append("SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_DEFAULT AS COLUMN_DEF, ")
            append("CASE WHEN COLUMNPROPERTY(OBJECT_ID(TABLE_NAME), COLUMN_NAME, 'IsIdentity') = 1 THEN 'YES' ELSE 'NO' END AS IS_AUTOINCREMENT, ")
            append("NUMERIC_SCALE AS DECIMAL_DIGITS, ")
            append("CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END AS NULLABLE, ")
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
            append("SELECT ccu.COLUMN_NAME AS COLUMN_NAME, tc.CONSTRAINT_NAME AS PK_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ")
            append("INNER JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu ")
            append("ON ccu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND ccu.TABLE_NAME = tc.TABLE_NAME ")
            append("WHERE tc.TABLE_CATALOG = '$catalog' AND tc.TABLE_SCHEMA = '$schema' ")
            append("AND tc.TABLE_NAME = '$table' AND tc.CONSTRAINT_TYPE = 'PRIMARY KEY' ")
            append("ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT tc.CONSTRAINT_NAME AS INDEX_NAME, ccu.COLUMN_NAME AS COLUMN_NAME, NULL AS FILTER_CONDITION, ")
            append("CASE WHEN tc.CONSTRAINT_TYPE = 'UNIQUE' THEN 0 WHEN tc.CONSTRAINT_TYPE = 'PRIMARY KEY' THEN 0 ELSE 1 END AS NON_UNIQUE ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ")
            append("INNER JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu ")
            append("ON ccu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND ccu.TABLE_NAME = tc.TABLE_NAME ")
            append("WHERE tc.TABLE_CATALOG = '$catalog' AND tc.TABLE_SCHEMA = '$schema' ")
            append("AND tc.TABLE_NAME = '$table' AND tc.CONSTRAINT_TYPE <> 'FOREIGN KEY' ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME")
        }
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT tc.TABLE_NAME AS FKTABLE_NAME, tc.CONSTRAINT_NAME AS FK_NAME, ")
            typeProvider.appendReferenceOptions("rc.UPDATE_RULE", "UPDATE_RULE", this)
            append(", ")
            typeProvider.appendReferenceOptions("rc.DELETE_RULE", "DELETE_RULE", this)
            append(", ")
            append("kcu.ORDINAL_POSITION, kcu.COLUMN_NAME AS FKCOLUMN_NAME, ")
            append("ccu.TABLE_SCHEMA AS PKTABLE_SCHEM, ccu.TABLE_NAME AS PKTABLE_NAME, ccu.COLUMN_NAME AS PKCOLUMN_NAME ")
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

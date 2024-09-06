package org.jetbrains.exposed.sql.vendors

import io.r2dbc.spi.IsolationLevel

class SQLServerMetadata : MetadataProvider {
    override fun identifierQuoteString(): String = "\""

    override fun storesUpperCaseIdentifiers(): Boolean = false

    override fun storesUpperCaseQuotedIdentifiers(): Boolean = false

    override fun storesLowerCaseIdentifiers(): Boolean = false

    override fun storesLowerCaseQuotedIdentifiers(): Boolean = false

    override fun supportsMixedCaseIdentifiers(): Boolean = true

    override fun supportsMixedCaseQuotedIdentifiers(): Boolean = true

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

    override fun extraNameCharacters(): String = "$#@"

    override fun maxColumnNameLength(): Int = 128

    override fun supportsAlterTableWithAddColumn(): Boolean = true

    override fun supportsMultipleResultSets(): Boolean = true

    override fun supportsSelectForUpdate(): Boolean = false

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
        return "SELECT DB_NAME() AS CURRENT_CATALOG"
    }

    override fun getSchema(): String {
        return "SELECT SCHEMA_NAME()"
    }

    override fun setSchema(value: String): String {
        return "ALTER USER WITH DEFAULT_SCHEMA = $value"
    }

    override fun getCatalogs(): String {
        return "SELECT name AS TABLE_CAT FROM sys.databases order by name"
    }

    override fun getSchemas(): String {
        return "SELECT sys.schemas.name 'TABLE_SCHEM' FROM sys.schemas order by 1"
    }

    override fun getReadOnlyMode(): String {
        return "SELECT false"
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

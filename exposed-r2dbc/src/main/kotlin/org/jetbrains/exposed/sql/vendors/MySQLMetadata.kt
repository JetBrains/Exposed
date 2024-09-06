package org.jetbrains.exposed.sql.vendors

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal

open class MySQLMetadata : MetadataProvider {
    override fun identifierQuoteString(): String = "`"

    override fun storesUpperCaseIdentifiers(): Boolean = false

    override fun storesUpperCaseQuotedIdentifiers(): Boolean = true

    override fun storesLowerCaseIdentifiers(): Boolean = false

    override fun storesLowerCaseQuotedIdentifiers(): Boolean = false

    override fun supportsMixedCaseIdentifiers(): Boolean = true

    override fun supportsMixedCaseQuotedIdentifiers(): Boolean = true

    // SELECT WORD FROM INFORMATION_SCHEMA.KEYWORDS WHERE RESERVED - returns different???
    override fun sqlKeywords(): String {
        return "ACCESSIBLE,ADD,ANALYZE,ASC,BEFORE,CASCADE,CHANGE,CONTINUE,DATABASE,DATABASES,DAY_HOUR,DAY_MICROSECOND,DAY_MINUTE," +
            "DAY_SECOND,DELAYED,DESC,DISTINCTROW,DIV,DUAL,ELSEIF,EMPTY,ENCLOSED,ESCAPED,EXIT,EXPLAIN,FIRST_VALUE,FLOAT4,FLOAT8,FORCE," +
            "FULLTEXT,GENERATED,GROUPS,HIGH_PRIORITY,HOUR_MICROSECOND,HOUR_MINUTE,HOUR_SECOND,IF,IGNORE,INDEX,INFILE,INT1,INT2,INT3," +
            "INT4,INT8,IO_AFTER_GTIDS,IO_BEFORE_GTIDS,ITERATE,JSON_TABLE,KEY,KEYS,KILL,LAG,LAST_VALUE,LEAD,LEAVE,LIMIT,LINEAR,LINES," +
            "LOAD,LOCK,LONG,LONGBLOB,LONGTEXT,LOOP,LOW_PRIORITY,MASTER_BIND,MASTER_SSL_VERIFY_SERVER_CERT,MAXVALUE,MEDIUMBLOB,MEDIUMINT," +
            "MEDIUMTEXT,MIDDLEINT,MINUTE_MICROSECOND,MINUTE_SECOND,NO_WRITE_TO_BINLOG,NTH_VALUE,NTILE,OPTIMIZE,OPTIMIZER_COSTS,OPTION," +
            "OPTIONALLY,OUTFILE,PURGE,READ,READ_WRITE,REGEXP,RENAME,REPEAT,REPLACE,REQUIRE,RESIGNAL,RESTRICT,RLIKE,SCHEMA,SCHEMAS," +
            "SECOND_MICROSECOND,SEPARATOR,SHOW,SIGNAL,SPATIAL,SQL_BIG_RESULT,SQL_CALC_FOUND_ROWS,SQL_SMALL_RESULT,SSL,STARTING,STORED," +
            "STRAIGHT_JOIN,TERMINATED,TINYBLOB,TINYINT,TINYTEXT,UNDO,UNLOCK,UNSIGNED,USAGE,USE,UTC_DATE,UTC_TIME,UTC_TIMESTAMP,VARBINARY," +
            "VARCHARACTER,VIRTUAL,WHILE,WRITE,XOR,YEAR_MONTH,ZEROFILL"
    }

    override fun extraNameCharacters(): String = "#@"

    override fun maxColumnNameLength(): Int = 64

    override fun supportsAlterTableWithAddColumn(): Boolean = true

    override fun supportsMultipleResultSets(): Boolean = true

    override fun supportsSelectForUpdate(): Boolean = true

    // will maintenance across/between versions be required too?
    override fun getDefaultTransactionIsolation(): IsolationLevel = if (TransactionManager.current().db.isVersionCovers(BigDecimal("6.0"))) {
        IsolationLevel.REPEATABLE_READ
    } else {
        IsolationLevel.READ_COMMITTED
    }

    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getCatalog(): String {
        return "SELECT DATABASE()"
    }

    override fun setCatalog(value: String): String {
        return "USE $value"
    }

    override fun getSchema(): String {
        return "SELECT DATABASE()"
    }

    override fun setSchema(value: String): String {
        return "USE $value"
    }

    override fun getCatalogs(): String {
        return "SELECT SCHEMA_NAME TABLE_CAT FROM INFORMATION_SCHEMA.SCHEMATA ORDER BY SCHEMA_NAME"
    }

    override fun getSchemas(): String {
        return "SELECT SCHEMA_NAME TABLE_SCHEM FROM INFORMATION_SCHEMA.SCHEMATA ORDER BY SCHEMA_NAME"
    }

    override fun getReadOnlyMode(): String {
        return "SHOW VARIABLES LIKE 'read_only'"
    }

    override fun setReadOnlyMode(value: Boolean): String {
        TODO("Not yet implemented")
    }

    // how does sequences translate over?
    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String?, type: String): String {
        return buildString {
            append("SELECT TABLE_SCHEMA as TABLE_CAT, null as TABLE_SCHEM, TABLE_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLES WHERE TRUE ")
            if (!catalog.isNullOrEmpty()) {
                append("AND TABLE_SCHEMA LIKE '$catalog' ")
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
            append("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, NUMERIC_SCALE COLUMN_SIZE,")
            append("NUMERIC_PRECISION DECIMAL_DIGITS, IS_NULLABLE NULLABLE, COLUMN_DEFAULT COLUMN_DEF, ")
            append("CASE WHEN EXTRA = 'auto_increment' THEN CAST(YES AS VARCHAR) ELSE CAST(NO AS VARCHAR) END IS_AUTOINCREMENT ")
            append("FROM INFORMATION_SCHEMA.COLUMNS ")
            append("WHERE TABLE_CATALOG LIKE '$catalog' AND TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME LIKE '$tableNamePattern' AND COLUMN_NAME LIKE '%') ")
            append("ORDER BY TABLE_NAME")
        }
    }

    override fun getPrimaryKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT COLUMN_NAME, COALESCE(INDEX_NAME, COLUMN_NAME) PK_NAME ")
            append("FROM INFORMATION_SCHEMA.STATISTICS ")
            append("WHERE TABLE_CATALOG LIKE $catalog AND TABLE_SCHEMA LIKE $schema AND TABLE_NAME = $table ")
            append("AND INDEX_NAME = 'PRIMARY' ORDER BY COLUMN_NAME")
        }
        //        return buildString {
//            append("SELECT c.COLUMN_NAME, tc.CONSTRAINT_NAME PK_NAME ")
//            append("FROM INFORMATION_SCHEMA.COLUMNS c INNER JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ")
//            append("ON c.TABLE_SCHEMA = tc.CONSTRAINT_SCHEMA AND c.TABLE_NAME = tc.TABLE_NAME ")
//            append("WHERE c.TABLE_SCHEMA = '$schema' AND tc.CONSTRAINT_SCHEMA = '$schema' ")
//            append("AND tc.TABLE_SCHEMA = '$schema' AND tc.TABLE_NAME = '$table' ")
//            append("AND tc.CONSTRAINT_TYPE = 'PRIMARY KEY' ORDER BY COLUMN_NAME")
//        }
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT NON_UNIQUE, INDEX_NAME, COLUMN_NAME, FILTER_CONDITION ")
            append("FROM INFORMATION_SCHEMA.STATISTICS ")
            append("WHERE TABLE_CATALOG = '$catalog' AND TABLE_SCHEMA = '$schema' AND TABLE_NAME = '$table' ")
            append("AND (NON_UNIQUE=1) ORDER BY NON_UNIQUE, INDEX_NAME")
        }
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT rc.CONSTRAINT_NAME,= ku.TABLE_NAME,= ku.COLUMN_NAME, ku.REFERENCED_TABLE_NAME, ")
            append("ku.REFERENCED_COLUMN_NAME, rc.UPDATE_RULE, rc.DELETE_RULE ")
            append("FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku ")
            append("ON ku.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME ")
            append("WHERE ku.TABLE_SCHEMA = '$schema' AND ku.CONSTRAINT_SCHEMA = '$schema' ")
            append("AND rc.CONSTRAINT_SCHEMA = '$schema' AND ku.TABLE_NAME = '$table' ORDER BY ku.ORDINAL_POSITION")
        }
    }
}

package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.ReferenceOption

internal object MariaDBPropertyProvider : MySQLPropertyProvider() {
    override val storesUpperCaseQuotedIdentifiers: Boolean
        get() = false

    override fun sqlKeywords(): String {
        return "ACCESSIBLE,ANALYZE,ASENSITIVE,BEFORE,BIGINT,BINARY,BLOB,CALL,CHANGE,CONDITION,DATABASE,DATABASES,DAY_HOUR," +
            "DAY_MICROSECOND,DAY_MINUTE,DAY_SECOND,DELAYED,DETERMINISTIC,DISTINCTROW,DIV,DUAL,EACH,ELSEIF,ENCLOSED,ESCAPED,EXIT," +
            "EXPLAIN,FLOAT4,FLOAT8,FORCE,FULLTEXT,GENERAL,HIGH_PRIORITY,HOUR_MICROSECOND,HOUR_MINUTE,HOUR_SECOND,IF,IGNORE,IGNORE_SERVER_IDS," +
            "INDEX,INFILE,INOUT,INT1,INT2,INT3,INT4,INT8,ITERATE,KEY,KEYS,KILL,LEAVE,LIMIT,LINEAR,LINES,LOAD,LOCALTIME,LOCALTIMESTAMP,LOCK," +
            "LONG,LONGBLOB,LONGTEXT,LOOP,LOW_PRIORITY,MASTER_HEARTBEAT_PERIOD,MASTER_SSL_VERIFY_SERVER_CERT,MAXVALUE,MEDIUMBLOB,MEDIUMINT," +
            "MEDIUMTEXT,MIDDLEINT,MINUTE_MICROSECOND,MINUTE_SECOND,MOD,MODIFIES,NO_WRITE_TO_BINLOG,OPTIMIZE,OPTIONALLY,OUT,OUTFILE,PURGE," +
            "RANGE,READ_WRITE,READS,REGEXP,RELEASE,RENAME,REPEAT,REPLACE,REQUIRE,RESIGNAL,RESTRICT,RETURN,RLIKE,SCHEMAS,SECOND_MICROSECOND," +
            "SENSITIVE,SEPARATOR,SHOW,SIGNAL,SLOW,SPATIAL,SPECIFIC,SQL_BIG_RESULT,SQL_CALC_FOUND_ROWS,SQL_SMALL_RESULT,SQLEXCEPTION,SSL,STARTING," +
            "STRAIGHT_JOIN,TERMINATED,TINYBLOB,TINYINT,TINYTEXT,TRIGGER,UNDO,UNLOCK,UNSIGNED,USE,UTC_DATE,UTC_TIME,UTC_TIMESTAMP,VARBINARY," +
            "VARCHARACTER,WHILE,XOR,YEAR_MONTH,ZEROFILL"
    }
}

internal object MariaDBTypeProvider : MySQLTypeProvider() {
    override val referenceOptions: Map<ReferenceOption, Int> by lazy {
        mapOf(
            ReferenceOption.CASCADE to 0,
            ReferenceOption.RESTRICT to 1,
            ReferenceOption.NO_ACTION to 1,
            ReferenceOption.SET_NULL to 2,
            ReferenceOption.SET_DEFAULT to 1
        )
    }

    override val bitType: DataType
        get() = super.bitType.copy(precision = numericPrecision)

    // problematic: used for both byte and bool types but returns different ids...
    override val tinyIntType: DataType
        get() = super.tinyIntType.copy(code = -7, precision = numericPrecision)
}

class MariaDBMetadata : MySQLMetadata() {
    override val propertyProvider: PropertyProvider = MariaDBPropertyProvider

    override val typeProvider: SqlTypeProvider = MariaDBTypeProvider

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT INDEX_NAME, COLUMN_NAME, NULL FILTER_CONDITION, NON_UNIQUE ")
            append("FROM INFORMATION_SCHEMA.STATISTICS ")
            append("WHERE TABLE_SCHEMA = '$schema' AND TABLE_NAME = '$table' ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME")
        }
    }
}

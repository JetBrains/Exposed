package org.jetbrains.exposed.sql.vendors

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal

@Suppress("MagicNumber")
internal open class MySQLPropertyProvider : PropertyProvider() {
    override val identifierQuoteString: String
        get() = "`"

    override val storesUpperCaseQuotedIdentifiers: Boolean
        get() = true

    override val supportsMixedCaseIdentifiers: Boolean
        get() = true

    override val extraNameCharacters: String
        get() = "#@"

    override val defaultTransactionIsolation: IsolationLevel
        get() = if (isMySQL6Plus) {
            IsolationLevel.REPEATABLE_READ
        } else {
            IsolationLevel.READ_COMMITTED
        }

    override val maxColumnNameLength: Int
        get() = 64

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
}

@Suppress("MagicNumber")
internal open class MySQLTypeProvider : SqlTypeProvider() {
    override val referenceOptions: Map<ReferenceOption, Int> by lazy {
        val options = mutableMapOf(
            ReferenceOption.CASCADE to 0,
            ReferenceOption.SET_NULL to 2
        )
        if (isMySQL6Plus) {
            options[ReferenceOption.SET_DEFAULT] = 1
            options[ReferenceOption.RESTRICT] = 1
            options[ReferenceOption.NO_ACTION] = 1
        } else {
            options[ReferenceOption.SET_DEFAULT] = 3
            options[ReferenceOption.RESTRICT] = 3
            options[ReferenceOption.NO_ACTION] = 3
        }
        options
    }

    override val bitType: DataType
        get() = super.bitType.copy(precision = if (isMySQL6Plus) "1" else "0")

    override val blobType: DataType
        get() = super.blobType.copy(code = -4)

    override val floatType: DataType
        get() = super.floatType.copy(code = 7)

    // problematic: used for both byte and bool types but returns different ids...
    override val tinyIntType: DataType
        get() = super.tinyIntType.copy(code = -7, precision = if (isMySQL6Plus) "1" else "0")

    override val additionalTypes: Set<DataType>
        get() = setOf(
            DataType("BIGINT UNSIGNED", -5, numericPrecision),
            DataType("DATETIME", 93, "26"),
            DataType("INT", 4, numericPrecision),
            DataType("INT UNSIGNED", 4, numericPrecision),
            DataType("JSON", -1, "1073741824"),
            DataType("LONGTEXT", -1, maxPrecision),
            DataType("MEDIUMTEXT", -1, characterPrecision),
            DataType("SMALLINT UNSIGNED", 5, numericPrecision),
            DataType("TEXT", -1, characterPrecision),
            DataType("TINYINT UNSIGNED", -6, numericPrecision),
        )
}

open class MySQLMetadata : MetadataProvider(MySQLPropertyProvider(), MySQLTypeProvider()) {
    override fun getUrl(): String {
        TODO("Not yet implemented")
    }

    override fun getUsername(): String {
        TODO("Not yet implemented")
    }

    override fun getReadOnlyMode(): String {
        return "SHOW VARIABLES LIKE 'read_only'"
    }

    override fun setReadOnlyMode(value: Boolean): String {
        TODO("Not yet implemented")
    }

    override fun getCatalog(): String {
        return "SELECT DATABASE() TABLE_CAT"
    }

    override fun setCatalog(value: String): String {
        return "USE $value"
    }

    override fun getSchema(): String {
        return "SELECT NULL TABLE_SCHEM"
    }

    override fun setSchema(value: String): String {
        return "USE $value"
    }

    override fun getCatalogs(): String {
        return buildString {
            append("SELECT DISTINCT SCHEMA_NAME TABLE_CAT ")
            append("FROM INFORMATION_SCHEMA.SCHEMATA ")
            append("ORDER BY TABLE_CAT")
        }
    }

    // what should be done to simulate retrieving an empty result set without pinging database?
    // this should never be used with MySQL/MariaDB
    override fun getSchemas(): String {
        return "SELECT 1 TABLE_SCHEM FROM DUAL WHERE FALSE"
    }

    override fun getTables(catalog: String?, schemaPattern: String?, tableNamePattern: String): String {
        return buildString {
            append("SELECT TABLE_SCHEMA TABLE_CAT, NULL TABLE_SCHEM, TABLE_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLES ")
            append("WHERE TABLE_NAME LIKE '$tableNamePattern' ")
            if (!catalog.isNullOrEmpty()) {
                append("AND TABLE_SCHEMA LIKE '$catalog' ")
            }
            append("AND TABLE_TYPE = 'BASE TABLE' ")
            append("ORDER BY TABLE_CAT, TABLE_NAME")
        }
    }

    // what should be done to simulate retrieving an empty result set without pinging database?
    // this should never be used with MySQL/MariaDB
    override fun getSequences(): String {
        return "SELECT 1 SEQUENCE_NAME FROM DUAL WHERE FALSE"
    }

    override fun getColumns(catalog: String, schemaPattern: String, tableNamePattern: String): String {
        return buildString {
            append("SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_DEFAULT COLUMN_DEF, ")
            append("CASE WHEN EXTRA = 'auto_increment' THEN 'YES' ELSE 'NO' END IS_AUTOINCREMENT, ")
            append("NUMERIC_SCALE DECIMAL_DIGITS, ")
            append("CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END NULLABLE, ")
            typeProvider.appendDataPrecisions("DATA_TYPE", "COLUMN_SIZE", this)
            append(", ")
            typeProvider.appendDataTypes("DATA_TYPE", "DATA_TYPE", this)
            append(" ")
            append("FROM INFORMATION_SCHEMA.COLUMNS ")
            append("WHERE TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME LIKE '$tableNamePattern' AND COLUMN_NAME LIKE '%' ")
            append("ORDER BY TABLE_NAME, ORDINAL_POSITION")
        }
    }

    override fun getPrimaryKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT COLUMN_NAME, INDEX_NAME PK_NAME ")
            append("FROM INFORMATION_SCHEMA.STATISTICS ")
            append("WHERE TABLE_SCHEMA LIKE '$schema' AND TABLE_NAME LIKE '$table' ")
            append("AND INDEX_NAME = 'PRIMARY' ")
            append("ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE, ")
            if (isMySQL6Plus) {
                append("EXPRESSION FILTER_CONDITION ")
            } else {
                append("NULL FILTER_CONDITION ")
            }
            append("FROM INFORMATION_SCHEMA.STATISTICS ")
            append("WHERE TABLE_SCHEMA = '$schema' AND TABLE_NAME = '$table' ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME")
        }
    }

    override fun getImportedKeys(catalog: String, schema: String, table: String): String {
        return buildString {
            append("SELECT tc.TABLE_NAME FKTABLE_NAME, tc.CONSTRAINT_NAME FK_NAME, ")
            typeProvider.appendReferenceOptions("rc.UPDATE_RULE", "UPDATE_RULE", this)
            append(", ")
            typeProvider.appendReferenceOptions("rc.DELETE_RULE", "DELETE_RULE", this)
            append(", ")
            append("kcu.ORDINAL_POSITION, kcu.COLUMN_NAME FKCOLUMN_NAME, kcu.REFERENCED_TABLE_SCHEMA PKTABLE_SCHEM, ")
            append("kcu.REFERENCED_TABLE_NAME PKTABLE_NAME, kcu.REFERENCED_COLUMN_NAME PKCOLUMN_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ")
            append("JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc ")
            append("ON tc.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME ")
            append("JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ")
            append("ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME ")
            append("WHERE tc.TABLE_SCHEMA LIKE '$schema' AND tc.TABLE_NAME LIKE '$table' ")
            append("AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY' ")
            append("ORDER BY PKTABLE_SCHEM, PKTABLE_NAME, FK_NAME, ORDINAL_POSITION")
        }
    }
}

private val isMySQL6Plus: Boolean
    get() = TransactionManager.current().db.isVersionCovers(BigDecimal("6.0"))

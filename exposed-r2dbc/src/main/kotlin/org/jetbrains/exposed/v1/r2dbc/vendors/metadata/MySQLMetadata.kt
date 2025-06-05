package org.jetbrains.exposed.v1.r2dbc.vendors.metadata

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
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
            options[ReferenceOption.RESTRICT] = 1
            options[ReferenceOption.SET_DEFAULT] = 1
            options[ReferenceOption.NO_ACTION] = 1
        } else {
            options[ReferenceOption.RESTRICT] = 3
            options[ReferenceOption.SET_DEFAULT] = 3
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

internal open class MySQLMetadata : MetadataProvider(MySQLPropertyProvider(), MySQLTypeProvider()) {
    // TODO potential mismatch: MariaDB R2DBC returns User@Host, but MariaDB JDBC returns just User
    override fun getUsername(): String {
        return "SELECT USER() AS USER_NAME"
    }

    override fun getReadOnlyMode(): String {
        return "SELECT @@transaction_read_only AS READ_ONLY"
    }

    override fun setReadOnlyMode(value: Boolean): String {
        return if (value) {
            "SET @@transaction_read_only = ON"
        } else {
            "SET @@transaction_read_only = OFF"
        }
    }

    override fun getDatabaseMode(): String = ""

    override fun getCatalog(): String {
        return "SELECT DATABASE() AS TABLE_CAT"
    }

    override fun setCatalog(value: String): String {
        return "USE $value"
    }

    override fun getSchema(): String = ""

    override fun getCatalogs(): String {
        return buildString {
            append("SELECT DISTINCT SCHEMA_NAME AS TABLE_CAT ")
            append("FROM INFORMATION_SCHEMA.SCHEMATA ")
            append("ORDER BY TABLE_CAT")
        }
    }

    override fun getSchemas(): String = ""

    override fun getTables(catalog: String, schemaPattern: String): String {
        return buildString {
            append("SELECT TABLE_SCHEMA AS TABLE_CAT, NULL AS TABLE_SCHEM, TABLE_NAME ")
            append("FROM INFORMATION_SCHEMA.TABLES ")
            append("WHERE TABLE_SCHEMA = '$catalog' ")
            append("AND TABLE_TYPE = 'BASE TABLE' ")
            append("ORDER BY TABLE_CAT, TABLE_NAME")
        }
    }

    override fun getAllSequences(): String = ""

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
            append("CASE WHEN EXTRA = 'auto_increment' THEN 'YES' ELSE 'NO' END AS IS_AUTOINCREMENT ")
            append("FROM INFORMATION_SCHEMA.COLUMNS ")
            append("WHERE TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME = '$table' ")
            append("ORDER BY ORDINAL_POSITION")
        }
    }

    override fun getPrimaryKeys(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT COLUMN_NAME, INDEX_NAME AS PK_NAME ")
            append("FROM INFORMATION_SCHEMA.STATISTICS ")
            append("WHERE TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME = '$table' ")
            append("AND INDEX_NAME = 'PRIMARY' ")
            append("ORDER BY COLUMN_NAME")
        }
    }

    override fun getIndexInfo(catalog: String, schemaPattern: String, table: String): String {
        // EXPRESSION AS FILTER_CONDITION <-- this actually returns the correct functional index string
        // but this goes against JDBC metadata query, which always returns null for this result field
        // Todo: Assess current impact of this mismatch in JDBC migration & consider switching both to use EXPRESSION
        return buildString {
            append("SELECT CASE WHEN NON_UNIQUE = 0 THEN 'FALSE' ELSE 'TRUE' END AS NON_UNIQUE, ")
            append("INDEX_NAME, SEQ_IN_INDEX AS ORDINAL_POSITION, ")
            append("COLUMN_NAME, NULL AS FILTER_CONDITION ")
            append("FROM INFORMATION_SCHEMA.STATISTICS ")
            append("WHERE TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND TABLE_NAME = '$table' ")
            append("ORDER BY NON_UNIQUE, INDEX_NAME, ORDINAL_POSITION")
        }
    }

    override fun getImportedKeys(catalog: String, schemaPattern: String, table: String): String {
        return buildString {
            append("SELECT kcu.REFERENCED_TABLE_NAME AS PKTABLE_NAME, kcu.REFERENCED_COLUMN_NAME AS PKCOLUMN_NAME, ")
            append("kcu.TABLE_NAME AS FKTABLE_NAME, kcu.COLUMN_NAME AS FKCOLUMN_NAME, ")
            append("kcu.ORDINAL_POSITION AS KEY_SEQ, ")
            typeProvider.appendReferenceOptions("rc.UPDATE_RULE", "UPDATE_RULE", this)
            append(", ")
            typeProvider.appendReferenceOptions("rc.DELETE_RULE", "DELETE_RULE", this)
            append(", ")
            append("rc.CONSTRAINT_NAME AS FK_NAME ")
            append("FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc ")
            append("INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ")
            append("ON rc.CONSTRAINT_SCHEMA = kcu.TABLE_SCHEMA AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME ")
            append("WHERE kcu.TABLE_SCHEMA LIKE '$schemaPattern' ")
            append("AND kcu.CONSTRAINT_SCHEMA LIKE '$schemaPattern' ")
            append("AND rc.CONSTRAINT_SCHEMA LIKE '$schemaPattern' ")
            append("AND kcu.TABLE_NAME = '$table' ")
            append("ORDER BY PKTABLE_NAME, KEY_SEQ")
        }
    }

    override fun getCheckConstraints(catalog: String, schemaPattern: String, table: String): String = ""
}

private val isMySQL6Plus: Boolean
    get() = TransactionManager.current().db.isVersionCovers(BigDecimal("6.0"))

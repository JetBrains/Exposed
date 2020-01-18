package org.jetbrains.exposed.sql.vendors

/** Returns a map of vendor specific keywords. */
val VENDORS_KEYWORDS: Map<String, List<String>> = mapOf(
    OracleDialect.dialectName to listOf("COMMENT", "NUMBER", "UID")
)

/** Returns a set with all the keywords that are part of the SQL:2003 standard. */
val ANSI_SQL_2003_KEYWORDS: Set<String> = setOf(
    "A",
    "ABS",
    "ABSOLUTE",
    "ACTION",
    "ADA",
    "ADD",
    "ADMIN",
    "AFTER",
    "ALL",
    "ALLOCATE",
    "ALTER",
    "ALWAYS",
    "AND",
    "ANY",
    "ARE",
    "ARRAY",
    "AS",
    "ASC",
    "ASENSITIVE",
    "ASSERTION",
    "ASSIGNMENT",
    "ASYMMETRIC",
    "AT",
    "ATOMIC",
    "ATTRIBUTE",
    "ATTRIBUTES",
    "AUTHORIZATION",
    "AVG",
    "BEFORE",
    "BEGIN",
    "BERNOULLI",
    "BETWEEN",
    "BIGINT",
    "BINARY",
    "BLOB",
    "BOOLEAN",
    "BOTH",
    "BREADTH",
    "BY",
    "C",
    "CALL",
    "CALLED",
    "CARDINALITY",
    "CASCADE",
    "CASCADED",
    "CASE",
    "CAST",
    "CATALOG",
    "CATALOG_NAME",
    "CEIL",
    "CEILING",
    "CHAIN",
    "CHAR",
    "CHAR_LENGTH",
    "CHARACTER",
    "CHARACTER_LENGTH",
    "CHARACTER_SET_CATALOG",
    "CHARACTER_SET_NAME",
    "CHARACTER_SET_SCHEMA",
    "CHARACTERISTICS",
    "CHARACTERS",
    "CHECK",
    "CHECKED",
    "CLASS_ORIGIN",
    "CLOB",
    "CLOSE",
    "COALESCE",
    "COBOL",
    "CODE_UNITS",
    "COLLATE",
    "COLLATION",
    "COLLATION_CATALOG",
    "COLLATION_NAME",
    "COLLATION_SCHEMA",
    "COLLECT",
    "COLUMN",
    "COLUMN_NAME",
    "COMMAND_FUNCTION",
    "COMMAND_FUNCTION_CODE",
    "COMMIT",
    "COMMITTED",
    "CONDITION",
    "CONDITION_NUMBER",
    "CONNECT",
    "CONNECTION_NAME",
    "CONSTRAINT",
    "CONSTRAINT_CATALOG",
    "CONSTRAINT_NAME",
    "CONSTRAINT_SCHEMA",
    "CONSTRAINTS",
    "CONSTRUCTORS",
    "CONTAINS",
    "CONTINUE",
    "CONVERT",
    "CORR",
    "CORRESPONDING",
    "COUNT",
    "COVAR_POP",
    "COVAR_SAMP",
    "CREATE",
    "CROSS",
    "CUBE",
    "CUME_DIST",
    "CURRENT",
    "CURRENT_COLLATION",
    "CURRENT_DATE",
    "CURRENT_DEFAULT_TRANSFORM_GROUP",
    "CURRENT_PATH",
    "CURRENT_ROLE",
    "CURRENT_TIME",
    "CURRENT_TIMESTAMP",
    "CURRENT_TRANSFORM_GROUP_FOR_TYPE",
    "CURRENT_USER",
    "CURSOR",
    "CURSOR_NAME",
    "CYCLE",
    "DATA",
    "DATE",
    "DATETIME_INTERVAL_CODE",
    "DATETIME_INTERVAL_PRECISION",
    "DAY",
    "DEALLOCATE",
    "DEC",
    "DECIMAL",
    "DECLARE",
    "DEFAULT",
    "DEFAULTS",
    "DEFERRABLE",
    "DEFERRED",
    "DEFINED",
    "DEFINER",
    "DEGREE",
    "DELETE",
    "DENSE_RANK",
    "DEPTH",
    "DEREF",
    "DERIVED",
    "DESC",
    "DESCRIBE",
    "DESCRIPTOR",
    "DETERMINISTIC",
    "DIAGNOSTICS",
    "DISCONNECT",
    "DISPATCH",
    "DISTINCT",
    "DOMAIN",
    "DOUBLE",
    "DROP",
    "DYNAMIC",
    "DYNAMIC_FUNCTION",
    "DYNAMIC_FUNCTION_CODE",
    "EACH",
    "ELEMENT",
    "ELSE",
    "END",
    "END",
    "EQUALS",
    "EVERY",
    "EXCEPT",
    "EXCEPTION",
    "EXCLUDE",
    "EXCLUDING",
    "EXEC",
    "EXECUTE",
    "EXISTS",
    "EXP",
    "EXTERNAL",
    "EXTRACT",
    "FALSE",
    "FETCH",
    "FILTER",
    "FINAL",
    "FIRST",
    "FLOAT",
    "FLOOR",
    "FOLLOWING",
    "FOR",
    "FOREIGN",
    "FORTRAN",
    "FOUND",
    "FREE",
    "FROM",
    "FULL",
    "FUNCTION",
    "FUSION",
    "G",
    "GENERAL",
    "GET",
    "GLOBAL",
    "GO",
    "GOTO",
    "GRANT",
    "GRANTED",
    "GROUP",
    "GROUPING",
    "HAVING",
    "HIERARCHY",
    "HOLD",
    "HOUR",
    "IDENTITY",
    "IMMEDIATE",
    "IMPLEMENTATION",
    "IN",
    "INCLUDING",
    "INCREMENT",
    "INDICATOR",
    "INITIALLY",
    "INNER",
    "INOUT",
    "INPUT",
    "INSENSITIVE",
    "INSERT",
    "INSTANCE",
    "INSTANTIABLE",
    "INT",
    "INTEGER",
    "INTERSECT",
    "INTERSECTION",
    "INTERVAL",
    "INTO",
    "INVOKER",
    "IS",
    "ISOLATION",
    "ISOLATION",
    "JOIN",
    "K",
    "KEY",
    "KEY_MEMBER",
    "KEY_TYPE",
    "LANGUAGE",
    "LARGE",
    "LAST",
    "LATERAL",
    "LEADING",
    "LEFT",
    "LENGTH",
    "LEVEL",
    "LIKE",
    "LN",
    "LOCAL",
    "LOCALTIME",
    "LOCALTIMESTAMP",
    "LOCATOR",
    "LOWER",
    "M",
    "MAP",
    "MATCH",
    "MATCHED",
    "MAX",
    "MAXVALUE",
    "MEMBER",
    "MERGE",
    "MESSAGE_LENGTH",
    "MESSAGE_OCTET_LENGTH",
    "MESSAGE_TEXT",
    "METHOD",
    "MIN",
    "MINUTE",
    "MINVALUE",
    "MOD",
    "MODIFIES",
    "MODULE",
    "MONTH",
    "MORE",
    "MULTISET",
    "MUMPS",
    "NAME",
    "NAMES",
    "NATIONAL",
    "NATURAL",
    "NCHAR",
    "NCLOB",
    "NESTING",
    "NEW",
    "NEXT",
    "NO",
    "NONE",
    "NORMALIZE",
    "NORMALIZED",
    "NOT",
    "NULL",
    "NULLABLE",
    "NULLIF",
    "NULLS",
    "NUMBER",
    "NUMERIC",
    "OBJECT",
    "OCTET_LENGTH",
    "OCTETS",
    "OF",
    "OLD",
    "ON",
    "ONLY",
    "OPEN",
    "OPTION",
    "OPTIONS",
    "OR",
    "ORDER",
    "ORDERING",
    "ORDINALITY",
    "OTHERS",
    "OUT",
    "OUTER",
    "OUTPUT",
    "OVER",
    "OVERLAPS",
    "OVERLAY",
    "OVERRIDING",
    "PAD",
    "PARAMETER",
    "PARAMETER_MODE",
    "PARAMETER_NAME",
    "PARAMETER_ORDINAL_POSITION",
    "PARAMETER_SPECIFIC_CATALOG",
    "PARAMETER_SPECIFIC_NAME",
    "PARAMETER_SPECIFIC_SCHEMA",
    "PARTIAL",
    "PARTITION",
    "PASCAL",
    "PATH",
    "PERCENT_RANK",
    "PERCENTILE_CONT",
    "PERCENTILE_DISC",
    "PLACING",
    "PLI",
    "POSITION",
    "POWER",
    "PRECEDING",
    "PRECISION",
    "PREPARE",
    "PRESERVE",
    "PRIMARY",
    "PRIOR",
    "PRIVILEGES",
    "PROCEDURE",
    "PUBLIC",
    "RANGE",
    "RANK",
    "READ",
    "READS",
    "REAL",
    "RECURSIVE",
    "REF",
    "REFERENCES",
    "REFERENCING",
    "REGR_AVGX",
    "REGR_AVGY",
    "REGR_COUNT",
    "REGR_INTERCEPT",
    "REGR_R2",
    "REGR_SLOPE",
    "REGR_SXX",
    "REGR_SXY",
    "REGR_SYY",
    "RELATIVE",
    "RELEASE",
    "REPEATABLE",
    "RESTART",
    "RESULT",
    "RETURN",
    "RETURNED_CARDINALITY",
    "RETURNED_LENGTH",
    "RETURNED_OCTET_LENGTH",
    "RETURNED_SQLSTATE",
    "RETURNS",
    "REVOKE",
    "RIGHT",
    "ROLE",
    "ROLLBACK",
    "ROLLUP",
    "ROUTINE",
    "ROUTINE_CATALOG",
    "ROUTINE_NAME",
    "ROUTINE_SCHEMA",
    "ROW",
    "ROW_COUNT",
    "ROW_NUMBER",
    "ROWS",
    "SAVEPOINT",
    "SCALE",
    "SCHEMA",
    "SCHEMA_NAME",
    "SCOPE_CATALOG",
    "SCOPE_NAME",
    "SCOPE_SCHEMA",
    "SCROLL",
    "SEARCH",
    "SECOND",
    "SECTION",
    "SECURITY",
    "SELECT",
    "SELF",
    "SENSITIVE",
    "SEQUENCE",
    "SERIALIZABLE",
    "SERVER_NAME",
    "SESSION",
    "SESSION_USER",
    "SET",
    "SETS",
    "SIMILAR",
    "SIMPLE",
    "SIZE",
    "SMALLINT",
    "SOME",
    "SOURCE",
    "SPACE",
    "SPECIFIC",
    "SPECIFIC_NAME",
    "SPECIFICTYPE",
    "SQL",
    "SQLEXCEPTION",
    "SQLSTATE",
    "SQLWARNING",
    "SQRT",
    "START",
    "STATE",
    "STATEMENT",
    "STATIC",
    "STDDEV_POP",
    "STDDEV_SAMP",
    "STRUCTURE",
    "STYLE",
    "SUBCLASS_ORIGIN",
    "SUBMULTISET",
    "SUBSTRING",
    "SUM",
    "SYMMETRIC",
    "SYSTEM",
    "SYSTEM_USER",
    "TABLE",
    "TABLE_NAME",
    "TABLESAMPLE",
    "TEMPORARY",
    "THEN",
    "TIES",
    "TIME",
    "TIMESTAMP",
    "TIMEZONE_HOUR",
    "TIMEZONE_MINUTE",
    "TO",
    "TOP_LEVEL_COUNT",
    "TRAILING",
    "TRANSACTION",
    "TRANSACTION_ACTIVE",
    "TRANSACTIONS_COMMITTED",
    "TRANSACTIONS_ROLLED_BACK",
    "TRANSFORM",
    "TRANSFORMS",
    "TRANSLATE",
    "TRANSLATION",
    "TREAT",
    "TRIGGER",
    "TRIGGER_CATALOG",
    "TRIGGER_NAME",
    "TRIGGER_SCHEMA",
    "TRIM",
    "TRUE",
    "TYPE",
    "UESCAPE",
    "UNBOUNDED",
    "UNCOMMITTED",
    "UNDER",
    "UNION",
    "UNIQUE",
    "UNKNOWN",
    "UNNAMED",
    "UNNEST",
    "UPDATE",
    "UPPER",
    "USAGE",
    "USER",
    "USER_DEFINED_TYPE_CATALOG",
    "USER_DEFINED_TYPE_CODE",
    "USER_DEFINED_TYPE_NAME",
    "USER_DEFINED_TYPE_SCHEMA",
    "USING",
    "VALUE",
    "VALUES",
    "VAR_POP",
    "VAR_SAMP",
    "VARCHAR",
    "VARYING",
    "VIEW",
    "WHEN",
    "WHENEVER",
    "WHERE",
    "WIDTH_BUCKET",
    "WINDOW",
    "WITH",
    "WITHIN",
    "WITHOUT",
    "WORK",
    "WRITE",
    "YEAR",
    "ZONE"
)

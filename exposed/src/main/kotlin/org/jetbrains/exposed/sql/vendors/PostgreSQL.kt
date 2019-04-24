package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

internal object PostgreSQLDataTypeProvider : DataTypeProvider() {

    override fun shortAutoincType(): String = "SERIAL"

    override fun longAutoincType(): String = "BIGSERIAL"

    override fun dateTimeType(): String = "TIMESTAMP"

    override fun uuidType(): String = "uuid"

    override fun blobType(): String = "bytea"

    override fun binaryType(length: Int): String = "bytea"

    override fun uuidToDB(value: UUID): Any = value

    override val blobAsStream: Boolean = true
}

internal object PostgreSQLFunctionProvider : FunctionProvider() {
    override fun update(targets: ColumnSet, columnsAndValues: List<Pair<Column<*>, Any?>>, limit: Int?, where: Op<Boolean>?, transaction: Transaction): String {
        if (limit != null) transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in UPDATE clause.")
        return super.update(targets, columnsAndValues, limit, where, transaction)
    }

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {
        val builder = QueryBuilder(true)
        val sql = if (data.isEmpty()) ""
        else data.joinToString(prefix = "VALUES (", postfix = ")") { (col, value) ->
            builder.registerArgument(col, value)
        }

        val columns = data.map { it.first }

        val def = super.insert(false, table, columns, sql, transaction)

        val uniqueCols = columns.filter { it.indexInPK != null }.sortedBy { it.indexInPK }
        if (uniqueCols.isEmpty())
            transaction.throwUnsupportedException("Postgres replace table must supply at least one primary key")
        val conflictKey = uniqueCols.joinToString { transaction.identity(it) }
        return def + "ON CONFLICT ($conflictKey) DO UPDATE SET " + columns.joinToString { "${transaction.identity(it)}=EXCLUDED.${transaction.identity(it)}" }
    }

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) "$def $onConflictIgnore" else def
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        if (limit != null) transaction.throwUnsupportedException("LIMIT is not supported in DELETE in PostgreSQL")
        return super.delete(ignore, table, where, limit, transaction)
    }

    private const val onConflictIgnore = "ON CONFLICT DO NOTHING"

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder): String {
        val tr = TransactionManager.current()
        return when {
            expr.orderBy.isNotEmpty() -> tr.throwUnsupportedException("PostgreSQL doesn't support ORDER BY in STRING_AGG.")
            expr.distinct -> tr.throwUnsupportedException("PostgreSQL doesn't support DISTINCT in STRING_AGG.")
            expr.separator == null -> tr.throwUnsupportedException("PostgreSQL requires explicit separator in STRING_AGG.")
            else -> "STRING_AGG(${expr.expr.toSQL(queryBuilder)}, '${expr.separator}')"
        }
    }
}

open class PostgreSQLDialect : VendorDialect(dialectName, PostgreSQLDataTypeProvider, PostgreSQLFunctionProvider) {
    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun modifyColumn(column: Column<*>): String = buildString {
        val colName = TransactionManager.current().identity(column)
        append("ALTER COLUMN $colName TYPE ${column.columnType.sqlType()},")
        append("ALTER COLUMN $colName ")
        if (column.columnType.nullable)
            append("DROP ")
        else
            append("SET ")
        append("NOT NULL")
        column.dbDefaultValue?.let {
            append(", ALTER COLUMN $colName SET DEFAULT ${PostgreSQLDataTypeProvider.processForDefaultValue(it)}")
        }
    }

    companion object {
        const val dialectName = "postgresql"
        val keywords = """
            A,ABS,ABSOLUTE,ACTION,ADA,ADD,ADMIN,AFTER,ALIAS,ALL,ALLOCATE,ALTER,ALWAYS,AND,ANY,ARE,ARRAY,AS,ASC,
            ASENSITIVE,ASSERTION,ASSIGNMENT,ASYMMETRIC,AT,ATOMIC,ATTRIBUTE,ATTRIBUTES,AUTHORIZATION,AVG,BEFORE,
            BEGIN,BERNOULLI,BETWEEN,BIGINT,BINARY,BITVAR,BIT_LENGTH,BLOB,BOOLEAN,BOTH,BREADTH,BY,C,CALL,CALLED,
            CARDINALITY,CASCADE,CASCADED,CASE,CAST,CATALOG,CATALOG_NAME,CEIL,CEILING,CHAIN,CHAR,CHARACTER,
            CHARACTERISTICS,CHARACTERS,CHARACTER_LENGTH,CHARACTER_SET_CATALOG,CHARACTER_SET_NAME,
            CHARACTER_SET_SCHEMA,CHAR_LENGTH,CHECK,CHECKED,CLASS_ORIGIN,CLOB,CLOSE,COALESCE,COBOL,
            COLLATE,COLLATION,COLLATION_CATALOG,COLLATION_NAME,COLLATION_SCHEMA,COLLECT,COLUMN,COLUMN_NAME,
            COMMAND_FUNCTION,COMMAND_FUNCTION_CODE,COMMIT,COMMITTED,COMPLETION,CONDITION,CONDITION_NUMBER,
            CONNECT,CONNECTION_NAME,CONSTRAINT,CONSTRAINTS,CONSTRAINT_CATALOG,CONSTRAINT_NAME,
            CONSTRAINT_SCHEMA,CONSTRUCTOR,CONTAINS,CONTINUE,CONVERT,CORR,CORRESPONDING,COUNT,COVAR_POP,
            COVAR_SAMP,CREATE,CREATEDB,CREATEROLE,CREATEUSER,CROSS,CUBE,CUME_DIST,CURRENT,CURRENT_DATE,
            CURRENT_DEFAULT_TRANSFORM_GROUP,CURRENT_PATH,CURRENT_ROLE,CURRENT_TIME,CURRENT_TIMESTAMP,
            CURRENT_TRANSFORM_GROUP_FOR_TYPE,CURRENT_USER,CURSOR,CURSOR_NAME,CYCLE,DATA,DATE,
            DATETIME_INTERVAL_CODE,DATETIME_INTERVAL_PRECISION,DAY,DEALLOCATE,DEC,DECIMAL,DECLARE,DEFAULT,
            DEFAULTS,DEFERRABLE,DEFERRED,DEFINED,DEFINER,DEGREE,DELETE,DENSE_RANK,DEPTH,DEREF,DERIVED,DESC,
            DESCRIBE,DESCRIPTOR,DESTROY,DESTRUCTOR,DETERMINISTIC,DIAGNOSTICS,DISCONNECT,DISPATCH,DISTINCT,
            DOMAIN,DOUBLE,DROP,DYNAMIC,DYNAMIC_FUNCTION,DYNAMIC_FUNCTION_CODE,EACH,ELEMENT,ELSE,END,END-EXEC,
            EQUALS,ESCAPE,EVERY,EXCEPT,EXCEPTION,EXCLUDE,EXCLUDING,EXEC,EXECUTE,EXISTING,EXISTS,EXP,EXTERNAL,
            EXTRACT,FALSE,FETCH,FILTER,FINAL,FIRST,FLOAT,FLOOR,FOLLOWING,FOR,FOREIGN,FORTRAN,FOUND,FREE,FROM,
            FULL,FUNCTION,FUSION,G,GENERAL,GENERATED,GET,GLOBAL,GO,GOTO,GRANT,GRANTED,GROUP,GROUPING,HAVING,
            HIERARCHY,HOLD,HOST,HOUR,IDENTITY,IGNORE,IMMEDIATE,IMPLEMENTATION,IN,INCLUDING,INCREMENT,
            INDICATOR,INFIX,INITIALIZE,INITIALLY,INNER,INOUT,INPUT,INSENSITIVE,INSERT,INSTANCE,INSTANTIABLE,
            INT,INTEGER,INTERSECT,INTERSECTION,INTERVAL,INTO,INVOKER,IS,ISOLATION,ITERATE,JOIN,K,KEY,
            KEY_MEMBER,KEY_TYPE,LANCOMPILER,LANGUAGE,LARGE,LAST,LATERAL,LEADING,LEFT,LENGTH,LESS,LEVEL,LIKE,
            LN,LOCAL,LOCALTIME,LOCALTIMESTAMP,LOCATOR,LOGIN,LOWER,M,MAP,MATCH,MATCHED,MAX,MAXVALUE,MEMBER,
            MERGE,MESSAGE_LENGTH,MESSAGE_OCTET_LENGTH,MESSAGE_TEXT,METHOD,MIN,MINUTE,MINVALUE,MOD,MODIFIES,
            MODIFY,MODULE,MONTH,MORE,MULTISET,MUMPS,NAME,NAMES,NATIONAL,NATURAL,NCHAR,NCLOB,NESTING,NEW,NEXT,
            NO,NOCREATEDB,NOCREATEROLE,NOCREATEUSER,NOINHERIT,NOLOGIN,NONE,NORMALIZE,NORMALIZED,NOSUPERUSER,
            NOT,NULL,NULLABLE,NULLIF,NULLS,NUMBER,NUMERIC,OBJECT,OCTETS,OCTET_LENGTH,OF,OLD,ON,ONLY,OPEN,
            OPERATION,OPTION,OPTIONS,OR,ORDER,ORDERING,ORDINALITY,OTHERS,OUT,OUTER,OUTPUT,OVER,OVERLAPS,
            OVERLAY,OVERRIDING,PAD,PARAMETER,PARAMETERS,PARAMETER_MODE,PARAMETER_NAME,
            PARAMETER_ORDINAL_POSITION,PARAMETER_SPECIFIC_CATALOG,PARAMETER_SPECIFIC_NAME,
            PARAMETER_SPECIFIC_SCHEMA,PARTIAL,PARTITION,PASCAL,PATH,PERCENTILE_CONT,PERCENTILE_DISC,
            PERCENT_RANK,PLACING,PLI,POSITION,POSTFIX,POWER,PRECEDING,PRECISION,PREFIX,PREORDER,PREPARE,
            PRESERVE,PRIMARY,PRIOR,PRIVILEGES,PROCEDURE,PUBLIC,RANGE,RANK,READ,READS,REAL,RECURSIVE,REF,
            REFERENCES,REFERENCING,REGR_AVGX,REGR_AVGY,REGR_COUNT,REGR_INTERCEPT,REGR_R2,REGR_SLOPE,REGR_SXX,
            REGR_SXY,REGR_SYY,RELATIVE,RELEASE,REPEATABLE,RESTART,RESULT,RETURN,RETURNED_CARDINALITY,
            RETURNED_LENGTH,RETURNED_OCTET_LENGTH,RETURNED_SQLSTATE,RETURNS,REVOKE,RIGHT,ROLE,ROLLBACK,ROLLUP,
            ROUTINE,ROUTINE_CATALOG,ROUTINE_NAME,ROUTINE_SCHEMA,ROW,ROWS,ROW_COUNT,ROW_NUMBER,SAVEPOINT,SCALE,
            SCHEMA,SCHEMA_NAME,SCOPE,SCOPE_CATALOG,SCOPE_NAME,SCOPE_SCHEMA,SCROLL,SEARCH,SECOND,SECTION,
            SECURITY,SELECT,SELF,SENSITIVE,SEQUENCE,SERIALIZABLE,SERVER_NAME,SESSION,SESSION_USER,SET,SETS,
            SIMILAR,SIMPLE,SIZE,SMALLINT,SOME,SOURCE,SPACE,SPECIFIC,SPECIFICTYPE,SPECIFIC_NAME,SQL,SQLCODE,
            SQLERROR,SQLEXCEPTION,SQLSTATE,SQLWARNING,SQRT,START,STATE,STATEMENT,STATIC,STDDEV_POP,STDDEV_SAMP,
            STRUCTURE,STYLE,SUBCLASS_ORIGIN,SUBLIST,SUBMULTISET,SUBSTRING,SUM,SUPERUSER,SYMMETRIC,SYSTEM,
            SYSTEM_USER,TABLE,TABLESAMPLE,TABLE_NAME,TEMPORARY,TERMINATE,THAN,THEN,TIES,TIME,TIMESTAMP,
            TIMEZONE_HOUR,TIMEZONE_MINUTE,TO,TOAST,TOP_LEVEL_COUNT,TRAILING,TRANSACTION,TRANSACTIONS_COMMITTED,
            TRANSACTIONS_ROLLED_BACK,TRANSACTION_ACTIVE,TRANSFORM,TRANSFORMS,TRANSLATE,TRANSLATION,TREAT,
            TRIGGER,TRIGGER_CATALOG,TRIGGER_NAME,TRIGGER_SCHEMA,TRIM,TRUE,TYPE,UESCAPE,UNBOUNDED,UNCOMMITTED,
            UNDER,UNION,UNIQUE,UNKNOWN,UNNAMED,UNNEST,UPDATE,UPPER,USAGE,USER,USER_DEFINED_TYPE_CATALOG,
            USER_DEFINED_TYPE_CODE,USER_DEFINED_TYPE_NAME,USER_DEFINED_TYPE_SCHEMA,USING,VALUE,VALUES,VARCHAR,
            VARIABLE,VARYING,VAR_POP,VAR_SAMP,VIEW,WHEN,WHENEVER,WHERE,WIDTH_BUCKET,WINDOW,WITH,WITHIN,
            WITHOUT,WORK,WRITE,YEAR,ZONE
        """.trimIndent().split(",")
    }
}

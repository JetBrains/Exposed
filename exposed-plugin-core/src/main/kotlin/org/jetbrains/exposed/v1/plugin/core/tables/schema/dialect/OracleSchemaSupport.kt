package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import java.math.BigDecimal
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Responsible for obtaining non-standard metadata from an Oracle database.
 *
 * The driver lists tables, columns, primary keys and indices, but much of what the generator needs is not in what it
 * reports: the precision that tells one `NUMBER` from another, the length semantics of a character column, column
 * comments, the kind of an identity column, and every foreign key that targets a `UNIQUE` constraint rather than a
 * primary key. A column default is reported as a stream that can be read only once and in column order. All of these
 * are read from the `ALL_` catalog views instead, which show what the connected user is allowed to see.
 */
@Suppress("TooManyFunctions")
internal object OracleSchemaSupport : DialectSchemaSupport {
    override val syntax: DialectSyntax = OracleSyntax

    override val createsIndexForForeignKeys: Boolean = false

    // A schema is a user, and holds the objects that user owns
    override val supportsSchemas: Boolean = true

    override val schemasAreCatalogs: Boolean = false

    // Every user is a schema, including the many that the database creates for itself, which the catalog marks
    override fun isSystemSchema(connection: Connection, name: String): Boolean {
        return connection.query("SELECT ORACLE_MAINTAINED FROM ALL_USERS WHERE USERNAME = ?", name) { rs ->
            rs.next() && rs.getString("ORACLE_MAINTAINED") == "Y"
        }
    }

    override fun declaredColumnTypes(connection: Connection, schema: String, tableName: String): Map<String, String> {
        val columns = columns(connection, schema, tableName)
        val vectorTypes = if (columns.any { it.dataType == VECTOR }) {
            vectorTypes(connection, schema, tableName)
        } else {
            emptyMap()
        }
        return columns.associate { it.name to it.normalizedType(vectorTypes[it.name]) }
    }

    // A virtual column has its expression where any other column has its default
    override fun generatedColumns(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return columns(connection, schema, tableName)
            .filter { it.isVirtual && it.default != null }
            .associate { it.name to it.default!! }
    }

    // A `UNIQUE` constraint is backed by an index of the same name, unless it was added on an index that already existed
    override fun uniqueConstraintNames(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.query(
            "SELECT INDEX_NAME, CONSTRAINT_NAME FROM ALL_CONSTRAINTS " +
                "WHERE OWNER = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'U' AND INDEX_NAME IS NOT NULL",
            schema,
            tableName,
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    put(rs.getString("INDEX_NAME"), rs.getString("CONSTRAINT_NAME"))
                }
            }
        }
    }

    // The catalog does not keep the value that a sequence started with, so that one is never returned
    override fun sequenceParameters(
        connection: Connection,
        schema: String,
        sequenceName: String,
        sequenceMaxValue: Long
    ): SequenceParameters? {
        return connection.query(
            "SELECT MIN_VALUE, MAX_VALUE, INCREMENT_BY, CYCLE_FLAG, CACHE_SIZE FROM ALL_SEQUENCES " +
                "WHERE SEQUENCE_OWNER = ? AND SEQUENCE_NAME = ?",
            schema,
            sequenceName,
        ) { rs ->
            if (!rs.next()) return@query null
            val maximum = rs.getBigDecimal("MAX_VALUE")
            val cache = rs.getLong("CACHE_SIZE")
            SequenceParameters(
                startWith = null,
                incrementBy = rs.getLong("INCREMENT_BY").takeIf { it != DEFAULT_SEQUENCE_INCREMENT },
                minValue = rs.getBigDecimal("MIN_VALUE").toLongOrNull()?.takeIf { it != IMPLIED_SEQUENCE_MINIMUM },
                // a sequence created without a maximum gets one beyond the range of Long, which is implied as well
                maxValue = maximum.toLongOrNull()?.takeIf { it != sequenceMaxValue },
                cycle = true.takeIf { rs.getString("CYCLE_FLAG") == "Y" },
                // NOCACHE is stored as 0, which is not a size that can be declared
                cache = cache.takeIf { it != DEFAULT_SEQUENCE_CACHE && it != NO_SEQUENCE_CACHE },
            ).takeUnless { it.isDefault }
        }
    }

    // The sequence behind an identity column belongs to the database, and is left out of [sequences] instead
    override fun ownedSequences(connection: Connection, schema: String, tableName: String): Map<String, String> = emptyMap()

    override fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>> = emptyMap()

    // The driver reports only the keys that target a primary key, and none that target a `UNIQUE` constraint
    override fun foreignKeys(connection: Connection, schema: String, tableName: String): List<ReportedForeignKeyColumn> {
        return connection.query(
            "SELECT c.CONSTRAINT_NAME, c.DELETE_RULE, cc.POSITION, cc.COLUMN_NAME, " +
                "r.OWNER AS TARGET_OWNER, r.TABLE_NAME AS TARGET_TABLE, rc.COLUMN_NAME AS TARGET_COLUMN " +
                "FROM ALL_CONSTRAINTS c " +
                "JOIN ALL_CONS_COLUMNS cc ON cc.OWNER = c.OWNER AND cc.CONSTRAINT_NAME = c.CONSTRAINT_NAME AND cc.TABLE_NAME = c.TABLE_NAME " +
                "JOIN ALL_CONSTRAINTS r ON r.OWNER = c.R_OWNER AND r.CONSTRAINT_NAME = c.R_CONSTRAINT_NAME " +
                "JOIN ALL_CONS_COLUMNS rc ON rc.OWNER = r.OWNER AND rc.CONSTRAINT_NAME = r.CONSTRAINT_NAME " +
                "AND rc.TABLE_NAME = r.TABLE_NAME AND rc.POSITION = cc.POSITION " +
                "WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE = 'R' " +
                "ORDER BY c.CONSTRAINT_NAME, cc.POSITION",
            schema,
            tableName,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    val name = rs.getString("CONSTRAINT_NAME")
                    add(
                        ReportedForeignKeyColumn(
                            groupKey = name,
                            name = name,
                            position = rs.getInt("POSITION"),
                            columnName = rs.getString("COLUMN_NAME"),
                            targetSchema = rs.getString("TARGET_OWNER"),
                            targetTable = rs.getString("TARGET_TABLE"),
                            targetColumnName = rs.getString("TARGET_COLUMN"),
                            deleteRule = when (rs.getString("DELETE_RULE")) {
                                "CASCADE" -> DatabaseMetaData.importedKeyCascade
                                "SET NULL" -> DatabaseMetaData.importedKeySetNull
                                else -> DatabaseMetaData.importedKeyNoAction
                            },
                            // a key has no update rule at all
                            updateRule = DatabaseMetaData.importedKeyNoAction,
                        )
                    )
                }
            }
        }
    }

    override fun columnOverrides(connection: Connection, schema: String, tableName: String): Map<String, ColumnOverride> {
        return columns(connection, schema, tableName).mapNotNull { column ->
            val notes = column.unsupportedNotes(tableName)
            if (notes.isEmpty() && column.comment == null && !column.isIdentity) return@mapNotNull null
            column.name to ColumnOverride(
                // an identity column is filled in by the database, and is not what autoIncrement() declares
                autoIncrement = false.takeIf { column.isIdentity },
                unsupportedNote = notes.joinToString(". ").ifEmpty { null },
                comment = column.comment,
            )
        }.toMap()
    }

    // A default is stored as it was written, trailing whitespace included, and the driver reports it as a stream that
    // can be read once only
    override fun columnDefaults(connection: Connection, schema: String, tableName: String): Map<String, String?> {
        return columns(connection, schema, tableName).associate { column ->
            column.name to when {
                column.isVirtual -> null
                // passed on as a default that cannot be declared, so that the column is left to the database
                column.isIdentity -> column.identityClause()
                else -> column.default?.let(::normalizeDefault)
            }
        }
    }

    // Exposed lists the sequences of the connected user only, and among them those that back an identity column
    override fun sequences(connection: Connection, schemas: List<String>): List<SequenceReference> {
        return schemas.flatMap { schema ->
            connection.query(
                "SELECT SEQUENCE_NAME FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER = ? ORDER BY SEQUENCE_NAME",
                schema
            ) { rs ->
                buildList {
                    while (rs.next()) {
                        val name = rs.getString("SEQUENCE_NAME")
                        if (!OracleSyntax.isIdentitySequenceName(name)) add(SequenceReference(schema, name))
                    }
                }
            }
        }
    }

    // An index covers every row of its table
    override fun indexFilters(connection: Connection, schema: String, tableName: String): Map<String, String> = emptyMap()

    // A function-based index, and a descending one, which is stored in the same way, has its expression in a view of
    // its own; the driver reports the hidden column that holds the value of the expression instead
    override fun indexExpressions(connection: Connection, schema: String, tableName: String): Map<String, List<String>> {
        val keys = connection.query(
            "SELECT ic.INDEX_NAME, ic.COLUMN_NAME, ic.DESCEND, ie.COLUMN_EXPRESSION " +
                "FROM ALL_IND_COLUMNS ic " +
                "LEFT JOIN ALL_IND_EXPRESSIONS ie ON ie.INDEX_OWNER = ic.INDEX_OWNER AND ie.INDEX_NAME = ic.INDEX_NAME " +
                "AND ie.COLUMN_POSITION = ic.COLUMN_POSITION " +
                "WHERE ic.TABLE_OWNER = ? AND ic.TABLE_NAME = ? ORDER BY ic.INDEX_NAME, ic.COLUMN_POSITION",
            schema,
            tableName,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    val indexName = rs.getString("INDEX_NAME")
                    val columnName = rs.getString("COLUMN_NAME")
                    val descending = rs.getString("DESCEND") == "DESC"
                    val expression = rs.getString("COLUMN_EXPRESSION")?.trim()
                    add(Triple(indexName, expression != null, (expression ?: columnName) + if (descending) " DESC" else ""))
                }
            }
        }
        return keys.groupBy { it.first }
            .filterValues { parts -> parts.any { it.second } }
            .mapValues { (_, parts) -> parts.map { it.third } }
    }

    // A materialized view is listed as a table by the driver, since a table of the same name holds its rows
    override fun unsupportedTableNote(connection: Connection, schema: String, tableName: String): String? {
        val isMaterializedView = connection.query(
            "SELECT 1 FROM ALL_MVIEWS WHERE OWNER = ? AND MVIEW_NAME = ?",
            schema,
            tableName,
        ) { rs -> rs.next() }
        if (!isMaterializedView) return null
        return "Table '$tableName' holds the rows of the materialized view of the same name. Its rows are written by the database " +
            "when the view is refreshed, so the generated definition can be used to query them, but not to insert, update or delete. " +
            "A table created from this definition is an ordinary table, since the query of the view is not part of it"
    }

    /** A column of a table as the catalog reports it. */
    private data class Column(
        val name: String,
        /** The type as the catalog names it, which for a timestamp includes its precision, as in `TIMESTAMP(6)`. */
        val dataType: String,
        val precision: Int?,
        val scale: Int?,
        /** The size in bytes. */
        val dataLength: Int,
        /** The length in characters of a character column. */
        val charLength: Int,
        /** Whether the length of a character column was declared in characters (`C`) or in bytes (`B`). */
        val charUsed: String?,
        val isVirtual: Boolean,
        val isIdentity: Boolean,
        val isDefaultOnNull: Boolean,
        val identityGeneration: String?,
        val identityOptions: String?,
        val comment: String?,
        /** The default as stored, or the expression of a virtual column. */
        val default: String?,
    ) {
        /** The type in the mapper's vocabulary when it has an exact Exposed function, and under its own name otherwise. */
        @Suppress("CyclomaticComplexMethod")
        fun normalizedType(vectorType: String?): String = when (dataType) {
            "NUMBER" -> when {
                // a number without a precision, which also stands for INTEGER, SMALLINT and a DECIMAL without arguments
                precision == null -> if (scale == null) "NUMBER" else "NUMBER(*, $scale)"
                scale == 0 -> INTEGER_TYPES[precision] ?: "DECIMAL($precision, 0)"
                scale == null || scale < 0 -> "NUMBER($precision, ${scale ?: 0})"
                else -> "DECIMAL($precision, $scale)"
            }
            // FLOAT and DOUBLE PRECISION are both stored with the largest binary precision, and REAL as FLOAT(63)
            "FLOAT" -> if (precision == DOUBLE_BINARY_PRECISION) "DOUBLE PRECISION" else "FLOAT(${precision ?: 0})"
            "CHAR" -> "CHAR($charLength)"
            "VARCHAR2" -> "VARCHAR($charLength)"
            "NCHAR", "NVARCHAR2" -> "$dataType($charLength)"
            // Exposed declares a binary() of up to 1999 bytes only
            "RAW" -> if (dataLength < MAX_BINARY_LENGTH) "VARBINARY($dataLength)" else "RAW($dataLength)"
            // Exposed declares these two without a precision, which is the default of 6 fractional digits. Any other
            // precision keeps a name that the mapper is told not to map
            "TIMESTAMP(6)" -> "TIMESTAMP"
            "TIMESTAMP(6) WITH TIME ZONE" -> "TIMESTAMP WITH TIME ZONE"
            VECTOR -> vectorType ?: "VECTOR(*, *)"
            else -> dataType
        }

        /** What the generated definition cannot say about this column. */
        fun unsupportedNotes(tableName: String): List<String> = buildList {
            val prefix = "Column '$name' of table '$tableName'"
            if (isIdentity) {
                add(
                    "$prefix is an identity column (${identityClause()}), which Exposed cannot declare on Oracle. It is marked " +
                        "databaseGenerated() so that the database supplies its values. A table created from this definition has a plain " +
                        "column there instead, and autoIncrement() would not fit either, since it takes values from a sequence of its own"
                )
            }
            if (isDefaultOnNull && !isIdentity) {
                add(
                    "$prefix is declared with DEFAULT ON NULL, so that its default also replaces an explicit NULL. " +
                        "The generated definition declares a plain default"
                )
            }
            if (dataType == "VARCHAR2" && charUsed == "B") {
                add(
                    "$prefix is declared as VARCHAR2($charLength BYTE), with its length in bytes, while the generated varchar() " +
                        "declares VARCHAR2($charLength CHAR), with its length in characters. A table created from this definition " +
                        "accepts longer values wherever a character takes more than one byte"
                )
            }
            if (dataType == "CHAR" && charUsed == "C") {
                add(
                    "$prefix is declared as CHAR($charLength CHAR), with its length in characters, while the generated char() " +
                        "declares CHAR($charLength), whose length is in bytes unless the session says otherwise"
                )
            }
            if (dataType == "CHAR" && charLength == 1) {
                add(
                    "$prefix is a CHAR(1), which is also what Exposed's bool() and char(name) declare on Oracle, so that the three " +
                        "cannot be told apart. It is generated as char(name, 1); configure a column mapping if it holds a boolean or a " +
                        "single Char"
                )
            }
        }

        /** The clause that declares this identity column, with its start and increment when they are not the defaults. */
        fun identityClause(): String {
            val generation = "GENERATED ${identityGeneration ?: "ALWAYS"}${if (isDefaultOnNull) " ON NULL" else ""} AS IDENTITY"
            val options = identityOptions.orEmpty()
            val start = IDENTITY_START_PATTERN.find(options)?.groupValues?.get(1)
            val increment = IDENTITY_INCREMENT_PATTERN.find(options)?.groupValues?.get(1)
            val declared = listOfNotNull(
                start?.takeIf { it != DEFAULT_IDENTITY_VALUE }?.let { "START WITH $it" },
                increment?.takeIf { it != DEFAULT_IDENTITY_VALUE }?.let { "INCREMENT BY $it" },
            )
            return if (declared.isEmpty()) generation else "$generation (${declared.joinToString(" ")})"
        }
    }

    // Only the columns that a user declared: the database adds hidden ones of its own for a function-based index and
    // for a JSON column. The default comes last, since it is a LONG, which is read as a stream
    private fun columns(connection: Connection, schema: String, tableName: String): List<Column> {
        return connection.query(
            "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_PRECISION, c.DATA_SCALE, c.DATA_LENGTH, c.CHAR_LENGTH, c.CHAR_USED, " +
                "c.VIRTUAL_COLUMN, c.IDENTITY_COLUMN, c.DEFAULT_ON_NULL, i.GENERATION_TYPE, i.IDENTITY_OPTIONS, m.COMMENTS, c.DATA_DEFAULT " +
                "FROM ALL_TAB_COLS c " +
                "LEFT JOIN ALL_TAB_IDENTITY_COLS i ON i.OWNER = c.OWNER AND i.TABLE_NAME = c.TABLE_NAME AND i.COLUMN_NAME = c.COLUMN_NAME " +
                "LEFT JOIN ALL_COL_COMMENTS m ON m.OWNER = c.OWNER AND m.TABLE_NAME = c.TABLE_NAME AND m.COLUMN_NAME = c.COLUMN_NAME " +
                "WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.USER_GENERATED = 'YES' ORDER BY c.INTERNAL_COLUMN_ID",
            schema,
            tableName,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        Column(
                            name = rs.getString("COLUMN_NAME"),
                            dataType = rs.getString("DATA_TYPE").orEmpty().uppercase(),
                            precision = rs.getInt("DATA_PRECISION").takeUnless { rs.wasNull() },
                            scale = rs.getInt("DATA_SCALE").takeUnless { rs.wasNull() },
                            dataLength = rs.getInt("DATA_LENGTH"),
                            charLength = rs.getInt("CHAR_LENGTH"),
                            charUsed = rs.getString("CHAR_USED"),
                            isVirtual = rs.getString("VIRTUAL_COLUMN") == "YES",
                            isIdentity = rs.getString("IDENTITY_COLUMN") == "YES",
                            isDefaultOnNull = rs.getString("DEFAULT_ON_NULL") == "YES",
                            identityGeneration = rs.getString("GENERATION_TYPE"),
                            identityOptions = rs.getString("IDENTITY_OPTIONS"),
                            comment = rs.getString("COMMENTS")?.takeIf { it.isNotBlank() },
                            default = rs.getString("DATA_DEFAULT")?.trim()?.takeIf { it.isNotEmpty() },
                        )
                    )
                }
            }
        }
    }

    // The dimensions and the format of a vector are in a column that only a database with vectors has, so it is read
    // only for a table that has such a column: `VECTOR(3,FLOAT32,DENSE)`, with `*` for any number or any format
    private fun vectorTypes(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.query(
            "SELECT COLUMN_NAME, VECTOR_INFO FROM ALL_TAB_COLS WHERE OWNER = ? AND TABLE_NAME = ? AND DATA_TYPE = 'VECTOR'",
            schema,
            tableName,
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    rs.getString("VECTOR_INFO")?.let { put(rs.getString("COLUMN_NAME"), normalizeVectorType(it)) }
                }
            }
        }
    }

    /**
     * Brings the description of a vector type to the mapper's vocabulary when it is what Exposed's `vector()` declares
     * (a dense vector of 32-bit floats, with a number of dimensions or any), and leaves it under a name that the mapper
     * does not know otherwise.
     */
    internal fun normalizeVectorType(vectorInfo: String): String {
        val parts = vectorInfo.trim().substringAfter('(', "").substringBeforeLast(')').split(',').map { it.trim().uppercase() }
        val dimensions = parts.getOrNull(0).orEmpty()
        val format = parts.getOrNull(1).orEmpty()
        val storage = parts.getOrNull(2) ?: DENSE_VECTOR
        return when {
            format != EXPOSED_VECTOR_FORMAT || storage != DENSE_VECTOR -> "VECTOR(${parts.joinToString(", ")})"
            dimensions == "*" -> VECTOR
            dimensions.toIntOrNull() != null -> "VECTOR($dimensions)"
            else -> "VECTOR(${parts.joinToString(", ")})"
        }
    }

    /**
     * Brings a default, as the catalog stores it, to the form that the driver of other databases reports. The two
     * conversions that Exposed writes for a date and a date-time literal become literals of the standard form; anything
     * else stays as it was written.
     */
    internal fun normalizeDefault(definition: String): String {
        val value = definition.trim()
        TO_DATE_PATTERN.matchEntire(value)?.let { return "DATE '${it.groupValues[1]}'" }
        TO_TIMESTAMP_PATTERN.matchEntire(value)?.let { return "TIMESTAMP '${it.groupValues[1]}'" }
        return value
    }

    private fun BigDecimal?.toLongOrNull(): Long? = try {
        this?.longValueExact()
    } catch (_: ArithmeticException) {
        null
    }

    private const val VECTOR: String = "VECTOR"
    private const val EXPOSED_VECTOR_FORMAT: String = "FLOAT32"
    private const val DENSE_VECTOR: String = "DENSE"

    /** The types that Exposed declares for `byte()`, `short()`, `integer()`, `long()` and `ulong()`, by precision. */
    @Suppress("MagicNumber")
    private val INTEGER_TYPES: Map<Int, String> = mapOf(
        3 to "TINYINT",
        5 to "SMALLINT",
        10 to "INT",
        19 to "BIGINT",
        20 to "BIGINT UNSIGNED"
    )

    private const val DOUBLE_BINARY_PRECISION: Int = 126
    private const val MAX_BINARY_LENGTH: Int = 2000

    private const val DEFAULT_IDENTITY_VALUE: String = "1"
    private val IDENTITY_START_PATTERN: Regex = Regex("""START WITH:\s*(-?\d+)""")
    private val IDENTITY_INCREMENT_PATTERN: Regex = Regex("""INCREMENT BY:\s*(-?\d+)""")

    /** What `autoIncrement("sequence")` declares when it creates a sequence, and what the database adds to that. */
    private const val IMPLIED_SEQUENCE_MINIMUM: Long = 1
    private const val DEFAULT_SEQUENCE_INCREMENT: Long = 1
    private const val DEFAULT_SEQUENCE_CACHE: Long = 20
    private const val NO_SEQUENCE_CACHE: Long = 0

    private val TO_DATE_PATTERN: Regex = Regex("""^TO_DATE\s*\(\s*'(\d{4}-\d{2}-\d{2})'\s*,\s*'YYYY-MM-DD'\s*\)$""")
    private val TO_TIMESTAMP_PATTERN: Regex =
        Regex("""^TO_TIMESTAMP\s*\(\s*'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d{1,9})?)'\s*,\s*'YYYY-MM-DD HH24:MI:SS\.FF3'\s*\)$""")

    private inline fun <T> Connection.query(sql: String, vararg parameters: String, read: (ResultSet) -> T): T =
        prepareStatement(sql).use { statement: PreparedStatement ->
            parameters.forEachIndexed { index, parameter -> statement.setString(index + 1, parameter) }
            statement.executeQuery().use(read)
        }
}

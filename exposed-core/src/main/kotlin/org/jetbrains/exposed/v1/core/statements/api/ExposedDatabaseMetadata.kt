package org.jetbrains.exposed.v1.core.statements.api

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.core.vendors.H2Dialect.H2CompatibilityMode
import java.sql.Types

/**
 * Base class responsible for shared utility methods needed for retrieving and storing information about
 * the underlying driver and associated [database].
 */
abstract class ExposedDatabaseMetadata(val database: String) {
    /** Clears and resets any stored information about the database's current schema to default values. */
    abstract fun resetCurrentScheme()

    @Suppress("ForbiddenComment")
    // TODO: THIS should become protected after the usage in DatabaseDialect is fully deprecated
    /**
     * Returns the corresponding [ReferenceOption] for the specified [refOption] result,
     * or `null` if the database result is an invalid string without a corresponding match.
     */
    @InternalApi
    abstract fun resolveReferenceOption(refOption: String): ReferenceOption?

    /** Clears any cached values. */
    abstract fun cleanCache()

    /** The database-specific and metadata-reliant implementation of [IdentifierManagerApi]. */
    abstract val identifierManager: IdentifierManagerApi

    /**
     * Here is the table of default values which are returned from the column `"COLUMN_DEF"` depending on how it was configured:
     *
     * - Not set: `varchar("any", 128).nullable()`
     * - Set null: `varchar("any", 128).nullable().default(null)`
     * - Set "NULL": `varchar("any", 128).nullable().default("NULL")`
     * ```
     * DB                  Not set    Set null                    Set "NULL"
     * SqlServer           null       "(NULL)"                    "('NULL')"
     * SQLite              null       "NULL"                      "'NULL'"
     * Postgres            null       "NULL::character varying"   "'NULL'::character varying"
     * PostgresNG          null       "NULL::character varying"   "'NULL'::character varying"
     * Oracle              null       "NULL "                     "'NULL' "
     * MySql5              null       null                        "NULL"
     * MySql8              null       null                        "NULL"
     * MariaDB3            "NULL"     "NULL"                      "'NULL'"
     * MariaDB2            "NULL"     "NULL"                      "'NULL'"
     * H2V1                null       "NULL"                      "'NULL'"
     * H2V1 (MySql)        null       "NULL"                      "'NULL'"
     * H2V2                null       "NULL"                      "'NULL'"
     * H2V2 (MySql)        null       "NULL"                      "'NULL'"
     * H2V2 (MariaDB)      null       "NULL"                      "'NULL'"
     * H2V2 (PSQL)         null       "NULL"                      "'NULL'"
     * H2V2 (Oracle)       null       "NULL"                      "'NULL'"
     * H2V2 (SqlServer)    null       "NULL"                      "'NULL'"
     * ```
     * According to this table there is no simple rule of what is the default value. It should be checked
     * for each DB (or groups of DBs) specifically.
     * In the case of MySql and MariaDB it's also not possible to say whether was default value skipped or
     * explicitly set to `null`.
     *
     * @return `null` - if the value was set to `null` or not configured. `defaultValue` in other case.
     */
    @Suppress("ForbiddenComment")
    // TODO: move to the utility class, rename
    @InternalApi
    protected fun sanitizedDefault(defaultValue: String): String? {
        val dialect = currentDialect
        val h2Mode = dialect.h2Mode
        return when {
            // Check for MariaDB must be before MySql because MariaDBDialect as a class inherits MysqlDialect
            dialect is MariaDBDialect || h2Mode == H2CompatibilityMode.MariaDB -> when {
                defaultValue.startsWith("b'") -> defaultValue.substringAfter("b'").trim('\'')
                else -> defaultValue.extractNullAndStringFromDefaultValue()
            }
            // A special case, because MySql returns default string "NULL" as string "NULL", but other DBs return it as "'NULL'"
            dialect is MysqlDialect && defaultValue == "NULL" -> defaultValue
            dialect is MysqlDialect || h2Mode == H2CompatibilityMode.MySQL -> when {
                defaultValue.startsWith("b'") -> defaultValue.substringAfter("b'").trim('\'')
                else -> defaultValue.extractNullAndStringFromDefaultValue()
            }
            dialect is SQLServerDialect -> defaultValue.trim('(', ')').extractNullAndStringFromDefaultValue()
            dialect is OracleDialect -> defaultValue.trim().extractNullAndStringFromDefaultValue()
            else -> defaultValue.extractNullAndStringFromDefaultValue()
        }
    }

    private fun String.extractNullAndStringFromDefaultValue() = when {
        this.startsWith("NULL") -> null
        this.startsWith('\'') && this.endsWith('\'') -> this.trim('\'')
        else -> this
    }

    /** Extracts result data about a specific column as [ColumnMetadata]. */
    @InternalApi
    protected fun RowApi.asColumnMetadata(prefetchedColumnTypes: Map<String, String> = emptyMap()): ColumnMetadata {
        val defaultDbValue = getObject("COLUMN_DEF", java.lang.String::class.java)?.toString()?.let {
            sanitizedDefault(it)
        }
        val autoIncrement = getObject("IS_AUTOINCREMENT", java.lang.String::class.java)?.toString() == "YES"
        val type = getObject("DATA_TYPE")?.toString()?.toInt() ?: 0
        val name = getStringOrThrow("COLUMN_NAME")
        val nullable = getObject("NULLABLE")?.toString()?.lowercase() in listOf("true", "1")
        val size = getObject("COLUMN_SIZE")?.toString()?.toInt().takeIf { it != 0 }
        val scale = getObject("DECIMAL_DIGITS")?.toString()?.toInt().takeIf { it != 0 }
        val sqlType = getColumnType(this, prefetchedColumnTypes)

        return ColumnMetadata(name, type, sqlType, nullable, size, scale, autoIncrement, defaultDbValue?.takeIf { !autoIncrement })
    }

    private fun RowApi.getStringOrThrow(
        field: String,
        transform: String.() -> String = { this }
    ): String {
        return getObject(field, java.lang.String::class.java)
            ?.toString()
            ?.transform()
            ?: error("Object retrieved from field $field in current data row is null")
    }

    private fun getColumnType(result: RowApi, prefetchedColumnTypes: Map<String, String>): String {
        if (currentDialect !is H2Dialect) return ""

        val columnName = result.getStringOrThrow("COLUMN_NAME")
        val columnType = prefetchedColumnTypes[columnName]
            ?: result.getStringOrThrow("TYPE_NAME") { uppercase() }
        val dataType = result.getObject("DATA_TYPE")?.toString()?.toInt()
        return if (dataType == Types.ARRAY) {
            val baseType = columnType.substringBefore(" ARRAY")
            normalizedColumnType(baseType) + columnType.replaceBefore(" ARRAY", "")
        } else {
            normalizedColumnType(columnType)
        }
    }

    /** Returns the normalized column type. */
    private fun normalizedColumnType(columnType: String): String {
        val h2Mode = currentDialect.h2Mode
        return when {
            columnType.matches(Regex("CHARACTER VARYING(?:\\(\\d+\\))?")) -> when (h2Mode) {
                H2CompatibilityMode.Oracle -> columnType.replace("CHARACTER VARYING", "VARCHAR2")
                else -> columnType.replace("CHARACTER VARYING", "VARCHAR")
            }
            columnType.matches(Regex("CHARACTER(?:\\(\\d+\\))?")) -> columnType.replace("CHARACTER", "CHAR")
            columnType.matches(Regex("BINARY VARYING(?:\\(\\d+\\))?")) -> when (h2Mode) {
                H2CompatibilityMode.PostgreSQL -> "bytea"
                H2CompatibilityMode.Oracle -> columnType.replace("BINARY VARYING", "RAW")
                else -> columnType.replace("BINARY VARYING", "VARBINARY")
            }
            columnType == "BOOLEAN" -> when (h2Mode) {
                H2CompatibilityMode.SQLServer -> "BIT"
                else -> columnType
            }
            columnType == "BINARY LARGE OBJECT" -> "BLOB"
            columnType == "CHARACTER LARGE OBJECT" -> "CLOB"
            columnType == "INTEGER" && h2Mode != H2CompatibilityMode.Oracle -> "INT"
            else -> columnType
        }
    }
}

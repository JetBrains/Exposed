package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.MariaDBDialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.h2Mode

/**
 * Base class responsible for shared utility methods needed for retrieving and storing information about
 * the underlying driver and associated [database].
 */
abstract class ExposedDatabaseMetadata(val database: String) {
    /** Clears and resets any stored information about the database's current schema to default values. */
    abstract fun resetCurrentScheme()

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

    @InternalApi
    // TODO: drop inner and move to utils package
    protected inner class CachableMapWithDefault<K, V>(
        private val map: MutableMap<K, V> = mutableMapOf(),
        val default: (K) -> V
    ) : Map<K, V> by map {
        override fun get(key: K): V? = map.getOrPut(key) { default(key) }
        override fun containsKey(key: K): Boolean = true
        override fun isEmpty(): Boolean = false

        override val entries: Set<Map.Entry<K, V>>
            get() = throw UnsupportedOperationException(
                "The entries field should not be used on CachableMapWithDefault because the lazy population of the collection for missing keys " +
                    "and entries may lead to inconsistencies between calls."
            )

        override val keys: Set<K>
            get() = throw UnsupportedOperationException(
                "The keys field should not be used on CachableMapWithDefault because the lazy population of the collection for missing keys " +
                    "and keys may lead to inconsistencies between calls."
            )
    }

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
}

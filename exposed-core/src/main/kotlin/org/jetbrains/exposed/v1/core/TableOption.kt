package org.jetbrains.exposed.v1.core

/**
 * Base class for table options that are appended at the very end of CREATE TABLE statements,
 * after the closing parenthesis of the column definitions block.
 *
 * Table options are database-specific settings that appear after the `)` that closes the
 * column definitions and before any WITH clause. Commonly used for MySQL/MariaDB storage engines,
 * character sets, and other vendor-specific options.
 *
 * **Important**: Table options are only applied during table creation and are **not tracked**
 * by Exposed's migration system. Changes to options will not be detected automatically.
 *
 * Example SQL structure:
 * ```sql
 * CREATE TABLE users (
 *     id INT,
 *     name VARCHAR(50)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4  -- Table options appear here
 *   WITH (fillfactor=70)  -- Storage parameters appear here
 * ```
 */
abstract class TableOption {
    /**
     * Returns the SQL string representation of this option.
     */
    abstract fun toSQL(): String

    override fun toString(): String = toSQL()
}

/**
 * MySQL/MariaDB storage engine option.
 *
 * Example:
 * ```kotlin
 * object Users : Table("users") {
 *     override val options = listOf(EngineOption(TableEngine.INNODB))
 * }
 * ```
 *
 * Generates: `ENGINE=InnoDB`
 */
class EngineOption(private val engine: TableEngine) : TableOption() {
    override fun toSQL(): String = "ENGINE=${engine.engineName}"
}

/**
 * Character set and collation option for MySQL/MariaDB.
 *
 * Example:
 * ```kotlin
 * object Users : Table("users") {
 *     override val options = listOf(CharsetOption("utf8mb4", "utf8mb4_unicode_ci"))
 * }
 * ```
 *
 * Generates: `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
 */
class CharsetOption(
    private val charset: String,
    private val collation: String? = null
) : TableOption() {
    override fun toSQL(): String = buildString {
        append("DEFAULT CHARSET=$charset")
        if (collation != null) {
            append(" COLLATE=$collation")
        }
    }
}

/**
 * Generic table option for database-specific settings not covered by specific option types.
 *
 * Use this for vendor-specific options that don't have a dedicated option class.
 *
 * Example:
 * ```kotlin
 * object Users : Table("users") {
 *     override val options = listOf(RawTableOption("ROW_FORMAT=COMPRESSED"))
 * }
 * ```
 */
class RawTableOption(private val sql: String) : TableOption() {
    override fun toSQL(): String = sql
}

/**
 * Common MySQL/MariaDB storage engines.
 */
enum class TableEngine(val engineName: String) {
    /** InnoDB - Default engine with ACID transaction support and foreign keys */
    INNODB("InnoDB"),

    /** MyISAM - Legacy engine for read-heavy workloads without transaction support */
    MYISAM("MyISAM"),

    /** MEMORY - In-memory tables for temporary or cache data */
    MEMORY("MEMORY"),

    /** ARCHIVE - Compressed, archived storage for historical data */
    ARCHIVE("ARCHIVE"),

    /** CSV - Tables stored in CSV format */
    CSV("CSV")
}

package org.jetbrains.exposed.v1.core

/**
 * Base class for table modifiers that are appended at the very end of CREATE TABLE statements,
 * after the closing parenthesis of the column definitions block.
 *
 * Table modifiers are database-specific options that appear after the `)` that closes the
 * column definitions and before any WITH clause. Commonly used for MySQL/MariaDB storage engines,
 * character sets, and other vendor-specific options.
 *
 * **Important**: Table modifiers are only applied during table creation and are **not tracked**
 * by Exposed's migration system. Changes to modifiers will not be detected automatically.
 *
 * Example SQL structure:
 * ```sql
 * CREATE TABLE users (
 *     id INT,
 *     name VARCHAR(50)
 * ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4  -- Table modifiers appear here
 *   WITH (fillfactor=70)  -- Storage parameters appear here
 * ```
 */
abstract class TableModifier {
    /**
     * Returns the SQL string representation of this modifier.
     */
    abstract fun toSQL(): String

    override fun toString(): String = toSQL()
}

/**
 * MySQL/MariaDB storage engine modifier.
 *
 * Example:
 * ```kotlin
 * object Users : Table("users") {
 *     override val modifiers = listOf(EngineModifier(TableEngine.INNODB))
 * }
 * ```
 *
 * Generates: `ENGINE=InnoDB`
 */
class EngineModifier(private val engine: TableEngine) : TableModifier() {
    override fun toSQL(): String = "ENGINE=${engine.engineName}"
}

/**
 * Character set and collation modifier for MySQL/MariaDB.
 *
 * Example:
 * ```kotlin
 * object Users : Table("users") {
 *     override val modifiers = listOf(CharsetModifier("utf8mb4", "utf8mb4_unicode_ci"))
 * }
 * ```
 *
 * Generates: `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
 */
class CharsetModifier(
    private val charset: String,
    private val collation: String? = null
) : TableModifier() {
    override fun toSQL(): String = buildString {
        append("DEFAULT CHARSET=$charset")
        if (collation != null) {
            append(" COLLATE=$collation")
        }
    }
}

/**
 * Generic table modifier for database-specific options not covered by specific modifier types.
 *
 * Use this for vendor-specific options that don't have a dedicated modifier class.
 *
 * Example:
 * ```kotlin
 * object Users : Table("users") {
 *     override val modifiers = listOf(RawTableModifier("ROW_FORMAT=COMPRESSED"))
 * }
 * ```
 */
class RawTableModifier(private val sql: String) : TableModifier() {
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

package org.jetbrains.exposed.v1.core

/**
 * Base class for storage parameters included in the WITH clause at the very end of CREATE TABLE statements,
 * after the closing parenthesis of the column definitions block and after any table modifiers.
 *
 * Storage parameters are database-specific options for configuring table storage behavior.
 * Used by PostgreSQL, SQL Server, and other databases.
 *
 * **Important**: Storage parameters are only applied during table creation and are **not tracked**
 * by Exposed's migration system. Changes to storage parameters will not be detected automatically.
 *
 * Example SQL structure:
 * ```sql
 * CREATE TABLE users (
 *     id INT,
 *     name VARCHAR(50)
 * ) ENGINE=InnoDB  -- Table modifiers appear here
 *   WITH (fillfactor=70, autovacuum_enabled=false)  -- Storage parameters appear here
 * ```
 */
abstract class TableStorageParameter {
    /**
     * Returns the SQL string representation of this parameter for use in a WITH clause.
     */
    abstract fun toSQL(): String

    override fun toString(): String = toSQL()
}

/**
 * PostgreSQL fillfactor parameter.
 *
 * Specifies the percentage of space that table pages should be filled with data.
 * The remaining space is reserved for updates to reduce table bloat.
 *
 * Valid range: 10-100
 *
 * Example:
 * ```kotlin
 * object Users : Table("users") {
 *     override val storageParameters = listOf(FillFactorParameter(70))
 * }
 * ```
 *
 * Generates: `fillfactor=70` (used in `WITH (fillfactor=70)`)
 */
class FillFactorParameter(private val value: Int) : TableStorageParameter() {
    init {
        require(value in 10..100) { "fillfactor must be between 10 and 100, got $value" }
    }

    override fun toSQL(): String = "fillfactor=$value"
}

/**
 * PostgreSQL autovacuum_enabled parameter.
 *
 * Controls whether automatic vacuuming is enabled for this table.
 *
 * Example:
 * ```kotlin
 * object LargeTable : Table("large_table") {
 *     override val storageParameters = listOf(AutovacuumEnabledParameter(false))
 * }
 * ```
 *
 * Generates: `autovacuum_enabled=false`
 */
class AutovacuumEnabledParameter(private val enabled: Boolean) : TableStorageParameter() {
    override fun toSQL(): String = "autovacuum_enabled=$enabled"
}

/**
 * PostgreSQL toast_tuple_target parameter.
 *
 * Controls the minimum tuple length required before TOAST compression is attempted.
 * Larger values may reduce CPU overhead at the cost of storage space.
 *
 * Example:
 * ```kotlin
 * object Documents : Table("documents") {
 *     override val storageParameters = listOf(ToastTupleTargetParameter(8160))
 * }
 * ```
 *
 * Generates: `toast_tuple_target=8160`
 */
class ToastTupleTargetParameter(private val value: Int) : TableStorageParameter() {
    init {
        require(value > 0) { "toast_tuple_target must be positive, got $value" }
    }

    override fun toSQL(): String = "toast_tuple_target=$value"
}

/**
 * Generic storage parameter for database-specific options not covered by specific parameter types.
 *
 * Use this for vendor-specific options that don't have a dedicated parameter class.
 *
 * Example:
 * ```kotlin
 * object Users : Table("users") {
 *     override val storageParameters = listOf(RawStorageParameter("parallel_workers=4"))
 * }
 * ```
 */
class RawStorageParameter(private val sql: String) : TableStorageParameter() {
    override fun toSQL(): String = sql
}

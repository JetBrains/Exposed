package org.jetbrains.exposed.v1.iface.annotations

/**
 * Sets a client-side default value.
 * The value is applied when creating a new entity, not at database level.
 *
 * Example:
 * ```kotlin
 * @ClientDefault("0")
 * var loginCount: Int
 *
 * @ClientDefault("true")
 * var isActive: Boolean
 * ```
 *
 * Note: The value string will be parsed according to the property type.
 *
 * @param value String representation of the default value.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ClientDefault(
    val value: String
)

/**
 * Sets a database-level default expression.
 * This generates SQL DEFAULT clause.
 *
 * Use this for:
 * - Database functions: CurrentTimestamp, CurrentDate, Random, etc.
 * - Literal values that should be enforced at database level
 *
 * Example:
 * ```kotlin
 * @DefaultExpression("CurrentTimestamp")
 * val createdAt: Instant
 *
 * @DefaultExpression("CurrentDate")
 * val dateJoined: LocalDate
 *
 * @DefaultExpression("0")
 * var viewCount: Int
 *
 * @DefaultExpression("Random()")
 * val randomValue: Double
 * ```
 *
 * The expression name should match Exposed's Expression functions:
 * - CurrentTimestamp (Instant/Timestamp columns)
 * - CurrentDate (LocalDate columns)
 * - CurrentDateTime (LocalDateTime columns)
 * - Random() (numeric columns)
 * - Or literal values: "0", "''", "true", etc.
 *
 * @param expression Name of the Exposed expression or literal SQL value.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DefaultExpression(
    val expression: String
)

/**
 * Convenience annotation for CURRENT_TIMESTAMP default.
 * Equivalent to @DefaultExpression("CurrentTimestamp").
 *
 * Example:
 * ```kotlin
 * @CurrentTimestamp
 * val createdAt: Instant
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class CurrentTimestamp

/**
 * Convenience annotation for CURRENT_DATE default.
 * Equivalent to @DefaultExpression("CurrentDate").
 *
 * Example:
 * ```kotlin
 * @CurrentDate
 * val dateJoined: LocalDate
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class CurrentDate

/**
 * Convenience annotation for CURRENT_DATETIME default.
 * Equivalent to @DefaultExpression("CurrentDateTime").
 *
 * Example:
 * ```kotlin
 * @CurrentDateTime
 * val updatedAt: LocalDateTime
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class CurrentDateTime

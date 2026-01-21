package org.jetbrains.exposed.v1.iface.annotations

/**
 * Configures a String column as VARCHAR with specified length.
 *
 * Example:
 * ```kotlin
 * @Varchar(100)
 * var name: String
 * ```
 *
 * @param length Maximum length of the varchar column.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Varchar(
    val length: Int = 255
)

/**
 * Configures a String column as TEXT (unlimited length).
 *
 * Example:
 * ```kotlin
 * @Text
 * var description: String
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Text

/**
 * Configures a BigDecimal column with precision and scale.
 *
 * Example:
 * ```kotlin
 * @Decimal(precision = 10, scale = 2)
 * var price: BigDecimal
 * ```
 *
 * @param precision Total number of digits.
 * @param scale Number of digits after decimal point.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Decimal(
    val precision: Int,
    val scale: Int
)

/**
 * Configures a ByteArray column with maximum length.
 *
 * Example:
 * ```kotlin
 * @Binary(1024)
 * var data: ByteArray
 * ```
 *
 * @param length Maximum length in bytes.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Binary(
    val length: Int
)

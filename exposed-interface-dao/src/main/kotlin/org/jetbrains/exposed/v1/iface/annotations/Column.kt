package org.jetbrains.exposed.v1.iface.annotations

/**
 * Configures basic column properties that apply to all column types.
 *
 * Example:
 * ```kotlin
 * @ExposedEntity
 * interface IUser {
 *     val id: Int
 *
 *     @Column(name = "user_name")
 *     var name: String
 *
 *     @Column(name = "email_address")
 *     var email: String
 * }
 * ```
 *
 * For type-specific configuration, use specialized annotations:
 * - @Varchar(length) for String columns
 * - @Decimal(precision, scale) for BigDecimal columns
 * - etc.
 *
 * @param name Column name in database. Defaults to property name in snake_case.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Column(
    /**
     * Column name in database. Defaults to property name in snake_case.
     */
    val name: String = ""
)

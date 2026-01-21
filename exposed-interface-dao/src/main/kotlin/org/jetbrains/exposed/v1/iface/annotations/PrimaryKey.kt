package org.jetbrains.exposed.v1.iface.annotations

/**
 * Marks a property as the primary key.
 *
 * Example:
 * ```kotlin
 * @ExposedEntity
 * interface IUser {
 *     @PrimaryKey
 *     val id: Int
 *     var name: String
 * }
 * ```
 *
 * Note: If not explicitly marked, a property named `id` will be automatically treated as the primary key.
 *
 * @param autoIncrement Auto-increment this key.
 * @param columnName Column name. Defaults to property name.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PrimaryKey(
    /**
     * Auto-increment this key.
     */
    val autoIncrement: Boolean = true,

    /**
     * Column name. Defaults to property name.
     */
    val columnName: String = ""
)

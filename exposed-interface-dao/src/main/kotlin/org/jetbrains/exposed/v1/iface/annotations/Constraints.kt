package org.jetbrains.exposed.v1.iface.annotations

/**
 * Marks a column as unique.
 *
 * Example:
 * ```kotlin
 * @Unique
 * var email: String
 * ```
 *
 * For composite unique constraints, use @UniqueIndex on the entity class.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Unique

/**
 * Creates an index on this column.
 *
 * Example:
 * ```kotlin
 * @Index
 * var username: String
 *
 * @Index(name = "idx_created_at")
 * var createdAt: Instant
 * ```
 *
 * @param name Optional index name. If not specified, a name will be auto-generated.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Index(
    val name: String = ""
)

/**
 * Creates a composite unique index on multiple columns.
 *
 * Example:
 * ```kotlin
 * @ExposedEntity
 * @UniqueIndex(columns = ["userId", "roleId"])
 * interface IUserRole {
 *     val id: Int
 *     var userId: Int
 *     var roleId: Int
 * }
 * ```
 *
 * @param columns Array of property names to include in the unique index.
 * @param name Optional index name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class UniqueIndex(
    val columns: Array<String>,
    val name: String = ""
)

/**
 * Creates a composite index on multiple columns.
 *
 * Example:
 * ```kotlin
 * @ExposedEntity
 * @CompositeIndex(columns = ["lastName", "firstName"])
 * interface IUser {
 *     val id: Int
 *     var firstName: String
 *     var lastName: String
 * }
 * ```
 *
 * @param columns Array of property names to include in the index.
 * @param name Optional index name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class CompositeIndex(
    val columns: Array<String>,
    val name: String = ""
)

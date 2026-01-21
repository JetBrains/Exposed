package org.jetbrains.exposed.v1.iface.annotations

/**
 * Marks an interface as an Exposed entity.
 *
 * KSP will generate:
 * - Table object
 * - Entity implementation class
 * - Immutable data class variant
 * - Companion object methods (findById, findAll, etc.)
 * - Extension functions (save, delete, etc.)
 *
 * Example:
 * ```kotlin
 * @ExposedEntity(tableName = "users")
 * interface IUser {
 *     val id: Int
 *     var name: String
 * }
 * ```
 *
 * @param tableName Table name in database. Defaults to interface name without 'I' prefix, pluralized.
 * @param schema Schema name if applicable.
 * @param generateDataClass Generate immutable data class variant.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ExposedEntity(
    /**
     * Table name in database. Defaults to interface name without 'I' prefix, in snake_case.
     * For example, `IUser` becomes `user`, `IUserProfile` becomes `user_profile`.
     */
    val tableName: String = "",

    /**
     * Schema name if applicable.
     */
    val schema: String = "",

    /**
     * Generate immutable data class variant.
     */
    val generateDataClass: Boolean = true
)

package org.jetbrains.exposed.v1.core

/**
 * API marked with this annotation is experimental.
 * Any behavior associated with its use is not guaranteed to be stable.
 */
@RequiresOptIn(
    message = "This database migration API is experimental. " +
        "Its usage must be marked with '@OptIn(org.jetbrains.exposed.v1.sql.ExperimentalDatabaseMigrationApi::class)' " +
        "or '@org.jetbrains.exposed.v1.sql.ExperimentalDatabaseMigrationApi'."
)
@Target(AnnotationTarget.FUNCTION)
annotation class ExperimentalDatabaseMigrationApi

/**
 * API marked with this annotation is experimental.
 * Any behavior associated with its use is not guaranteed to be stable.
 */
@RequiresOptIn(
    message = "This API is experimental and the behavior defined by setting this value to 'true' is now the default. " +
        "Its usage must be marked with '@OptIn(org.jetbrains.exposed.v1.sql.ExperimentalKeywordApi::class)' " +
        "or '@org.jetbrains.exposed.v1.sql.ExperimentalKeywordApi'."
)
@Target(AnnotationTarget.PROPERTY)
annotation class ExperimentalKeywordApi

/**
 * API marked with this annotation is internal and should not be used outside Exposed.
 * It may be changed or removed in the future without notice.
 * Using it outside Exposed may result in undefined and unexpected behaviour.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal in Exposed and should not be used. It may be changed or removed in the future without notice."
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS
)
annotation class InternalApi

package org.jetbrains.exposed.r2dbc.dao

/**
 * API marked with this annotation is experimental.
 * The shape of the R2DBC DAO API may change in incompatible ways while it stabilizes.
 *
 * Opt in either by annotating the call site with
 * `@OptIn(org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi::class)` /
 * `@org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi`,
 * or by adding `-opt-in=org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi` to your Kotlin
 * compiler options.
 */
@RequiresOptIn(
    message = "This is an experimental Exposed R2DBC DAO API. Its shape may change in incompatible ways. " +
        "Opt in with '@OptIn(org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi::class)' " +
        "or '@org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi'."
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS
)
annotation class ExperimentalR2dbcDaoApi

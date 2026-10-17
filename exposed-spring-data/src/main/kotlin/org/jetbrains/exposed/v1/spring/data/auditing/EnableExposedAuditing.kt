package org.jetbrains.exposed.v1.spring.data.auditing

import org.springframework.context.annotation.Import

/**
 * Enables auditing of entities saved through Exposed repositories: properties annotated with
 * [org.springframework.data.annotation.CreatedDate], [org.springframework.data.annotation.LastModifiedDate],
 * [org.springframework.data.annotation.CreatedBy] and [org.springframework.data.annotation.LastModifiedBy]
 * are set when an entity is inserted or updated. On update, the stored created values are kept, also when the saved
 * entity doesn't carry them.
 *
 * @property auditorAwareRef the name of the [org.springframework.data.domain.AuditorAware] bean that provides the current auditor.
 * @property setDates whether the date properties are set.
 * @property modifyOnCreate whether the last modified properties are also set when an entity is inserted.
 * @property dateTimeProviderRef the name of the [org.springframework.data.auditing.DateTimeProvider] bean that provides the current time.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Import(ExposedAuditingRegistrar::class)
annotation class EnableExposedAuditing(
    val auditorAwareRef: String = "",
    val setDates: Boolean = true,
    val modifyOnCreate: Boolean = true,
    val dateTimeProviderRef: String = "",
)

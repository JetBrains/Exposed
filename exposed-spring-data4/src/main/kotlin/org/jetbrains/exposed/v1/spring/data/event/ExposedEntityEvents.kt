package org.jetbrains.exposed.v1.spring.data.event

import org.jetbrains.exposed.v1.core.Table
import org.springframework.context.ApplicationEvent

/**
 * Base class of the lifecycle events of Exposed repositories. Each event is published right after the matching entity
 * callback and carries the [entity] that callback returned, together with the [table] of the repository.
 */
abstract class ExposedEntityEvent<T : Any>(val entity: T, val table: Table) : ApplicationEvent(entity)

/** Published after the [BeforeConvertCallback]s ran. */
class BeforeConvertEvent<T : Any>(entity: T, table: Table) : ExposedEntityEvent<T>(entity, table)

/** Published after the [BeforeSaveCallback]s ran, before the insert or update statement is executed. */
class BeforeSaveEvent<T : Any>(entity: T, table: Table) : ExposedEntityEvent<T>(entity, table)

/** Published after the [AfterSaveCallback]s ran. */
class AfterSaveEvent<T : Any>(entity: T, table: Table) : ExposedEntityEvent<T>(entity, table)

/** Published after the [AfterConvertCallback]s ran. */
class AfterConvertEvent<T : Any>(entity: T, table: Table) : ExposedEntityEvent<T>(entity, table)

/** Published after the [BeforeDeleteCallback]s ran, before the delete statement is executed. */
class BeforeDeleteEvent<T : Any>(entity: T, table: Table) : ExposedEntityEvent<T>(entity, table)

/** Published after the [AfterDeleteCallback]s ran. */
class AfterDeleteEvent<T : Any>(entity: T, table: Table) : ExposedEntityEvent<T>(entity, table)

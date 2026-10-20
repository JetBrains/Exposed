package org.jetbrains.exposed.v1.spring.data.event

import org.jetbrains.exposed.v1.core.Table
import org.springframework.data.mapping.callback.EntityCallback

/**
 * Runs before an entity is converted to column values for an insert or update. The returned entity is the one that is
 * stored, so a callback can change values (e.g. set defaults) by returning a modified copy.
 */
fun interface BeforeConvertCallback<T : Any> : EntityCallback<T> {
    fun onBeforeConvert(entity: T, table: Table): T
}

/** Runs after [BeforeConvertCallback], right before the insert or update statement is executed. */
fun interface BeforeSaveCallback<T : Any> : EntityCallback<T> {
    fun onBeforeSave(entity: T, table: Table): T
}

/** Runs after the insert or update statement was executed. The returned entity is what `save` returns. */
fun interface AfterSaveCallback<T : Any> : EntityCallback<T> {
    fun onAfterSave(entity: T, table: Table): T
}

/** Runs after a row was converted to the entity type. It isn't called for DTO, interface or `ResultRow` projections. */
fun interface AfterConvertCallback<T : Any> : EntityCallback<T> {
    fun onAfterConvert(entity: T, table: Table): T
}

/** Runs before an entity passed to `delete` or `deleteAll(entities)` is deleted. */
fun interface BeforeDeleteCallback<T : Any> : EntityCallback<T> {
    fun onBeforeDelete(entity: T, table: Table): T
}

/** Runs after an entity passed to `delete` or `deleteAll(entities)` was deleted. */
fun interface AfterDeleteCallback<T : Any> : EntityCallback<T> {
    fun onAfterDelete(entity: T, table: Table): T
}

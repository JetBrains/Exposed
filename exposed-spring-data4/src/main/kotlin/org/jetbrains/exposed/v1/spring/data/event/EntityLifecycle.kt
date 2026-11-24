package org.jetbrains.exposed.v1.spring.data.event

import org.jetbrains.exposed.v1.core.Table
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mapping.callback.EntityCallback
import org.springframework.data.mapping.callback.EntityCallbacks

/**
 * Runs the entity callbacks of one repository and publishes the matching [ExposedEntityEvent] right after them.
 *
 * Every step returns the entity the callbacks returned, which is the one to go on with. Without [callbacks] and
 * [publisher] every step returns the passed entity.
 */
internal class EntityLifecycle<T : Any>(
    private val table: Table,
    private val callbacks: EntityCallbacks? = null,
    private val publisher: ApplicationEventPublisher? = null,
) {
    fun beforeConvert(entity: T): T = run(entity, BeforeConvertCallback::class.java, ::BeforeConvertEvent)

    fun beforeSave(entity: T): T = run(entity, BeforeSaveCallback::class.java, ::BeforeSaveEvent)

    fun afterSave(entity: T): T = run(entity, AfterSaveCallback::class.java, ::AfterSaveEvent)

    fun afterConvert(entity: T): T = run(entity, AfterConvertCallback::class.java, ::AfterConvertEvent)

    fun beforeDelete(entity: T): T = run(entity, BeforeDeleteCallback::class.java, ::BeforeDeleteEvent)

    fun afterDelete(entity: T): T = run(entity, AfterDeleteCallback::class.java, ::AfterDeleteEvent)

    private fun run(entity: T, callbackType: Class<out EntityCallback<*>>, event: (T, Table) -> ExposedEntityEvent<T>): T {
        val result: T = callbacks?.callback(callbackType, entity, table) ?: entity
        publisher?.publishEvent(event(result, table))
        return result
    }
}

package org.example.entities

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.example.tables.BaseTable
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.dao.r2dbc.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

abstract class BaseEntityClass<out E : BaseEntity>(
    table: BaseTable
) : IntEntityClass<E>(table) {
    init {
        EntityHook.subscribe { change ->
            val changedEntity = change.toEntity(this)
            when (val type = change.changeType) {
                EntityChangeType.Updated -> {
                    val now = nowUTC()
                    changedEntity?.let {
                        // R2DBC DAO stages property writes in the transaction's entity cache.
                        it.modified = now
                    }
                    logChange(changedEntity, type, now)
                }
                else -> logChange(changedEntity, type)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun nowUTC() = Clock.System.now().toLocalDateTime(TimeZone.UTC)

    private fun logChange(entity: E?, type: EntityChangeType, dateTime: LocalDateTime? = null) {
        entity?.let {
            val entityClassName = this::class.java.enclosingClass.simpleName
            exposedLogger.info(
                "$entityClassName(${it.id}) ${type.name.lowercase()} at ${dateTime ?: nowUTC()}"
            )
        }
    }
}

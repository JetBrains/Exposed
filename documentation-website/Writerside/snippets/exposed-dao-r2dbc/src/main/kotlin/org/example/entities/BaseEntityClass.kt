package org.example.entities

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.example.tables.BaseTable
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.dao.r2dbc.EntityChangeType
import org.jetbrains.exposed.v1.dao.r2dbc.EntityHook
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.toEntity
import kotlin.let
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

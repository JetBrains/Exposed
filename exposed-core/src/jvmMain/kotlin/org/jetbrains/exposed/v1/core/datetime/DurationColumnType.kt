package org.jetbrains.exposed.v1.core.datetime

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Base column type for storing duration/time interval values.
 *
 * This abstract class handles columns that store time durations or intervals,
 * representing spans of time rather than specific moments. Durations are stored
 * as nanosecond values for maximum precision and cross-database compatibility.
 *
 * @param T The application-specific duration type (e.g., [kotlin.time.Duration])
 * @see KotlinDurationColumnType
 */
abstract class DurationColumnType<T> : ColumnType<T>() {
    abstract fun toDuration(value: T): Duration

    abstract fun fromDuration(value: Duration): T

    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun nonNullValueToString(value: T & Any): String {
        return "'${toDuration(value).inWholeNanoseconds}'"
    }

    private fun durationValueFromDB(value: Any): Duration = when (value) {
        Duration.INFINITE.inWholeNanoseconds -> Duration.INFINITE
        is Long -> value.nanoseconds
        is Number -> durationValueFromDB(value.toLong())
        is String -> Duration.parse(value)
        else -> durationValueFromDB(value.toString())
    }

    override fun valueFromDB(value: Any): T? = fromDuration(durationValueFromDB(value))

    override fun notNullValueToDB(value: T & Any): Any = toDuration(value).inWholeNanoseconds
}

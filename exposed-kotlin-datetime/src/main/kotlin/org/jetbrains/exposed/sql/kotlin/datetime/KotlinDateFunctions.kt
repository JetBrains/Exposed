@file:Suppress("FunctionName")

package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

internal class DateInternal(val expr: Expression<*>) : Function<LocalDate>(KotlinLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("DATE(", expr, ")") }
}

@JvmName("LocalDateDateFunction")
fun <T : LocalDate?> Date(expr: Expression<T>): Function<LocalDate> = DateInternal(expr)
@JvmName("LocalDateTimeDateFunction")
fun <T : LocalDateTime?> Date(expr: Expression<T>): Function<LocalDate> = DateInternal(expr)
@JvmName("InstantDateFunction")
fun <T : Instant?> Date(expr: Expression<T>): Function<LocalDate> = DateInternal(expr)

internal class TimeFunction(val expr: Expression<*>) : Function<LocalTime>(KotlinLocalTimeColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("Time(", expr, ")") }
}

@JvmName("LocalDateTimeFunction")
fun <T : LocalDate?> Time(expr: Expression<T>): Function<LocalTime> = TimeFunction(expr)
@JvmName("LocalDateTimeTimeFunction")
fun <T : LocalDateTime?> Time(expr: Expression<T>): Function<LocalTime> = TimeFunction(expr)
@JvmName("InstantTimeFunction")
fun <T : Instant?> Time(expr: Expression<T>): Function<LocalTime> = TimeFunction(expr)

object CurrentDateTime : Function<LocalDateTime>(KotlinLocalDateTimeColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }

    @Deprecated(
        message = "This class is now a singleton, no need for its constructor call; " +
            "this method is provided for backward-compatibility only, and will be removed in future releases",
        replaceWith = ReplaceWith("this"),
        level = DeprecationLevel.ERROR,
    )
    operator fun invoke() = this
}

object CurrentDate : Function<LocalDate>(KotlinLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when (currentDialect) {
            is MysqlDialect -> "CURRENT_DATE()"
            is SQLServerDialect -> "GETDATE()"
            else -> "CURRENT_DATE"
        }
    }
}

class CurrentTimestamp<T> : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

class YearInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.year(expr, queryBuilder)
    }
}

@JvmName("LocalDateYearFunction")
fun <T : LocalDate?> Year(expr: Expression<T>): Function<Int> = YearInternal(expr)
@JvmName("LocalDateTimeYearFunction")
fun <T : LocalDateTime?> Year(expr: Expression<T>): Function<Int> = YearInternal(expr)
@JvmName("InstantYearFunction")
fun <T : Instant?> Year(expr: Expression<T>): Function<Int> = YearInternal(expr)

internal class MonthInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.month(expr, queryBuilder)
    }
}

@JvmName("LocalDateMonthFunction")
fun <T : LocalDate?> Month(expr: Expression<T>): Function<Int> = MonthInternal(expr)
@JvmName("LocalDateTimeMonthFunction")
fun <T : LocalDateTime?> Month(expr: Expression<T>): Function<Int> = MonthInternal(expr)
@JvmName("InstantMonthFunction")
fun <T : Instant?> Month(expr: Expression<T>): Function<Int> = MonthInternal(expr)

internal class DayInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.day(expr, queryBuilder)
    }
}

@JvmName("LocalDateDayFunction")
fun <T : LocalDate?> Day(expr: Expression<T>): Function<Int> = DayInternal(expr)
@JvmName("LocalDateTimeDayFunction")
fun <T : LocalDateTime?> Day(expr: Expression<T>): Function<Int> = DayInternal(expr)
@JvmName("InstantDayFunction")
fun <T : Instant?> Day(expr: Expression<T>): Function<Int> = DayInternal(expr)

internal class HourInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.hour(expr, queryBuilder)
    }
}

@JvmName("LocalDateHourFunction")
fun <T : LocalDate?> Hour(expr: Expression<T>): Function<Int> = HourInternal(expr)
@JvmName("LocalDateTimeHourFunction")
fun <T : LocalDateTime?> Hour(expr: Expression<T>): Function<Int> = HourInternal(expr)
@JvmName("InstantHourFunction")
fun <T : Instant?> Hour(expr: Expression<T>): Function<Int> = HourInternal(expr)

internal class MinuteInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.minute(expr, queryBuilder)
    }
}

@JvmName("LocalDateMinuteFunction")
fun <T : LocalDate?> Minute(expr: Expression<T>): Function<Int> = MinuteInternal(expr)
@JvmName("LocalDateTimeMinuteFunction")
fun <T : LocalDateTime?> Minute(expr: Expression<T>): Function<Int> = MinuteInternal(expr)
@JvmName("InstantMinuteFunction")
fun <T : Instant?> Minute(expr: Expression<T>): Function<Int> = MinuteInternal(expr)

internal class SecondInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.second(expr, queryBuilder)
    }
}

@JvmName("LocalDateSecondFunction")
fun <T : LocalDate?> Second(expr: Expression<T>): Function<Int> = SecondInternal(expr)
@JvmName("LocalDateTimeSecondFunction")
fun <T : LocalDateTime?> Second(expr: Expression<T>): Function<Int> = SecondInternal(expr)
@JvmName("InstantSecondFunction")
fun <T : Instant?> Second(expr: Expression<T>): Function<Int> = SecondInternal(expr)

// Extention

@JvmName("LocalDateDateExt")
fun <T : LocalDate?> Expression<T>.date() = Date(this)
@JvmName("LocalDateTimeDateExt")
fun <T : LocalDateTime?> Expression<T>.date() = Date(this)
@JvmName("InstantDateExt")
fun <T : Instant?> Expression<T>.date() = Date(this)

@JvmName("LocalDateYearExt")
fun <T : LocalDate?> Expression<T>.year() = Year(this)
@JvmName("LocalDateTimeYearExt")
fun <T : LocalDateTime?> Expression<T>.year() = Year(this)
@JvmName("InstantYearExt")
fun <T : Instant?> Expression<T>.year() = Year(this)

@JvmName("LocalDateMonthExt")
fun <T : LocalDate?> Expression<T>.month() = Month(this)
@JvmName("LocalDateTimeMonthExt")
fun <T : LocalDateTime?> Expression<T>.month() = Month(this)
@JvmName("InstantMonthExt")
fun <T : Instant?> Expression<T>.month() = Month(this)

@JvmName("LocalDateDayExt")
fun <T : LocalDate?> Expression<T>.day() = Day(this)
@JvmName("LocalDateTimeDayExt")
fun <T : LocalDateTime?> Expression<T>.day() = Day(this)
@JvmName("InstantDayExt")
fun <T : Instant?> Expression<T>.day() = Day(this)

@JvmName("LocalDateHourExt")
fun <T : LocalDate?> Expression<T>.hour() = Hour(this)
@JvmName("LocalDateTimeHourExt")
fun <T : LocalDateTime?> Expression<T>.hour() = Hour(this)
@JvmName("InstantHourExt")
fun <T : Instant?> Expression<T>.hour() = Hour(this)

@JvmName("LocalDateMinuteExt")
fun <T : LocalDate?> Expression<T>.minute() = Minute(this)
@JvmName("LocalDateTimeMinuteExt")
fun <T : LocalDateTime?> Expression<T>.minute() = Minute(this)
@JvmName("InstantMinuteExt")
fun <T : Instant?> Expression<T>.minute() = Minute(this)

@JvmName("LocalDateSecondExt")
fun <T : LocalDate?> Expression<T>.second() = Second(this)
@JvmName("LocalDateTimeSecondExt")
fun <T : LocalDateTime?> Expression<T>.second() = Second(this)
@JvmName("InstantSecondExt")
fun <T : Instant?> Expression<T>.second() = Second(this)

fun dateParam(value: LocalDate): Expression<LocalDate> = QueryParameter(value, KotlinLocalDateColumnType.INSTANCE)
fun timeParam(value: LocalTime): Expression<LocalTime> = QueryParameter(value, KotlinLocalTimeColumnType.INSTANCE)
fun dateTimeParam(value: LocalDateTime): Expression<LocalDateTime> = QueryParameter(value, KotlinLocalDateTimeColumnType.INSTANCE)
fun timestampParam(value: Instant): Expression<Instant> = QueryParameter(value, KotlinInstantColumnType.INSTANCE)
fun durationParam(value: Duration): Expression<Duration> = QueryParameter(value, KotlinDurationColumnType.INSTANCE)

fun dateLiteral(value: LocalDate): LiteralOp<LocalDate> = LiteralOp(KotlinLocalDateColumnType.INSTANCE, value)
fun timeLiteral(value: LocalTime): LiteralOp<LocalTime> = LiteralOp(KotlinLocalTimeColumnType.INSTANCE, value)
fun dateTimeLiteral(value: LocalDateTime): LiteralOp<LocalDateTime> = LiteralOp(KotlinLocalDateTimeColumnType.INSTANCE, value)

fun timestampLiteral(value: Instant): LiteralOp<Instant> = LiteralOp(KotlinInstantColumnType.INSTANCE, value)
fun durationLiteral(value: Duration): LiteralOp<Duration> = LiteralOp(KotlinDurationColumnType.INSTANCE, value)

fun CustomDateFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalDate?> =
    CustomFunction(functionName, KotlinLocalDateColumnType.INSTANCE, *params)

fun CustomTimeFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalTime?> =
    CustomFunction(functionName, KotlinLocalTimeColumnType.INSTANCE, *params)

fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalDateTime?> =
    CustomFunction(functionName, KotlinLocalDateTimeColumnType.INSTANCE, *params)

fun CustomTimeStampFunction(functionName: String, vararg params: Expression<*>): CustomFunction<Instant?> =
    CustomFunction(functionName, KotlinInstantColumnType.INSTANCE, *params)

fun CustomDurationFunction(functionName: String, vararg params: Expression<*>): CustomFunction<Duration?> =
    CustomFunction(functionName, KotlinDurationColumnType.INSTANCE, *params)

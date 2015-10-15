package kotlin.sql

import org.joda.time.DateTime

fun date(date: String): DateTime {
    return DateTime.parse(date)
}

fun today(): DateTime {
    return DateTime.now().withTimeAtStartOfDay()
}

fun Long.toDate(): DateTime? {
    if (this <= 20000101 || 99999999 < this)
        return null

    val day = (this % 100).toInt()
    val month = ((this / 100) % 100).toInt()
    val year = (this / 10000).toInt()

    try {
        return DateTime(year, month, day, 0, 0)
    } catch (e: Exception) {
        return null
    }
}

val MAX_DATE = date("3333-01-01")

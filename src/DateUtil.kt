package kotlin.sql

import org.joda.time.DateTime

fun date(date: String): DateTime {
    return DateTime.parse(date)
}

fun today(): DateTime {
    return DateTime.now().withTimeAtStartOfDay()
}

val MAX_DATE = date("3333-01-01")

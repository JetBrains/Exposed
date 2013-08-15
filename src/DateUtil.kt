import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.sql.Date as sqlDate

fun sqlDate (date: Date) : sqlDate {
    return java.sql.Date(date.getYear(), date.getMonth(), date.getDate())
}

fun date(date: String) : sqlDate {
    var simpleDate = SimpleDateFormat("yyyy-MM-dd").parse(date)!!
    return sqlDate(simpleDate)
}

fun today() : sqlDate {
    val today = Calendar.getInstance();
    today.clear(Calendar.HOUR); today.clear(Calendar.MINUTE); today.clear(Calendar.SECOND);
    val todayDate = today.getTime()
    return sqlDate(todayDate)
}

val MAX_DATE = sqlDate(Date (java.lang.Long.MAX_VALUE))

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

fun sqlDate.addDays (days: Int) : sqlDate {
    val c = Calendar.getInstance()
    c.setTime(this) // Now use today date.
    c.add(Calendar.DATE, days); // Adding days
    return sqlDate(c.getTime())
}

val MAX_DATE = date("3333-01-01")

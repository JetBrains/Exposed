package kotlin.dao
import java.sql.Date
import kotlin.sql.*

public open class HistoryEntity (id: Int, table: HistoryTable) : Entity(id) {
    public var start: Date by table.start
    public var end: Date? by table.end

    public fun isValidBy (date: Date? = null) : Boolean {
        if (date == null) return end == null
        return start <= date && (end == null || date < end!!)
    }

    public fun close(date: Date = today()) {
        with (Session.get()) {
            end = date
        }
    }
}

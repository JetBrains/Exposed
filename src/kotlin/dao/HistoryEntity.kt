package kotlin.dao
import org.joda.time.DateTime

public open class HistoryEntity (id: EntityID, table: HistoryTable) : Entity(id) {
    public var start: DateTime by table.start
    public var end: DateTime? by table.end

    public open fun isValidBy (date: DateTime? = null) : Boolean {
        if (date == null) return end == null
        return start <= date && (end == null || date < end!!)
    }

    public fun close(date: DateTime = DateTime.now()) {
        end = date
    }
}

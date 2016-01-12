package kotlin.dao
import org.joda.time.DateTime

open class HistoryEntity (id: EntityID, table: HistoryTable) : Entity(id) {
    var start: DateTime by table.start
    var end: DateTime? by table.end

    open fun isValidBy (date: DateTime? = null) : Boolean {
        if (date == null) return end == null
        return start <= date && (end == null || date < end!!)
    }

    fun close(date: DateTime = DateTime.now()) {
        end = date
    }
}

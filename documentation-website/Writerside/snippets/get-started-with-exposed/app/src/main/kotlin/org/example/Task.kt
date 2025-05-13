import org.jetbrains.exposed.v1.core.Table

const val MAX_VARCHAR_LENGTH = 128

object Tasks : Table("tasks") {
    val id = integer("id").autoIncrement()
    val title = varchar("name", MAX_VARCHAR_LENGTH)
    val description = varchar("description", MAX_VARCHAR_LENGTH)
    val isCompleted = bool("completed").default(false)
}

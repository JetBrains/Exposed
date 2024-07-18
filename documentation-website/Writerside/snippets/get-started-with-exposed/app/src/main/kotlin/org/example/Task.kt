import org.jetbrains.exposed.sql.Table

object Tasks : Table("tasks") {
    val id = integer("id").autoIncrement()
    val title  = varchar("name", 128)
    val description  = varchar("description", 128)
    val isCompleted = bool("completed").default(false)
}

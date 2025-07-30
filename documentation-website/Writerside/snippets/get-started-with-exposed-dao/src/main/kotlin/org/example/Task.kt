package org.example

// ...
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

@Suppress("MagicNumber")
object Tasks : IntIdTable("tasks") {
    val title = varchar("name", 128)
    val description = varchar("description", 128)
    val isCompleted = bool("completed").default(false)
}

// ...

class Task(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Task>(Tasks)

    var title by Tasks.title
    var description by Tasks.description
    var isCompleted by Tasks.isCompleted

    override fun toString(): String {
        return "Task(id=$id, title=$title, completed=$isCompleted)"
    }
}

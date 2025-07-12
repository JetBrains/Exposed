package org.example

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun main() {
    Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

    transaction {
        addLogger(StdOutSqlLogger)

        // ...

        SchemaUtils.create(Tasks)

        val task1 = Task.new {
            title = "Learn Exposed DAO"
            description = "Follow the DAO tutorial"
        }

        val task2 = Task.new {
            title = "Read The Hobbit"
            description = "Read chapter one"
            isCompleted = true
        }

        println("Created new tasks with ids ${task1.id} and ${task2.id}")

        val completed = Task.find { Tasks.isCompleted eq true }
        println("Completed tasks: ${completed.count()}")

        // Update
        task1.isCompleted = true
        println("Updated task1: $task1")

        // Delete
        task2.delete()
        println("Remaining tasks: ${Task.all().toList()}")
    }
}

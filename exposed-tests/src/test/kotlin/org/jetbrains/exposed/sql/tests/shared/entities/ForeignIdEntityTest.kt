package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test

/**
 * A case when a table's primary key is a foreign key to some other table (ProjectConfigs.id -> Project.id)
 */
class ForeignIdEntityTest : DatabaseTestsBase() {

    object Schema {

        object Projects : LongIdTable() {
            val name = varchar("name", 50)
        }

        object ProjectConfigs : IdTable<Long>() {
            override val id = reference("id", Projects)
            val setting = bool("setting")
        }

    }

    class Project(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<Project>(Schema.Projects)

        var name by Schema.Projects.name
    }

    class ProjectConfig(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<ProjectConfig>(Schema.ProjectConfigs)

        var setting by Schema.ProjectConfigs.setting
    }

    @Test
    fun foreignIdEntityUpdate() {
        // reproducer for https://github.com/JetBrains/Exposed/issues/880
        withTables(Schema.Projects, Schema.ProjectConfigs) {
            db.useNestedTransactions = true

            transaction {
                val projectId = Project.new { name = "Space" }.id.value
                ProjectConfig.new(projectId) { setting = true }
            }

            transaction {
                ProjectConfig.all().first().setting = false
            }

            transaction {
                assertFalse(ProjectConfig.all().first().setting)
            }

        }
    }

}

package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertContentEquals

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

        object Actors : IdTable<String>("actors") {
            override val id = varchar("guild_id", 13).entityId()
            override val primaryKey = PrimaryKey(id)
        }

        object Roles : IntIdTable("roles") {
            val actor = reference("guild_id", Actors)
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

    class Actor(id: EntityID<String>) : Entity<String>(id) {
        companion object : EntityClass<String, Actor>(Schema.Actors)
        val roles by Role referrersOn Schema.Roles.actor
    }

    class Role(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Role>(Schema.Roles)
        var actor by Actor referencedOn Schema.Roles.actor
    }

    @Test
    fun foreignIdEntityUpdate() {
        // reproducer for https://github.com/JetBrains/Exposed/issues/880
        withTables(Schema.Projects, Schema.ProjectConfigs, configure = { useNestedTransactions = true }) {
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

    @Test
    fun testReferencedEntitiesWithIdenticalColumnNames() {
        withTables(Schema.Actors, Schema.Roles) {
            val actorA = Actor.new("3746529") { }
            val roleA = Role.new { actor = actorA }
            val roleB = Role.new { actor = actorA }

            assertContentEquals(listOf(roleA, roleB), actorA.roles)
        }
    }
}

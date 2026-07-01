package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.r2dbc.dao.LongEntity
import org.jetbrains.exposed.r2dbc.dao.LongEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ForeignIdEntityTest : R2dbcDatabaseTestsBase() {
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

        val actor by Actor referencedOn Schema.Roles.actor
    }

    @Test
    fun foreignIdEntityUpdate() {
        // reproducer for https://github.com/JetBrains/Exposed/issues/880
        withTables(Schema.Projects, Schema.ProjectConfigs, configure = { useNestedTransactions = true }) {
            suspendTransaction {
                val projectId = Project.new { name = "Space" }.flush().id.value
                ProjectConfig.new(projectId) { setting = true }
            }

            suspendTransaction {
                ProjectConfig.all().first().setting = false
            }

            suspendTransaction {
                assertFalse(ProjectConfig.all().first().setting)
            }
        }
    }

    @Test
    fun testReferencedEntitiesWithIdenticalColumnNames() {
        withTables(Schema.Actors, Schema.Roles) {
            val actorA = Actor.new("3746529") { }.flush()
            val roleA = Role.new { actor.set(actorA) }.flush()
            val roleB = Role.new { actor.set(actorA) }.flush()

            assertContentEquals(listOf(roleA, roleB), actorA.roles.toList())
        }
    }
}

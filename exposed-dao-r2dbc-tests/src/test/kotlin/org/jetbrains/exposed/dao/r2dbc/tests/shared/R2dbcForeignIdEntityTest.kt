package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.LongR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.LongR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertContentEquals

class R2dbcForeignIdEntityTest : R2dbcDatabaseTestsBase() {
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

    class Project(id: EntityID<Long>) : LongR2dbcEntity(id) {
        companion object : LongR2dbcEntityClass<Project>(Schema.Projects)

        var name by Schema.Projects.name
    }

    class ProjectConfig(id: EntityID<Long>) : LongR2dbcEntity(id) {
        companion object : LongR2dbcEntityClass<ProjectConfig>(Schema.ProjectConfigs)

        var setting by Schema.ProjectConfigs.setting
    }

    class Actor(id: EntityID<String>) : R2dbcEntity<String>(id) {
        companion object : R2dbcEntityClass<String, Actor>(Schema.Actors)

        val roles by Role referrersOnSuspend Schema.Roles.actor
    }

    class Role(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Role>(Schema.Roles)

        val actor by Actor referencedOnSuspend Schema.Roles.actor
    }

    @Test
    fun foreignIdEntityUpdate() {
        // reproducer for https://github.com/JetBrains/Exposed/issues/880
        withTables(Schema.Projects, Schema.ProjectConfigs, configure = { useNestedTransactions = true }) {
            suspendTransaction {
                // TODO we definitely need `newAndFlush()` alternative to the `new()` method
                val projectId = Project.new { name = "Space" }.also { entityCache.flush() }.id.value
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
            val actorA = Actor.new("3746529") { }
            val roleA = Role.new { actor set actorA }
            val roleB = Role.new { actor set actorA }

            assertContentEquals(listOf(roleA, roleB), actorA.roles().toList())
        }
    }
}

package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.any
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.r2dbc.Entity
import org.jetbrains.exposed.v1.dao.r2dbc.EntityClass
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.update
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

class NonAutoIncEntities : R2dbcDatabaseTestsBase() {
    abstract class BaseNonAutoIncTable(name: String) : IdTable<Int>(name) {
        override val id = integer("id").entityId()
        val b1 = bool("b1")
    }

    object NotAutoIntIdTable : BaseNonAutoIncTable("") {
        val defaultedInt = integer("i1")
    }

    class NotAutoEntity(id: EntityID<Int>) : Entity<Int>(id) {
        var b1 by NotAutoIntIdTable.b1
        var defaultedInNew by NotAutoIntIdTable.defaultedInt

        companion object : EntityClass<Int, NotAutoEntity>(NotAutoIntIdTable) {
            val lastId = AtomicInteger(0)
            internal const val defaultInt = 42
            suspend fun new(b: Boolean) = newSuspend(lastId.incrementAndGet()) { b1 = b }

            override suspend fun newSuspend(id: Int?, init: suspend NotAutoEntity.() -> Unit): NotAutoEntity {
                return super.newSuspend(id ?: lastId.incrementAndGet()) {
                    defaultedInNew = defaultInt
                    init()
                }
            }
        }
    }

    @Test
    fun testDefaultsWithOverrideNew() {
        withTables(NotAutoIntIdTable) {
            val entity1 = NotAutoEntity.new(true)
            assertEquals(true, entity1.b1)
            assertEquals(NotAutoEntity.defaultInt, entity1.defaultedInNew)

            val entity2 = NotAutoEntity.newSuspend {
                b1 = false
                defaultedInNew = 1
            }
            assertEquals(false, entity2.b1)
            assertEquals(1, entity2.defaultedInNew)
        }
    }

    @Test
    fun testNotAutoIncTable() {
        withTables(NotAutoIntIdTable) {
            val e1 = NotAutoEntity.new(true)
            val e2 = NotAutoEntity.new(false)

            val all = NotAutoEntity.all()
            assert(all.any { it.id == e1.id })
            assert(all.any { it.id == e2.id })
        }
    }

    object CustomPrimaryKeyColumnTable : IdTable<String>() {
        val customId: Column<String> = varchar("customId", 256)
        override val primaryKey = PrimaryKey(customId)
        override val id: Column<EntityID<String>> = customId.entityId()
    }

    class CustomPrimaryKeyColumnEntity(id: EntityID<String>) : Entity<String>(id) {
        companion object : EntityClass<String, CustomPrimaryKeyColumnEntity>(CustomPrimaryKeyColumnTable)

        var customId by CustomPrimaryKeyColumnTable.customId
    }

    @Test
    fun testIdValueIsTheSameAsCustomPrimaryKeyColumn() {
        withTables(CustomPrimaryKeyColumnTable) {
            val request = CustomPrimaryKeyColumnEntity.newSuspend {
                customId = "customIdValue"
            }

            assertEquals("customIdValue", request.id.value)
        }
    }

    object RequestsTable : IdTable<String>() {
        val requestId = varchar("request_id", 256)
        val deleted = bool("deleted")
        override val primaryKey: PrimaryKey = PrimaryKey(requestId)
        override val id: Column<EntityID<String>> = requestId.entityId()
    }

    class Request(id: EntityID<String>) : Entity<String>(id) {
        companion object : EntityClass<String, Request>(RequestsTable)

        var requestId by RequestsTable.requestId
        var deleted by RequestsTable.deleted

        override suspend fun delete() {
            RequestsTable.update({ RequestsTable.id eq id }) {
                it[deleted] = true
            }
        }
    }

    @Test
    fun testAccessEntityIdFromOverrideEntityMethod() {
        withTables(RequestsTable) {
            val request = Request.newSuspend {
                requestId = "test1"
                deleted = false
            }

            request.delete()

            val updated = Request["test1"]
            assertEquals(true, updated.deleted)
        }
    }
}

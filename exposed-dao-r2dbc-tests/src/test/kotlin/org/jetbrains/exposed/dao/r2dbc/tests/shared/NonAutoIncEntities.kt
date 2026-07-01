package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.any
import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.NewEntity
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
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
            fun new(b: Boolean) = new(lastId.incrementAndGet()) { b1 = b }

            override fun new(id: Int?, init: NotAutoEntity.() -> Unit): NewEntity<Int, NotAutoEntity> {
                return super.new(id ?: lastId.incrementAndGet()) {
                    defaultedInNew = defaultInt
                    init()
                }
            }
        }
    }

    @Test
    fun testDefaultsWithOverrideNew() {
        withTables(NotAutoIntIdTable) {
            val entity1 = NotAutoEntity.new(true).flush()
            assertEquals(true, entity1.b1)
            assertEquals(NotAutoEntity.defaultInt, entity1.defaultedInNew)

            val entity2 = NotAutoEntity.new {
                b1 = false
                defaultedInNew = 1
            }.flush()
            assertEquals(false, entity2.b1)
            assertEquals(1, entity2.defaultedInNew)
        }
    }

    @Test
    fun testNotAutoIncTable() {
        withTables(NotAutoIntIdTable) {
            val e1 = NotAutoEntity.new(true).flush()
            val e2 = NotAutoEntity.new(false).flush()

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
            val request = CustomPrimaryKeyColumnEntity.new {
                customId = "customIdValue"
            }.flush()

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
            val request = Request.new {
                requestId = "test1"
                deleted = false
            }.flush()

            request.delete()

            val updated = Request["test1"]
            assertEquals(true, updated.deleted)
        }
    }
}

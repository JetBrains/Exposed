package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NonAutoIncEntities : DatabaseTestsBase() {

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

            override fun new(id: Int?, init: NotAutoEntity.() -> Unit): NotAutoEntity {
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
            val entity1 = NotAutoEntity.new(true)
            assertEquals(true, entity1.b1)
            assertEquals(NotAutoEntity.defaultInt, entity1.defaultedInNew)

            val entity2 = NotAutoEntity.new {
                b1 = false
                defaultedInNew = 1
            }
            assertEquals(false, entity2.b1)
            assertEquals(1, entity2.defaultedInNew)
        }
    }

    @Test fun testNotAutoIncTable() {
        withTables(NotAutoIntIdTable) {
            val e1 = NotAutoEntity.new(true)
            val e2 = NotAutoEntity.new(false)

            TransactionManager.current().flushCache()

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
            }

            assertEquals("customIdValue", request.id.value)
        }
    }
}

package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

object EntityTestsData {

    object YTable: IdTable<String>("") {
        override val id: Column<EntityID<String>> = varchar("uuid", 36).primaryKey().entityId().clientDefault {
            EntityID(UUID.randomUUID().toString(), YTable)
        }

        val x = bool("x").default(true)
    }

    object XTable: IntIdTable() {
        val b1 = bool("b1").default(true)
        val b2 = bool("b2").default(false)
        val y1 = optReference("y1", YTable)
    }

    class XEntity(id: EntityID<Int>): Entity<Int>(id) {
        var b1 by XTable.b1
        var b2 by XTable.b2

        companion object : EntityClass<Int, XEntity>(XTable) {
        }
    }

    enum class XType {
        A, B
    }

    open class AEntity(id: EntityID<Int>): IntEntity(id) {
        var b1 by XTable.b1

        companion object: IntEntityClass<AEntity>(XTable) {
            fun create(b1: Boolean, type: XType): AEntity {
                val init: AEntity.() -> Unit = {
                    this.b1 = b1
                }
                val answer = when (type) {
                    XType.B -> BEntity.create { init() }
                    else -> new { init() }
                }
                return answer
            }
        }
    }

    class BEntity(id: EntityID<Int>): AEntity(id) {
        var b2 by XTable.b2
        var y by YEntity optionalReferencedOn XTable.y1

        companion object: IntEntityClass<BEntity>(XTable) {
            fun create(init: AEntity.() -> Unit): BEntity {
                val answer = new {
                    init()
                }
                return answer
            }
        }
    }

    class YEntity(id: EntityID<String>) : Entity<String>(id) {
        var x by YTable.x

        companion object : EntityClass<String, YEntity>(YTable) {

        }
    }
}

class EntityTests: DatabaseTestsBase() {
    @Test fun testDefaults01() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val x = EntityTestsData.XEntity.new {  }
            assertEquals (x.b1, true, "b1 mismatched")
            assertEquals (x.b2, false, "b2 mismatched")
        }
    }

    @Test fun testDefaults02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val a: EntityTestsData.AEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.A)
            assertEquals (a.b1, false, "a.b1 mismatched")

            val b: EntityTestsData.BEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.B) as EntityTestsData.BEntity
            val y = EntityTestsData.YEntity.new { x = false }
            assertEquals (b.b1, false, "a.b1 mismatched")
            assertEquals (b.b2, false, "b.b2 mismatched")

            b.y = y

            assertFalse (b.y!!.x)

        }
    }
}

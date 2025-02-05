@file:Suppress("MatchingDeclarationName", "Filename")

package org.jetbrains.exposed.r2dbc.sql.tests.shared.entities

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import java.util.*

object EntityTestsData {

    object YTable : IdTable<String>("YTable") {
        override val id: Column<EntityID<String>> = varchar("uuid", 36).entityId().clientDefault {
            EntityID(UUID.randomUUID().toString(), YTable)
        }

        val x = bool("x").default(true)

        override val primaryKey = PrimaryKey(id)
    }

    object XTable : IntIdTable("XTable") {
        val b1 = bool("b1").default(true)
        val b2 = bool("b2").default(false)
        val y1 = optReference("y1", YTable)
    }

//    class XEntity(id: EntityID<Int>) : Entity<Int>(id) {
//        var b1 by XTable.b1
//        var b2 by XTable.b2
//
//        companion object : EntityClass<Int, XEntity>(XTable)
//    }

    enum class XType {
        A, B
    }

//    open class AEntity(id: EntityID<Int>) : IntEntity(id) {
//        var b1 by XTable.b1
//
//        companion object : IntEntityClass<AEntity>(XTable) {
//            fun create(b1: Boolean, type: XType): AEntity {
//                val init: AEntity.() -> Unit = {
//                    this.b1 = b1
//                }
//                val answer = when (type) {
//                    XType.B -> BEntity.create { init() }
//                    else -> new { init() }
//                }
//                return answer
//            }
//        }
//    }

//    class BEntity(id: EntityID<Int>) : AEntity(id) {
//        var b2 by XTable.b2
//        var y by YEntity optionalReferencedOn XTable.y1
//
//        companion object : IntEntityClass<BEntity>(XTable) {
//            fun create(init: AEntity.() -> Unit): BEntity {
//                val answer = new {
//                    init()
//                }
//                return answer
//            }
//        }
//    }

//    class YEntity(id: EntityID<String>) : Entity<String>(id) {
//        var x by YTable.x
//        val b: BEntity? by BEntity.backReferencedOn(XTable.y1)
//        val bOpt by BEntity optionalBackReferencedOn XTable.y1
//
//        companion object : EntityClass<String, YEntity>(YTable)
//    }
}

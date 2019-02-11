package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.junit.Test
import java.util.*

object ViaTestData {
    object NumbersTable: UUIDTable() {
        val number = integer("number")
    }

    object StringsTable: IdTable<Long>("") {
        override val id: Column<EntityID<Long>> = long("id").autoIncrement().primaryKey().entityId()
        val text = varchar("text", 10)
    }

    object ConnectionTable: Table() {
        val numId = reference("numId", NumbersTable, ReferenceOption.CASCADE)
        val stringId = reference("stringId", StringsTable, ReferenceOption.CASCADE)

        init {
           index(true, numId, stringId)
        }
    }

    val allTables: Array<Table> = arrayOf(NumbersTable, StringsTable, ConnectionTable)
}

class VNumber(id: EntityID<UUID>): UUIDEntity(id) {
    var number by ViaTestData.NumbersTable.number
    var connectedStrings: SizedIterable<VString> by VString via ViaTestData.ConnectionTable

    companion object : UUIDEntityClass<VNumber>(ViaTestData.NumbersTable)
}

class VString(id: EntityID<Long>): Entity<Long>(id) {
    var text by ViaTestData.StringsTable.text
    companion object : EntityClass<Long, VString>(ViaTestData.StringsTable)
}


class ViaTests : DatabaseTestsBase() {
    @Test fun testConnection01() {
        withTables(*ViaTestData.allTables) {
            val n = VNumber.new { number = 10 }
            val s = VString.new { text = "aaa" }
            n.connectedStrings = SizedCollection(listOf(s))

            val row = ViaTestData.ConnectionTable.selectAll().single()
            assertEquals (n.id, row[ViaTestData.ConnectionTable.numId])
            assertEquals (s.id, row[ViaTestData.ConnectionTable.stringId])
        }
    }

    @Test fun testConnection02() {
        withTables(*ViaTestData.allTables) {
            val n1 = VNumber.new { number = 1 }
            val n2 = VNumber.new { number = 2 }
            val s1 = VString.new { text = "aaa" }
            val s2 = VString.new { text = "bbb" }

            n1.connectedStrings = SizedCollection(listOf(s1, s2))

            val row = ViaTestData.ConnectionTable.selectAll().toList()
            assertEquals(2, row.count())
            assertEquals (n1.id, row[0][ViaTestData.ConnectionTable.numId])
            assertEquals (n1.id, row[1][ViaTestData.ConnectionTable.numId])
            assertEqualCollections (listOf(s1.id, s2.id), row.map { it[ViaTestData.ConnectionTable.stringId] })
        }
    }

    @Test fun testConnection03() {
        withTables(*ViaTestData.allTables) {
            val n1 = VNumber.new { number = 1 }
            val n2 = VNumber.new { number = 2 }
            val s1 = VString.new { text = "aaa" }
            val s2 = VString.new { text = "bbb" }

            n1.connectedStrings = SizedCollection(listOf(s1, s2))
            n2.connectedStrings = SizedCollection(listOf(s1, s2))

            run {
                val row = ViaTestData.ConnectionTable.selectAll().toList()
                assertEquals(4, row.count())
                assertEqualCollections(n1.connectedStrings, listOf(s1, s2))
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }

            n1.connectedStrings = SizedCollection(emptyList())

            run {
                val row = ViaTestData.ConnectionTable.selectAll().toList()
                assertEquals(2, row.count())
                assertEquals (n2.id, row[0][ViaTestData.ConnectionTable.numId])
                assertEquals (n2.id, row[1][ViaTestData.ConnectionTable.numId])
                assertEqualCollections(n1.connectedStrings, emptyList())
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }

        }
    }

    @Test fun testConnection04() {
        withTables(*ViaTestData.allTables) {
            val n1 = VNumber.new { number = 1 }
            val n2 = VNumber.new { number = 2 }
            val s1 = VString.new { text = "aaa" }
            val s2 = VString.new { text = "bbb" }

            n1.connectedStrings = SizedCollection(listOf(s1, s2))
            n2.connectedStrings = SizedCollection(listOf(s1, s2))

            run {
                val row = ViaTestData.ConnectionTable.selectAll().toList()
                assertEquals(4, row.count())
                assertEqualCollections(n1.connectedStrings, listOf(s1, s2))
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }

            n1.connectedStrings = SizedCollection(listOf(s1))

            run {
                val row = ViaTestData.ConnectionTable.selectAll().toList()
                assertEquals(3, row.count())
                assertEqualCollections(n1.connectedStrings, listOf(s1))
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }
        }
    }

    object NodesTable : IntIdTable() {
        val name = varchar("name", 50)
    }
    object NodeToNodes : Table() {
        val parent = reference("parent_node_id", NodesTable)
        val child = reference("child_user_id", NodesTable)
    }
    class Node(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Node>(NodesTable)

        var name by NodesTable.name
        var parents by Node.via(NodeToNodes.child, NodeToNodes.parent)
        var children by Node.via(NodeToNodes.parent, NodeToNodes.child)
    }

    @Test
    fun testHierarchicalReferences() {
        withTables(NodeToNodes) {
            val root = Node.new { name = "root" }
            val child1 = Node.new {
                name = "child1"
            }
            child1.parents = SizedCollection(root)

            assertEquals(0, root.parents.count())
            assertEquals(1, root.children.count())

            val child2 = Node.new { name = "child2" }
            root.children = SizedCollection(listOf(child1, child2))

            assertEquals(root, child1.parents.singleOrNull())
            assertEquals(root, child2.parents.singleOrNull())
        }

    }
}

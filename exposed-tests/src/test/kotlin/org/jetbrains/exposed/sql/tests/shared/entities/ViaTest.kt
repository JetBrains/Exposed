package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import java.util.*

object ViaTestData {
    object NumbersTable : UUIDTable() {
        val number = integer("number")
    }

    object StringsTable : IdTable<Long>("") {
        override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        val text = varchar("text", 10)

        override val primaryKey = PrimaryKey(id)
    }

    interface IConnectionTable {
        val numId: Column<EntityID<UUID>>
        val stringId: Column<EntityID<Long>>
    }

    object ConnectionTable : Table(), IConnectionTable {
        override val numId = reference("numId", NumbersTable, ReferenceOption.CASCADE)
        override val stringId = reference("stringId", StringsTable, ReferenceOption.CASCADE)

        init {
            index(true, numId, stringId)
        }
    }

    object ConnectionAutoIncTable : IntIdTable(), IConnectionTable {
        override val numId = reference("numId", NumbersTable, ReferenceOption.CASCADE)
        override val stringId = reference("stringId", StringsTable, ReferenceOption.CASCADE)

        init {
            index(true, numId, stringId)
        }
    }

    val allTables: Array<Table> = arrayOf(NumbersTable, StringsTable, ConnectionTable, ConnectionAutoIncTable)
}

class VNumber(id: EntityID<UUID>) : UUIDEntity(id) {
    var number by ViaTestData.NumbersTable.number
    var connectedStrings: SizedIterable<VString> by VString via ViaTestData.ConnectionTable
    var connectedAutoStrings: SizedIterable<VString> by VString via ViaTestData.ConnectionAutoIncTable

    companion object : UUIDEntityClass<VNumber>(ViaTestData.NumbersTable)
}

class VString(id: EntityID<Long>) : Entity<Long>(id) {
    var text by ViaTestData.StringsTable.text
    companion object : EntityClass<Long, VString>(ViaTestData.StringsTable)
}

class ViaTests : DatabaseTestsBase() {

    private fun VNumber.testWithBothTables(valuesToSet: List<VString>, body: (ViaTestData.IConnectionTable, List<ResultRow>) -> Unit) {
        listOf(ViaTestData.ConnectionTable, ViaTestData.ConnectionAutoIncTable).forEach { t ->
            if (t == ViaTestData.ConnectionTable) {
                connectedStrings = SizedCollection(valuesToSet)
            } else {
                connectedAutoStrings = SizedCollection(valuesToSet)
            }

            val result = t.selectAll().toList()
            body(t, result)
        }
    }

    @Test fun testConnection01() {
        withTables(*ViaTestData.allTables) {
            val n = VNumber.new { number = 10 }
            val s = VString.new { text = "aaa" }
            n.testWithBothTables(listOf(s)) { table, result ->
                val row = result.single()
                assertEquals(n.id, row[table.numId])
                assertEquals(s.id, row[table.stringId])
            }
        }
    }

    @Test fun testConnection02() {
        withTables(*ViaTestData.allTables) {
            val n1 = VNumber.new { number = 1 }
            val n2 = VNumber.new { number = 2 }
            val s1 = VString.new { text = "aaa" }
            val s2 = VString.new { text = "bbb" }

            n1.testWithBothTables(listOf(s1, s2)) { table, row ->
                assertEquals(2, row.count())
                assertEquals(n1.id, row[0][table.numId])
                assertEquals(n1.id, row[1][table.numId])
                assertEqualCollections(listOf(s1.id, s2.id), row.map { it[table.stringId] })
            }
        }
    }

    @Test fun testConnection03() {
        withTables(*ViaTestData.allTables) {
            val n1 = VNumber.new { number = 1 }
            val n2 = VNumber.new { number = 2 }
            val s1 = VString.new { text = "aaa" }
            val s2 = VString.new { text = "bbb" }

            n1.testWithBothTables(listOf(s1, s2)) { _, _ -> }
            n2.testWithBothTables(listOf(s1, s2)) { _, row ->
                assertEquals(4, row.count())
                assertEqualCollections(n1.connectedStrings, listOf(s1, s2))
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }

            n1.testWithBothTables(emptyList()) { table, row ->
                assertEquals(2, row.count())
                assertEquals(n2.id, row[0][table.numId])
                assertEquals(n2.id, row[1][table.numId])
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

            n1.testWithBothTables(listOf(s1, s2)) { _, _ -> }
            n2.testWithBothTables(listOf(s1, s2)) { _, row ->
                assertEquals(4, row.count())
                assertEqualCollections(n1.connectedStrings, listOf(s1, s2))
                assertEqualCollections(n2.connectedStrings, listOf(s1, s2))
            }

            n1.testWithBothTables(listOf(s1)) { table, row ->
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
            addLogger(StdOutSqlLogger)
            val root = Node.new { name = "root" }
            val child1 = Node.new {
                name = "child1"
                parents = SizedCollection(root)
            }

            assertEquals(0L, root.parents.count())
            assertEquals(1L, root.children.count())

            val child2 = Node.new { name = "child2" }
            root.children = SizedCollection(listOf(child1, child2))

            assertEquals(root, child1.parents.singleOrNull())
            assertEquals(root, child2.parents.singleOrNull())
        }
    }

    @Test fun testRefresh() {
        withTables(*ViaTestData.allTables) {
            val s = VString.new { text = "ccc" }.apply {
                refresh(true)
            }
            assertEquals("ccc", s.text)
        }
    }
}

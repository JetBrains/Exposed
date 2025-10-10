package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.*
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.SizedIterable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.Test
import java.sql.Connection
import java.util.*
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun testConnection01() {
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

    @Test
    fun testConnection02() {
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

    @Test
    fun testConnection03() {
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

    @Test
    fun testConnection04() {
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

            n1.testWithBothTables(listOf(s1)) { _, row ->
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

        override fun equals(other: Any?): Boolean = (other as? Node)?.id == id

        override fun hashCode(): Int = Objects.hash(id)
    }

    @Test
    fun testHierarchicalReferences() {
        withTables(NodesTable, NodeToNodes) {
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

    @Test
    fun testRefresh() {
        withTables(*ViaTestData.allTables) {
            val s = VString.new { text = "ccc" }.apply {
                refresh(true)
            }
            assertEquals("ccc", s.text)
        }
    }

    @Test
    fun testWarmUpOnHierarchicalEntities() {
        withTables(NodesTable, NodeToNodes) {
            val child1 = Node.new { name = "child1" }
            val child2 = Node.new { name = "child1" }
            val root1 = Node.new {
                name = "root1"
                children = SizedCollection(child1)
            }
            val root2 = Node.new {
                name = "root2"
                children = SizedCollection(child1, child2)
            }

            entityCache.clear(flush = true)

            fun checkChildrenReferences(node: Node, values: List<Node>) {
                val sourceColumn = (Node::children.apply { isAccessible = true }.getDelegate(node) as InnerTableLink<*, *, *, *>).sourceColumn
                val children = entityCache.getReferrers<Node>(node.id, sourceColumn)
                assertEqualLists(children?.toList().orEmpty(), values)
            }

            Node.all().with(Node::children).toList()
            checkChildrenReferences(child1, emptyList())
            checkChildrenReferences(child2, emptyList())
            checkChildrenReferences(root1, listOf(child1))
            checkChildrenReferences(root2, listOf(child1, child2))

            fun checkParentsReferences(node: Node, values: List<Node>) {
                val sourceColumn = (Node::parents.apply { isAccessible = true }.getDelegate(node) as InnerTableLink<*, *, *, *>).sourceColumn
                val children = entityCache.getReferrers<Node>(node.id, sourceColumn)
                assertEqualLists(children?.toList().orEmpty(), values)
            }

            Node.all().with(Node::parents).toList()
            checkParentsReferences(child1, listOf(root1, root2))
            checkParentsReferences(child2, listOf(root2))
            checkParentsReferences(root1, emptyList())
            checkParentsReferences(root2, emptyList())
        }
    }

    class NodeOrdered(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<NodeOrdered>(NodesTable)

        var name by NodesTable.name
        var parents by NodeOrdered.via(NodeToNodes.child, NodeToNodes.parent)
        var children by NodeOrdered.via(NodeToNodes.parent, NodeToNodes.child) orderBy (NodesTable.name to SortOrder.ASC)

        override fun equals(other: Any?): Boolean = (other as? NodeOrdered)?.id == id

        override fun hashCode(): Int = Objects.hash(id)
    }

    @Test
    fun testOrderBy() {
        withTables(NodesTable, NodeToNodes) {
            val root = NodeOrdered.new { name = "root" }
            listOf("#3", "#0", "#2", "#4", "#1").forEach {
                NodeOrdered.new {
                    name = it
                    parents = SizedCollection(root)
                }
            }

            root.children.forEachIndexed { index, node ->
                assertEquals("#$index", node.name)
            }
        }
    }

    object Projects : IntIdTable("projects") {
        val name = varchar("name", 50)
    }

    class Project(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Project>(Projects)

        var name by Projects.name
        val tasks by Task via ProjectTasks
    }

    object ProjectTasks : CompositeIdTable("project_tasks") {
        val project = reference("project", Projects, onDelete = ReferenceOption.CASCADE)
        val task = reference("task", Tasks, onDelete = ReferenceOption.CASCADE)
        val approved = bool("approved")

        override val primaryKey = PrimaryKey(project, task)

        init {
            addIdColumn(project)
            addIdColumn(task)
        }
    }

    class ProjectTask(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<ProjectTask>(ProjectTasks)

        var approved by ProjectTasks.approved
    }

    object Tasks : IntIdTable("tasks") {
        val title = varchar("title", 64)
    }

    class Task(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Task>(Tasks)

        var title by Tasks.title
        val approved by ProjectTasks.approved
    }

    @Test
    fun testAdditionalLinkDataUsingCompositeIdInnerTable() {
        withTables(Projects, Tasks, ProjectTasks) {
            val p1 = Project.new { name = "Project 1" }
            val p2 = Project.new { name = "Project 2" }
            val t1 = Task.new { title = "Task 1" }
            val t2 = Task.new { title = "Task 2" }
            val t3 = Task.new { title = "Task 3" }

            ProjectTask.new(
                CompositeID {
                    it[ProjectTasks.task] = t1.id
                    it[ProjectTasks.project] = p1.id
                }
            ) { approved = true }
            ProjectTask.new(
                CompositeID {
                    it[ProjectTasks.task] = t2.id
                    it[ProjectTasks.project] = p2.id
                }
            ) { approved = false }
            ProjectTask.new(
                CompositeID {
                    it[ProjectTasks.task] = t3.id
                    it[ProjectTasks.project] = p2.id
                }
            ) { approved = false }

            commit()

            inTopLevelTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                Project.all().with(Project::tasks)
                val cache = TransactionManager.current().entityCache

                val p1Tasks = cache.getReferrers<Task>(p1.id, ProjectTasks.project)?.toList().orEmpty()
                assertEqualLists(p1Tasks.map { it.id }, listOf(t1.id))
                assertTrue { p1Tasks.all { task -> task.approved } }

                val p2Tasks = cache.getReferrers<Task>(p2.id, ProjectTasks.project)?.toList().orEmpty()
                assertEqualLists(p2Tasks.map { it.id }, listOf(t2.id, t3.id))
                assertFalse { p1Tasks.all { task -> !task.approved } }
            }
        }
    }
}

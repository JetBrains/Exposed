package org.jetbrains.exposed.dao.r2dbc.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.CompositeR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.CompositeR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.UuidR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.UuidR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.relationships.R2dbcInnerTableLinkAccessor
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import java.util.Objects
import kotlin.reflect.jvm.isAccessible
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

object ViaTestData {
    object NumbersTable : UuidTable() {
        val number = integer("number")
    }

    object StringsTable : IdTable<Long>("") {
        override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
        val text = varchar("text", 10)

        override val primaryKey = PrimaryKey(id)
    }

    interface IConnectionTable {
        val numId: Column<EntityID<Uuid>>
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

class VNumber(id: EntityID<Uuid>) : UuidR2dbcEntity(id) {
    var number by ViaTestData.NumbersTable.number
    val connectedStrings by VString viaSuspend ViaTestData.ConnectionTable
    val connectedAutoStrings by VString viaSuspend ViaTestData.ConnectionAutoIncTable

    companion object : UuidR2dbcEntityClass<VNumber>(ViaTestData.NumbersTable)
}

class VString(id: EntityID<Long>) : R2dbcEntity<Long>(id) {
    var text by ViaTestData.StringsTable.text

    companion object : R2dbcEntityClass<Long, VString>(ViaTestData.StringsTable)
}

class R2dbcViaTest : R2dbcDatabaseTestsBase() {
    private suspend fun VNumber.testWithBothTables(valuesToSet: List<VString>, body: suspend (ViaTestData.IConnectionTable, List<ResultRow>) -> Unit) {
        listOf(ViaTestData.ConnectionTable, ViaTestData.ConnectionAutoIncTable).forEach { t ->
            if (t == ViaTestData.ConnectionTable) {
                connectedStrings set valuesToSet
            } else {
                connectedAutoStrings set valuesToSet
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
                assertEqualCollections(n1.connectedStrings(), listOf(s1, s2))
                assertEqualCollections(n2.connectedStrings(), listOf(s1, s2))
            }

            n1.testWithBothTables(emptyList()) { table, row ->
                assertEquals(2, row.count())
                assertEquals(n2.id, row[0][table.numId])
                assertEquals(n2.id, row[1][table.numId])
                assertEqualCollections(n1.connectedStrings(), emptyList())
                assertEqualCollections(n2.connectedStrings(), listOf(s1, s2))
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
                assertEqualCollections(n1.connectedStrings(), listOf(s1, s2))
                assertEqualCollections(n2.connectedStrings(), listOf(s1, s2))
            }

            n1.testWithBothTables(listOf(s1)) { _, row ->
                assertEquals(3, row.count())
                assertEqualCollections(n1.connectedStrings(), listOf(s1))
                assertEqualCollections(n2.connectedStrings(), listOf(s1, s2))
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

    class Node(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Node>(NodesTable)

        var name by NodesTable.name
        val parents by Node.viaSuspend(NodeToNodes.child, NodeToNodes.parent)
        val children by Node.viaSuspend(NodeToNodes.parent, NodeToNodes.child)

        override fun equals(other: Any?): Boolean = (other as? Node)?.id == id

        override fun hashCode(): Int = Objects.hash(id)
    }

    @Test
    fun testHierarchicalReferences() {
        withTables(NodesTable, NodeToNodes) {
            val root = Node.new { name = "root" }
            val child1 = Node.new {
                name = "child1"
            }
            // TODO at the current moment it's not possible to set this value inside `new()`, becuase `new(){}` block is non suspend
            child1.parents set listOf(root)

            assertEquals(0L, root.parents().count())
            assertEquals(1L, root.children().count())

            val child2 = Node.new { name = "child2" }
            root.children set listOf(child1, child2)

            assertEquals(root, child1.parents().singleOrNull())
            assertEquals(root, child2.parents().singleOrNull())
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
            }
            // TODO same problem with `new(){}` block
            root1.children set listOf(child1)

            val root2 = Node.new {
                name = "root2"
            }
            // TODO same problem with `new(){}` block
            root2.children set listOf(child1, child2)

            entityCache.clear(flush = true)

            suspend fun checkChildrenReferences(node: Node, values: List<Node>) {
                val sourceColumn = (Node::children.apply { isAccessible = true }.getDelegate(node) as R2dbcInnerTableLinkAccessor<*, *, *, *>).link.sourceColumn
                val children = entityCache.getReferrers<Node>(node.id, sourceColumn)
                assertEqualLists(children?.toList().orEmpty(), values)
            }

            Node.all().with(Node::children).toList()
            checkChildrenReferences(child1, emptyList())
            checkChildrenReferences(child2, emptyList())
            checkChildrenReferences(root1, listOf(child1))
            checkChildrenReferences(root2, listOf(child1, child2))

            suspend fun checkParentsReferences(node: Node, values: List<Node>) {
                val sourceColumn = (Node::parents.apply { isAccessible = true }.getDelegate(node) as R2dbcInnerTableLinkAccessor<*, *, *, *>).link.sourceColumn
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

    class NodeOrdered(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<NodeOrdered>(NodesTable)

        var name by NodesTable.name
        val parents by NodeOrdered.viaSuspend(NodeToNodes.child, NodeToNodes.parent)
        val children by NodeOrdered.viaSuspend(NodeToNodes.parent, NodeToNodes.child) orderBy (NodesTable.name to SortOrder.ASC)

        override fun equals(other: Any?): Boolean = (other as? NodeOrdered)?.id == id

        override fun hashCode(): Int = Objects.hash(id)
    }

    @Test
    fun testOrderBy() {
        withTables(NodesTable, NodeToNodes) {
            val root = NodeOrdered.new { name = "root" }
            listOf("#3", "#0", "#2", "#4", "#1").forEach {
                val n = NodeOrdered.new {
                    name = it
                }
                // TODO same problem with `new(){}` block
                n.parents set listOf(root)
            }

            root.children().toList().forEachIndexed { index, node ->
                assertEquals("#$index", node.name)
            }
        }
    }

    object Projects : IntIdTable("projects") {
        val name = varchar("name", 50)
    }

    class Project(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Project>(Projects)

        var name by Projects.name
        val tasks by Task viaSuspend ProjectTasks
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

    class ProjectTask(id: EntityID<CompositeID>) : CompositeR2dbcEntity(id) {
        companion object : CompositeR2dbcEntityClass<ProjectTask>(ProjectTasks)

        var approved by ProjectTasks.approved
    }

    object Tasks : IntIdTable("tasks") {
        val title = varchar("title", 64)
    }

    class Task(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Task>(Tasks)

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

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
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

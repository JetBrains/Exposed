package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.junit.Test
import java.sql.Connection
import java.util.*
import kotlin.reflect.jvm.isAccessible

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

    // IdTable without auto-increment is used so manual ids can be inserted without excluding SQL Server
    object Projects : IdTable<Int>("projects") {
        override val id = integer("id").entityId()
        val name = varchar("name", 50)
        override val primaryKey = PrimaryKey(id)
    }
    class Project(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Project>(Projects)

        var name by Projects.name
        var tasks by TaskWithData via ProjectTasks
    }

    object ProjectTasks : Table("project_tasks") {
        val project = reference("project", Projects, onDelete = ReferenceOption.CASCADE)
        val task = reference("task", Tasks, onDelete = ReferenceOption.CASCADE)
        val approved = bool("approved")
        val sprint = integer("sprint")

        override val primaryKey = PrimaryKey(project, task)
    }

    // IdTable without auto-increment is used so manual ids can be inserted without excluding SQL Server
    object Tasks : IdTable<Int>("tasks") {
        override val id = integer("id").entityId()
        val title = varchar("title", 64)
        override val primaryKey = PrimaryKey(id)
    }
    class Task(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Task>(Tasks)

        var title by Tasks.title
        var projects by ProjectWithData via ProjectTasks
    }

    class ProjectWithData(
        val project: Project,
        val approved: Boolean,
        val sprint: Int
    ) : InnerTableLinkEntity<Int>(project) {
        companion object : InnerTableLinkEntityClass<Int, ProjectWithData>(Projects) {
            override fun createInstance(entityId: EntityID<Int>, row: ResultRow?): ProjectWithData {
                return row?.let {
                    ProjectWithData(Project.wrapRow(it), it[ProjectTasks.approved], it[ProjectTasks.sprint])
                } ?: ProjectWithData(Project(entityId), false, 0)
            }
        }

        override fun getInnerTableLinkValue(column: Column<*>): Any = when (column) {
            ProjectTasks.approved -> approved
            ProjectTasks.sprint -> sprint
            else -> error("Column does not exist in intermediate table")
        }
    }

    class TaskWithData(
        val task: Task,
        val approved: Boolean,
        val sprint: Int
    ) : InnerTableLinkEntity<Int>(task) {
        companion object : InnerTableLinkEntityClass<Int, TaskWithData>(Tasks) {
            override fun createInstance(entityId: EntityID<Int>, row: ResultRow?): TaskWithData {
                return row?.let {
                    TaskWithData(Task.wrapRow(it), it[ProjectTasks.approved], it[ProjectTasks.sprint])
                } ?: TaskWithData(Task(entityId), false, 0)
            }
        }

        override fun getInnerTableLinkValue(column: Column<*>): Any = when (column) {
            ProjectTasks.approved -> approved
            ProjectTasks.sprint -> sprint
            else -> error("Column does not exist in intermediate table")
        }
    }

    @Test
    fun testAdditionalLinkDataInsertAndUpdate() {
        withTables(Projects, Tasks, ProjectTasks) {
            val p1 = Project.new(123) { name = "Project 1" }
            val p2 = Project.new(456) { name = "Project 2" }
            val t1 = Task.new(11) { title = "Task 1" }
            val t2 = Task.new(22) { title = "Task 2" }
            val t3 = Task.new(33) { title = "Task 3" }

            p1.tasks = SizedCollection(TaskWithData(t1, false, 1))
            p2.tasks = SizedCollection(TaskWithData(t2, true, 2), TaskWithData(t1, false, 3))

            assertFalse(p1.tasks.single().approved)
            p1.tasks = SizedCollection(TaskWithData(t1, true, 1))
            assertTrue(p1.tasks.single().approved)

            assertEqualCollections(p2.tasks.map { it.task.id }, listOf(t2.id, t1.id))
            p2.tasks = SizedCollection(TaskWithData(t2, true, 2), TaskWithData(t3, false, 3))
            assertEqualCollections(p2.tasks.map { it.task.id }, listOf(t2.id, t3.id))
        }
    }

    @Test
    fun testAdditionalLinkDataLoadedOnParent() {
        withTables(Projects, Tasks, ProjectTasks) {
            val p1 = Project.new(123) { name = "Project 1" }
            val p2 = Project.new(456) { name = "Project 2" }
            val t1 = Task.new(11) { title = "Task 1" }
            val t2 = Task.new(22) { title = "Task 2" }
            val t3 = Task.new(33) { title = "Task 3" }

            p1.tasks = SizedCollection(TaskWithData(t1, false, 1))
            p2.tasks = SizedCollection(TaskWithData(t2, true, 2), TaskWithData(t3, false, 3))

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                Project.all().with(Project::tasks)
                val cache = TransactionManager.current().entityCache

                val p1Task = cache.getReferrers<TaskWithData>(p1.id, ProjectTasks.project)?.single()
                assertEquals(t1.id, p1Task?.id)
                assertEquals(t1.id, p1Task?.task?.id)
                assertEquals(false, p1Task?.approved)
                assertEquals(1, p1Task?.sprint)

                val p2Tasks = cache.getReferrers<TaskWithData>(p2.id, ProjectTasks.project)?.toList().orEmpty()
                assertEqualLists(p2Tasks.map { it.id }, listOf(t2.id, t3.id))
                assertEqualLists(p2Tasks.map { it.approved }, listOf(true, false))
                assertEqualLists(p2Tasks.map { it.sprint }, listOf(2, 3))
            }
        }
    }

    @Test
    fun testAdditionalLinkDataLoadedOnChild() {
        withTables(Projects, Tasks, ProjectTasks) {
            val p1 = Project.new(123) { name = "Project 1" }
            val p2 = Project.new(456) { name = "Project 2" }
            val t1 = Task.new(11) { title = "Task 1" }
            val t2 = Task.new(22) { title = "Task 2" }
            val t3 = Task.new(33) { title = "Task 3" }

            p1.tasks = SizedCollection(TaskWithData(t1, false, 1))
            p2.tasks = SizedCollection(TaskWithData(t2, true, 2), TaskWithData(t3, false, 3))

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                Task.all().with(Task::projects)
                val cache = TransactionManager.current().entityCache

                val t1Project = cache.getReferrers<ProjectWithData>(t1.id, ProjectTasks.task)?.single()
                assertEquals(p1.id, t1Project?.id)
                assertEquals(p1.id, t1Project?.project?.id)
                assertEquals(false, t1Project?.approved)
                assertEquals(1, t1Project?.sprint)
            }
        }
    }
}

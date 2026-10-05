package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
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
import org.jetbrains.exposed.v1.dao.r2dbc.CompositeEntity
import org.jetbrains.exposed.v1.dao.r2dbc.CompositeEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.Entity
import org.jetbrains.exposed.v1.dao.r2dbc.EntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.UuidEntity
import org.jetbrains.exposed.v1.dao.r2dbc.UuidEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.entityCache
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.dao.r2dbc.with
import org.jetbrains.exposed.v1.r2dbc.SizedCollection
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.NOT_APPLICABLE_TO_JDBC
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.junit.jupiter.api.Tag
import java.util.Objects
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

class VNumber(id: EntityID<Uuid>) : UuidEntity(id) {
    var number by ViaTestData.NumbersTable.number
    var connectedStrings by VString via ViaTestData.ConnectionTable
    var connectedAutoStrings by VString via ViaTestData.ConnectionAutoIncTable

    companion object : UuidEntityClass<VNumber>(ViaTestData.NumbersTable)
}

class VString(id: EntityID<Long>) : Entity<Long>(id) {
    var text by ViaTestData.StringsTable.text

    companion object : EntityClass<Long, VString>(ViaTestData.StringsTable)
}

class ViaTest : R2dbcDatabaseTestsBase() {
    private suspend fun VNumber.testWithBothTables(valuesToSet: List<VString>, body: suspend (ViaTestData.IConnectionTable, List<ResultRow>) -> Unit) {
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
            val n = VNumber.newSuspend { number = 10 }
            val s = VString.newSuspend { text = "aaa" }
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
            val n1 = VNumber.newSuspend { number = 1 }
            val n2 = VNumber.newSuspend { number = 2 }
            val s1 = VString.newSuspend { text = "aaa" }
            val s2 = VString.newSuspend { text = "bbb" }

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
            val n1 = VNumber.newSuspend { number = 1 }
            val n2 = VNumber.newSuspend { number = 2 }
            val s1 = VString.newSuspend { text = "aaa" }
            val s2 = VString.newSuspend { text = "bbb" }

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
            val n1 = VNumber.newSuspend { number = 1 }
            val n2 = VNumber.newSuspend { number = 2 }
            val s1 = VString.newSuspend { text = "aaa" }
            val s2 = VString.newSuspend { text = "bbb" }

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

    /**
     * Assigning a many-to-many relation is queued rather than executed, because a property setter
     * cannot suspend. Reading the relation back in the same transaction must still observe the
     * assignment, without the caller having to reach for `flushCache()`.
     *
     * The second assignment is the interesting one: by then the first read has populated the
     * referrers cache, so a stale cached collection could be returned.
     */
    @Test
    fun testReadBackAssignedLinksWithoutExplicitFlush() {
        withTables(*ViaTestData.allTables) {
            val n = VNumber.newSuspend { number = 10 }
            val s1 = VString.newSuspend { text = "aaa" }
            val s2 = VString.newSuspend { text = "bbb" }

            n.connectedStrings = SizedCollection(listOf(s1, s2))
            assertEqualCollections(listOf("aaa", "bbb"), n.connectedStrings.toList().map { it.text })

            n.connectedStrings = SizedCollection(listOf(s2))
            assertEqualCollections(listOf("bbb"), n.connectedStrings.toList().map { it.text })

            n.connectedStrings = SizedCollection(emptyList())
            assertEqualCollections(emptyList(), n.connectedStrings.toList().map { it.text })
        }
    }

    /**
     * Same contract as [testReadBackAssignedLinksWithoutExplicitFlush], in the shape a web handler
     * actually uses it: the parent is loaded with `findById` in a fresh transaction, the targets are
     * created during the same transaction, and the relation is read back through `Flow.map`.
     */
    @Test
    @Tag(NOT_APPLICABLE_TO_JDBC)
    fun testReadBackAssignedLinksAfterFindByIdWithoutExplicitFlush() {
        withTables(*ViaTestData.allTables) {
            val numberId = VNumber.newSuspend { number = 7 }.id
            // The read-back below runs in a separate top-level transaction, so the parent must be committed first;
            // otherwise a fresh connection cannot see the uncommitted row (e.g. on Oracle) and findById returns null.
            commit()

            inTopLevelSuspendTransaction {
                val n = VNumber.findById(numberId)!!
                val targets = listOf("aaa", "bbb").map { value -> VString.newSuspend { text = value } }

                n.connectedStrings = SizedCollection(targets)
                assertEqualCollections(listOf("aaa", "bbb"), n.connectedStrings.map { it.text }.toList())
            }
        }
    }

    /**
     * A queued many-to-many assignment must not survive a rollback and be replayed by a later flush.
     *
     * This holds for a non-obvious reason: the queue lives on the entity cache, and the cache is
     * discarded when the transaction rolls back — it is not cleared explicitly in `beforeRollback`
     * alongside `data`/`inserts`/`updates`. The test pins the behaviour so a change to cache lifetime
     * cannot silently start resurrecting rolled-back writes.
     */
    @Test
    fun testPendingLinkUpdatesAreDiscardedOnRollback() {
        withTables(*ViaTestData.allTables) {
            val numberId = VNumber.newSuspend { number = 1 }.id
            val stringId = VString.newSuspend { text = "aaa" }.id
            commit()

            inTopLevelSuspendTransaction {
                maxAttempts = 1
                val n = VNumber.findById(numberId)!!
                val s = VString.findById(stringId)!!
                n.connectedStrings = SizedCollection(listOf(s))
                rollback()
                flushCache()
                assertEquals(0L, ViaTestData.ConnectionTable.selectAll().count())
            }

            assertEquals(0L, ViaTestData.ConnectionTable.selectAll().count())
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
            val child1 = Node.newSuspend {
                name = "child1"
                parents = SizedCollection(
                    Node.newSuspend { name = "root" }
                )
            }

            val root = child1.parents.single()

            assertEquals(0L, root.parents.count())
            assertEquals(1L, root.children.count())

            val child2 = Node.newSuspend { name = "child2" }
            root.children = SizedCollection(listOf(child1, child2))

            assertEquals(root, child1.parents.singleOrNull())
            assertEquals(root, child2.parents.singleOrNull())
        }
    }

    @Test
    fun testRefresh() {
        withTables(*ViaTestData.allTables) {
            val s = VString.newSuspend { text = "ccc" }.apply {
                refresh(true)
            }
            assertEquals("ccc", s.text)
        }
    }

    @Test
    fun testWarmUpOnHierarchicalEntities() {
        withTables(NodesTable, NodeToNodes) {
            val child1 = Node.newSuspend { name = "child1" }
            val child2 = Node.newSuspend { name = "child1" }
            val root1 = Node.newSuspend {
                name = "root1"
                children = SizedCollection(child1)
            }
            val root2 = Node.newSuspend {
                name = "root2"
                children = SizedCollection(child1, child2)
            }

            entityCache.clear(flush = true)

            suspend fun checkChildrenReferences(node: Node, values: List<Node>) {
                val children = entityCache.getReferrers<Node>(node.id, NodeToNodes.parent)
                assertEqualLists(children?.toList().orEmpty(), values)
            }

            Node.all().with(Node::children).toList()
            checkChildrenReferences(child1, emptyList())
            checkChildrenReferences(child2, emptyList())
            checkChildrenReferences(root1, listOf(child1))
            checkChildrenReferences(root2, listOf(child1, child2))

            suspend fun checkParentsReferences(node: Node, values: List<Node>) {
                val children = entityCache.getReferrers<Node>(node.id, NodeToNodes.child)
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
            val root = NodeOrdered.newSuspend { name = "root" }
            listOf("#3", "#0", "#2", "#4", "#1").forEach {
                val n = NodeOrdered.newSuspend {
                    name = it
                    parents = SizedCollection(listOf(root))
                }
            }

            root.children.toList().forEachIndexed { index, node ->
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
        var tasks by Task via ProjectTasks
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
            val p1 = Project.newSuspend { name = "Project 1" }
            val p2 = Project.newSuspend { name = "Project 2" }
            val t1 = Task.newSuspend { title = "Task 1" }
            val t2 = Task.newSuspend { title = "Task 2" }
            val t3 = Task.newSuspend { title = "Task 3" }

            ProjectTask.newSuspend(
                CompositeID {
                    it[ProjectTasks.task] = t1.id
                    it[ProjectTasks.project] = p1.id
                }
            ) { approved = true }
            ProjectTask.newSuspend(
                CompositeID {
                    it[ProjectTasks.task] = t2.id
                    it[ProjectTasks.project] = p2.id
                }
            ) { approved = false }
            ProjectTask.newSuspend(
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

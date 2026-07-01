package org.jetbrains.exposed.dao.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.dao.r2dbc.tests.shared.EntityTests
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class InsertTests : R2dbcDatabaseTestsBase() {
    private object OrderedDataTable : IntIdTable() {
        val name = text("name")
        val order = integer("order")
    }

    class OrderedData(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<OrderedData>(OrderedDataTable)

        var name by OrderedDataTable.name
        var order by OrderedDataTable.order
    }

    @Test
    fun testInsertWithColumnNamedWithKeyword() {
        withTables(OrderedDataTable) {
            val foo = OrderedData.new {
                name = "foo"
                order = 20
            }.flush()
            val bar = OrderedData.new {
                name = "bar"
                order = 10
            }.flush()

            assertEqualLists(listOf(bar, foo), OrderedData.all().orderBy(OrderedDataTable.order to SortOrder.ASC).toList())
        }
    }

    @Test
    fun testOptReferenceAllowsNullValues() {
        withTables(EntityTests.Posts) {
            val id1 = EntityTests.Posts.insertAndGetId {
                it[board] = null
                it[category] = null
            }

            val inserted1 = EntityTests.Posts.selectAll().where { EntityTests.Posts.id eq id1 }.single()
            assertNull(inserted1[EntityTests.Posts.board])
            assertNull(inserted1[EntityTests.Posts.category])

            val boardId = EntityTests.Boards.insertAndGetId {
                it[name] = Uuid.random().toString()
            }
            val categoryId = EntityTests.Categories.insert {
                it[title] = "Category"
            }[EntityTests.Categories.uniqueId]

            val id2 = EntityTests.Posts.insertAndGetId {
                it[board] = Op.nullOp()
                it[category] = categoryId
                it[board] = boardId.value
            }

            EntityTests.Posts.deleteWhere { EntityTests.Posts.id eq id2 }

            val nullableCategoryID: Uuid? = categoryId
            val nullableBoardId: Int? = boardId.value
            EntityTests.Posts.insertAndGetId {
                it[board] = Op.nullOp()
                it[category] = nullableCategoryID
                it[board] = nullableBoardId
            }
        }
    }
}

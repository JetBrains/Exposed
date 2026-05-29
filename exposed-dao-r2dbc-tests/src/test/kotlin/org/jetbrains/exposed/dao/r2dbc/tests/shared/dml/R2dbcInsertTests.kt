package org.jetbrains.exposed.dao.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.dao.r2dbc.tests.shared.R2dbcEntityTests
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
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

class R2dbcInsertTests : R2dbcDatabaseTestsBase() {
    private object OrderedDataTable : IntIdTable() {
        val name = text("name")
        val order = integer("order")
    }

    class OrderedData(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<OrderedData>(OrderedDataTable)

        var name by OrderedDataTable.name
        var order by OrderedDataTable.order
    }

    @Test
    fun testInsertWithColumnNamedWithKeyword() {
        withTables(OrderedDataTable) {
            val foo = OrderedData.new {
                name = "foo"
                order = 20
            }
            val bar = OrderedData.new {
                name = "bar"
                order = 10
            }

            assertEqualLists(listOf(bar, foo), OrderedData.all().orderBy(OrderedDataTable.order to SortOrder.ASC).toList())
        }
    }

    @Test
    fun testOptReferenceAllowsNullValues() {
        withTables(R2dbcEntityTests.Posts) {
            val id1 = R2dbcEntityTests.Posts.insertAndGetId {
                it[board] = null
                it[category] = null
            }

            val inserted1 = R2dbcEntityTests.Posts.selectAll().where { R2dbcEntityTests.Posts.id eq id1 }.single()
            assertNull(inserted1[R2dbcEntityTests.Posts.board])
            assertNull(inserted1[R2dbcEntityTests.Posts.category])

            val boardId = R2dbcEntityTests.Boards.insertAndGetId {
                it[name] = Uuid.random().toString()
            }
            val categoryId = R2dbcEntityTests.Categories.insert {
                it[title] = "Category"
            }[R2dbcEntityTests.Categories.uniqueId]

            val id2 = R2dbcEntityTests.Posts.insertAndGetId {
                it[board] = Op.nullOp()
                it[category] = categoryId
                it[board] = boardId.value
            }

            R2dbcEntityTests.Posts.deleteWhere { R2dbcEntityTests.Posts.id eq id2 }

            val nullableCategoryID: Uuid? = categoryId
            val nullableBoardId: Int? = boardId.value
            R2dbcEntityTests.Posts.insertAndGetId {
                it[board] = Op.nullOp()
                it[category] = nullableCategoryID
                it[board] = nullableBoardId
            }
        }
    }
}

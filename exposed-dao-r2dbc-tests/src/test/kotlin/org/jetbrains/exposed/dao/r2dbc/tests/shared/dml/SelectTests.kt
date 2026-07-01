package org.jetbrains.exposed.dao.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.dao.r2dbc.tests.shared.EntityTests
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class SelectTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testInListWithEntityIDColumns() {
        withTables(EntityTests.Posts, EntityTests.Boards, EntityTests.Categories) {
            val board1 = EntityTests.Board.new {
                this.name = "Board1"
            }.flush()

            val post1 = EntityTests.Post.new {
                this.board.set(board1)
            }.flush()

            EntityTests.Post.new {
                category.set(EntityTests.Category.new { title = "Category1" }.initializedEntity)
            }.flush()

            val result1 = EntityTests.Posts.selectAll().where {
                EntityTests.Posts.board inList listOf(board1.id)
            }.singleOrNull()?.get(EntityTests.Posts.id)
            assertEquals(post1.id, result1)

            val result2 = EntityTests.Board.find {
                EntityTests.Boards.id inList listOf(1, 2, 3, 4, 5)
            }.singleOrNull()
            assertEquals(board1, result2)

            val result3 = EntityTests.Board.find {
                EntityTests.Boards.id notInList listOf(1, 2, 3, 4, 5)
            }.singleOrNull()
            assertNull(result3)
        }
    }
}

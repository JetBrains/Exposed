package org.jetbrains.exposed.dao.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.dao.r2dbc.tests.shared.R2dbcEntityTests
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class R2dbcSelectTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testInListWithEntityIDColumns() {
        withTables(R2dbcEntityTests.Posts, R2dbcEntityTests.Boards, R2dbcEntityTests.Categories) {
            val board1 = R2dbcEntityTests.Board.new {
                this.name = "Board1"
            }

            val post1 = R2dbcEntityTests.Post.new {
                this.board set board1
            }

            R2dbcEntityTests.Post.new {
                category set R2dbcEntityTests.Category.new { title = "Category1" }
            }

            val result1 = R2dbcEntityTests.Posts.selectAll().where {
                R2dbcEntityTests.Posts.board inList listOf(board1.id)
            }.singleOrNull()?.get(R2dbcEntityTests.Posts.id)
            assertEquals(post1.id, result1)

            val result2 = R2dbcEntityTests.Board.find {
                R2dbcEntityTests.Boards.id inList listOf(1, 2, 3, 4, 5)
            }.singleOrNull()
            assertEquals(board1, result2)

            val result3 = R2dbcEntityTests.Board.find {
                R2dbcEntityTests.Boards.id notInList listOf(1, 2, 3, 4, 5)
            }.singleOrNull()
            assertNull(result3)
        }
    }
}

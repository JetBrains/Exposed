package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.joinQuery
import org.jetbrains.exposed.v1.core.lastQueryAlias
import org.jetbrains.exposed.v1.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.sql.insert
import org.jetbrains.exposed.v1.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.sql.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.expectException
import org.junit.Test

class LateralJoinTests : R2dbcDatabaseTestsBase() {
    // lateral join is also supported by MySql8 database, but at the current moment there is no related configuration
    private val lateralJoinSupportedDb = TestDB.ALL_POSTGRES + TestDB.ORACLE

    @Test
    fun testLateralJoinQuery() {
        withTestTablesAndDefaultData { parent, child, _ ->
            val query = parent.joinQuery(
                joinType = JoinType.CROSS,
                lateral = true
            ) {
                child.selectAll().where { child.value greater parent.value }
            }

            val subqueryAlias = query.lastQueryAlias ?: error("Alias must exist!")

            assertEqualLists(listOf(30), query.selectAll().map { it[subqueryAlias[child.value]] })
        }
    }

    @Test
    fun testLateralJoinQueryAlias() {
        withTestTablesAndDefaultData { parent, child, _ ->
            // Cross join
            child.selectAll().where { child.value greater parent.value }.alias("subquery")
                .let { subqueryAlias ->
                    val query = parent.join(
                        subqueryAlias,
                        JoinType.CROSS,
                        onColumn = parent.id,
                        otherColumn = subqueryAlias[child.parent],
                        lateral = true
                    )

                    assertEqualLists(listOf(30), query.selectAll().map { it[subqueryAlias[child.value]] })
                }

            // Left join
            child.selectAll().where { child.value greater parent.value }.alias("subquery")
                .let { subqueryAlias ->
                    val query = parent.join(
                        subqueryAlias,
                        JoinType.LEFT,
                        onColumn = parent.id,
                        otherColumn = subqueryAlias[child.parent],
                        lateral = true
                    )

                    assertEqualLists(listOf(30), query.selectAll().map { it[subqueryAlias[child.value]] })
                }

            // Left join to Alias
            val parentQuery = parent.selectAll().alias("parent_query")
            child.selectAll().where { child.value greater parentQuery[parent.value] }.alias("subquery")
                .let { subqueryAlias ->
                    val query = parentQuery
                        .join(
                            subqueryAlias,
                            JoinType.LEFT,
                            onColumn = parentQuery[parent.id],
                            otherColumn = subqueryAlias[child.parent],
                            lateral = true
                        )

                    assertEqualLists(listOf(30), query.selectAll().map { it[subqueryAlias[child.value]] })
                }
        }
    }

    @Test
    fun testLateralDirectTableJoin() {
        withTestTables { parent, child, _ ->
            // Explicit notation
            expectException<IllegalArgumentException> {
                parent.join(child, JoinType.LEFT, onColumn = parent.id, otherColumn = child.parent, lateral = true)
            }

            // Implicit notation
            expectException<IllegalArgumentException> {
                parent.join(child, JoinType.LEFT, lateral = true).selectAll().toList()
            }
        }
    }

    object Parent : IntIdTable("lateral_join_parent") {
        val value = integer("value")
    }

    object Child : IntIdTable("lateral_join_child") {
        val parent = reference("tester1", Parent.id)
        val value = integer("value")
    }

    private fun withTestTables(statement: suspend R2dbcTransaction.(Parent, Child, TestDB) -> Unit) {
        withTables(excludeSettings = TestDB.entries - lateralJoinSupportedDb, Parent, Child) { testDb ->
            statement(Parent, Child, testDb)
        }
    }

    private fun withTestTablesAndDefaultData(statement: suspend R2dbcTransaction.(Parent, Child, TestDB) -> Unit) {
        withTestTables { parent, child, testDb ->
            val id = parent.insertAndGetId { it[value] = 20 }

            listOf(10, 30).forEach { value ->
                child.insert {
                    it[child.value] = value
                    it[child.parent] = id
                }
            }

            statement(parent, child, testDb)
        }
    }
}

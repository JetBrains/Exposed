package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.JdbcTransaction
import org.junit.Test

class LateralJoinTests : DatabaseTestsBase() {
    // lateral join is also supported by MySql8 database, but at the current moment there is no related configuration
    private val lateralJoinSupportedDb = listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.ORACLE)

    @Test
    fun testLateralJoinQuery() {
        withTestTablesAndDefaultData { parent, child, _ ->
            val query = parent.joinQuery(
                joinType = JoinType.CROSS,
                lateral = true
            ) {
                child.selectAll().where { child.value greater parent.value }.limit(1)
            }

            val subqueryAlias = query.lastQueryAlias ?: error("Alias must exist!")

            assertEqualLists(listOf(30), query.selectAll().map { it[subqueryAlias[child.value]] })
        }
    }

    @Test
    fun testLateralJoinQueryAlias() {
        withTestTablesAndDefaultData { parent, child, _ ->
            // Cross join
            child.selectAll().where { child.value greater parent.value }.limit(1).alias("subquery")
                .let { subqueryAlias ->
                    val query = parent.join(subqueryAlias, JoinType.CROSS, onColumn = parent.id, otherColumn = subqueryAlias[child.parent], lateral = true)

                    assertEqualLists(listOf(30), query.selectAll().map { it[subqueryAlias[child.value]] })
                }

            // Left join
            child.selectAll().where { child.value greater parent.value }.alias("subquery")
                .let { subqueryAlias ->
                    val query = parent.join(subqueryAlias, JoinType.LEFT, onColumn = parent.id, otherColumn = subqueryAlias[child.parent], lateral = true)

                    assertEqualLists(listOf(30), query.selectAll().map { it[subqueryAlias[child.value]] })
                }

            // Left join to Alias
            val parentQuery = parent.selectAll().alias("parent_query")
            child.selectAll().where { child.value greater parentQuery[parent.value] }.alias("subquery")
                .let { subqueryAlias ->
                    val query = parentQuery
                        .join(subqueryAlias, JoinType.LEFT, onColumn = parentQuery[parent.id], otherColumn = subqueryAlias[child.parent], lateral = true)

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

    private fun withTestTables(statement: JdbcTransaction.(Parent, Child, TestDB) -> Unit) {
        withTables(excludeSettings = TestDB.entries - lateralJoinSupportedDb, Parent, Child) { testDb ->
            statement(Parent, Child, testDb)
        }
    }

    private fun withTestTablesAndDefaultData(statement: JdbcTransaction.(Parent, Child, TestDB) -> Unit) {
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

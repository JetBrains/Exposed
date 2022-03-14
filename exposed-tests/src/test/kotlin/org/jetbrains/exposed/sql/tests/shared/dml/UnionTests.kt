package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MariaDBDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import kotlin.test.assertTrue

class UnionTests : DatabaseTestsBase() {
    @Test
    fun `test limit`() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER)) { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            val sergeyQuery = users.select { users.id.eq("sergey") }
            andreyQuery.union(sergeyQuery).limit(1).map { it[users.id] }.apply {
                assertEquals(1, size)
                assertEquals("andrey", single())
            }
        }
    }

    @Test
    fun `test limit with offset`() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER)) { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            val sergeyQuery = users.select { users.id.eq("sergey") }
            andreyQuery.union(sergeyQuery).limit(1, 1).map { it[users.id] }.apply {
                assertEquals(1, size)
                assertEquals("sergey", single())
            }
        }
    }

    @Test
    fun `test count`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            val sergeyQuery = users.select { users.id eq "sergey" }
            assertEquals(2, andreyQuery.union(sergeyQuery).count())
        }
    }

    @Test
    fun `test orderBy`() {
        withCitiesAndUsers { _, users, _ ->
            val idAlias = users.id.alias("id_alias")
            val andreyQuery = users.slice(idAlias).select { users.id inList setOf("andrey", "sergey") }
            val union = andreyQuery.union(andreyQuery).orderBy(idAlias, SortOrder.DESC)

            union.map { it[idAlias] }.apply {
                assertEqualLists(this, "sergey", "andrey")
            }

            union.withDistinct(false).map { it[idAlias] }.apply {
                assertEqualLists(this, listOf("sergey", "sergey", "andrey", "andrey"))
            }
        }
    }

    @Test
    fun `test union of two queries`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            val sergeyQuery = users.select { users.id eq "sergey" }
            andreyQuery.union(sergeyQuery).map { it[users.id] }.apply {
                assertEquals(2, size)
                assertEqualLists(this, "andrey", "sergey")
            }
        }
    }

    @Test
    fun `test intersection of three queries`() {
        withCitiesAndUsers(listOf(TestDB.MYSQL)) { _, users, _ ->
            val usersQuery = users.selectAll()
            val sergeyQuery = users.select { users.id eq "sergey" }
            val expectedUsers = usersQuery.map { it[users.id] } + "sergey"
            val intersectAppliedFirst = when (currentDialect) {
                is PostgreSQLDialect, is SQLServerDialect, is MariaDBDialect -> true
                is H2Dialect -> (currentDialect as H2Dialect).isSecondVersion
                else -> false
            }
            usersQuery.unionAll(usersQuery).intersect(sergeyQuery).map { it[users.id] }.apply {
                if (intersectAppliedFirst) {
                    assertEquals(6, size)
                    assertEqualCollections(this, expectedUsers)
                } else {
                    assertEquals(1, size)
                    assertEquals("sergey", this.single())
                }
            }
        }
    }

    @Test
    fun `test except of two queries`() {
        withCitiesAndUsers(listOf(TestDB.MYSQL)) { _, users, _ ->
            val usersQuery = users.selectAll()
            val expectedUsers = usersQuery.map { it[users.id] } - "sergey"
            val sergeyQuery = users.select { users.id eq "sergey" }
            usersQuery.except(sergeyQuery).map { it[users.id] }.apply {
                assertEquals(4, size)
                assertEqualCollections(this, expectedUsers)
            }
        }
    }

    @Test
    fun `test except of three queries`() {
        withCitiesAndUsers(listOf(TestDB.MYSQL)) { _, users, _ ->
            val usersQuery = users.selectAll()
            val expectedUsers = usersQuery.map { it[users.id] } - "sergey"
            val sergeyQuery = users.select { users.id eq "sergey" }
            usersQuery.unionAll(usersQuery).except(sergeyQuery).map { it[users.id] }.apply {
                assertEquals(4, size)
                assertEqualCollections(this, expectedUsers)
            }
        }
    }

    @Test
    fun `test except of two excepts queries`() {
        withCitiesAndUsers(listOf(TestDB.MYSQL)) { _, users, _ ->
            val usersQuery = users.selectAll()
            val expectedUsers = usersQuery.map { it[users.id] } - "sergey" - "andrey"
            val sergeyQuery = users.select { users.id eq "sergey" }
            val andreyQuery = users.select { users.id eq "andrey" }
            usersQuery.except(sergeyQuery).except(andreyQuery).map { it[users.id] }.apply {
                assertEquals(3, size)
                assertEqualCollections(this, expectedUsers)
            }
        }
    }

    @Test
    fun `test union of more than two queries`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            val sergeyQuery = users.select { users.id eq "sergey" }
            val eugeneQuery = users.select { users.id eq "eugene" }
            andreyQuery.union(sergeyQuery).union(eugeneQuery).map { it[users.id] }.apply {
                assertEquals(3, size)
                assertEqualCollections(this, listOf("andrey", "sergey", "eugene"))
            }
        }
    }

    @Test
    fun `test union of sorted queries`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyOrSergeyQuery: Query =
                users.select { users.id inList setOf("andrey", "sergey") }.orderBy(users.id to SortOrder.DESC)

            if (currentDialect.supportsSubqueryUnions) {
                andreyOrSergeyQuery.union(andreyOrSergeyQuery).withDistinct(false).map { it[users.id] }.apply {
                    assertEquals(4, size)
                    assertTrue(all { it in setOf("andrey", "sergey") })
                }
            } else {
                expectException<IllegalArgumentException> {
                    andreyOrSergeyQuery.union(andreyOrSergeyQuery)
                }
            }
        }
    }

    @Test
    fun `test union of limited queries`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyOrSergeyQuery = users.select { users.id inList setOf("andrey", "sergey") }.limit(1)

            if (currentDialect.supportsSubqueryUnions) {
                andreyOrSergeyQuery.unionAll(andreyOrSergeyQuery).map { it[users.id] }.apply {
                    assertEquals(2, size)
                    assertTrue(all { it == "andrey" })
                }
            } else {
                expectException<IllegalArgumentException> {
                    andreyOrSergeyQuery.union(andreyOrSergeyQuery)
                }
            }
        }
    }

    @Test
    fun `test union of sorted and limited queries`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyOrSergeyQuery =
                users.select { users.id inList setOf("andrey", "sergey") }.orderBy(users.id to SortOrder.DESC).limit(1)

            if (currentDialect.supportsSubqueryUnions) {
                andreyOrSergeyQuery.unionAll(andreyOrSergeyQuery).map { it[users.id] }.apply {
                    assertEquals(2, size)
                    assertTrue(all { it == "sergey" })
                }
            } else {
                expectException<IllegalArgumentException> {
                    andreyOrSergeyQuery.union(andreyOrSergeyQuery)
                }
            }
        }
    }

    @Test
    fun `test union with distinct results`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            andreyQuery.union(andreyQuery).map { it[users.id] }.apply {
                assertEqualLists(this, "andrey")
            }
        }
    }

    @Test
    fun `test union with all results`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            andreyQuery.unionAll(andreyQuery).map { it[users.id] }.apply {
                assertEqualLists(this, "andrey", "andrey")
            }
        }
    }

    @Test
    fun `test union with all results of three queries`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            andreyQuery.unionAll(andreyQuery).unionAll(andreyQuery).map { it[users.id] }.apply {
                assertEqualLists(this, List(3) { "andrey" })
            }
        }
    }

    @Test
    fun `test union with expressions`() {
        withCitiesAndUsers { _, users, _ ->
            val exp1a = intLiteral(10)
            val exp1b = intLiteral(100)
            val exp2a = stringLiteral("aaa")
            val exp2b = stringLiteral("bbb")
            val andreyQuery1 = users.slice(users.id, exp1a, exp2a).select { users.id eq "andrey" }
            val andreyQuery2 = users.slice(users.id, exp1b, exp2b).select { users.id eq "andrey" }
            val unionAlias = andreyQuery1.unionAll(andreyQuery2)
            unionAlias.map { Triple(it[users.id], it[exp1a], it[exp2a]) }.apply {
                assertEqualLists(this, listOf(Triple("andrey", 10, "aaa"), Triple("andrey", 100, "bbb")))
            }
        }
    }

    @Test
    fun `test union with expression and alias`() {
        withCitiesAndUsers { _, users, _ ->
            val exp1a = intLiteral(10)
            val exp1b = intLiteral(100)
            val exp2a = stringLiteral("aaa")
            val exp2b = stringLiteral("bbb")
            val andreyQuery1 = users.slice(users.id, exp1a, exp2a).select { users.id eq "andrey" }
            val andreyQuery2 = users.slice(users.id, exp1b, exp2b).select { users.id eq "andrey" }
            val unionAlias = andreyQuery1.unionAll(andreyQuery2).alias("unionAlias")
            unionAlias.selectAll().map { Triple(it[unionAlias[users.id]], it[unionAlias[exp1a]], it[unionAlias[exp2a]]) }.apply {
                assertEqualLists(this, listOf(Triple("andrey", 10, "aaa"), Triple("andrey", 100, "bbb")))
            }
        }
    }
}

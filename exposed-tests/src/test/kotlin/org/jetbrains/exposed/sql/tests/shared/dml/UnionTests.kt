package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnionTests : DatabaseTestsBase() {
    @Test
    fun `test limit`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            val sergeyQuery = users.select { users.id.eq("sergey") }
            andreyQuery.union(sergeyQuery).limit(1).map { it[users.id] }.apply {
                assertEquals(1, size)
                assertEquals("andrey", first())
            }
        }
    }

    @Test
    fun `test limit with offset`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            val sergeyQuery = users.select { users.id.eq("sergey") }
            andreyQuery.union(sergeyQuery).limit(1, 1).map { it[users.id] }.apply {
                assertEquals(1, size)
                assertEquals("sergey", first())
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
            println(currentDialect)
            val idAlias = users.id.alias("id_alias")
            val andreyQuery = users.slice(idAlias).select { users.id inList setOf("andrey", "sergey") }
            val union = andreyQuery.union(andreyQuery).orderBy(idAlias, SortOrder.DESC)

            union.map { it[idAlias] }.apply {
                assertEquals(listOf("sergey", "andrey"), this)
            }

            union.withAll().map { it[idAlias] }.apply {
                assertEquals(listOf("sergey", "sergey", "andrey", "andrey"), this)
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
                assertTrue(containsAll(listOf("andrey", "sergey")))
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
                assertTrue(containsAll(setOf("andrey", "sergey", "eugene")))
            }
        }
    }

    @Test
    fun `test union of sorted queries`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyOrSergeyQuery = users.select { users.id inList setOf("andrey", "sergey") }.orderBy(users.id to SortOrder.DESC)

            if (currentDialect.supportsSubqueryUnions) {
                andreyOrSergeyQuery.union(andreyOrSergeyQuery).withAll().map { it[users.id] }.apply {
                    assertEquals(4, size)
                    assertTrue(all { it in setOf("andrey", "sergey") })
                }
            } else {
                assertFailsWith<IllegalArgumentException> {
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
                andreyOrSergeyQuery.union(andreyOrSergeyQuery).withAll().map { it[users.id] }.apply {
                    assertEquals(2, size)
                    assertTrue(all { it == "andrey"})
                }
            } else {
                assertFailsWith<IllegalArgumentException> {
                    andreyOrSergeyQuery.union(andreyOrSergeyQuery)
                }
            }
        }
    }

    @Test
    fun `test union of sorted and limited queries`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyOrSergeyQuery = users.select { users.id inList setOf("andrey", "sergey") }.orderBy(users.id to SortOrder.DESC).limit(1)

            if (currentDialect.supportsSubqueryUnions) {
                andreyOrSergeyQuery.union(andreyOrSergeyQuery).withAll().map { it[users.id] }.apply {
                    assertEquals(2, size)
                    assertTrue(all { it == "sergey"})
                }
            } else {
                assertFailsWith<IllegalArgumentException> {
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
                assertEquals(listOf("andrey"), this)
            }
        }
    }

    @Test
    fun `test union with all results`() {
        withCitiesAndUsers { _, users, _ ->
            val andreyQuery = users.select { users.id eq "andrey" }
            andreyQuery.union(andreyQuery).withAll().map { it[users.id] }.apply {
                assertEquals(List(2) { "andrey" }, this)
            }
        }
    }
}

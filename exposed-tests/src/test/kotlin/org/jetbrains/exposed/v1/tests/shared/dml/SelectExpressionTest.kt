package org.jetbrains.exposed.v1.tests.shared.dml

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.selectValue
import org.jetbrains.exposed.v1.jdbc.unionAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SelectExpressionTest : DatabaseTestsBase() {
    object Users : Table("tl_users") {
        val name = varchar("name", 50)
    }

    object Posts : Table("tl_posts") {
        val title = varchar("title", 50)
    }

    @Test
    fun testTablelessSelectExpressionsAndValues() {
        withDb {
            val expression = intLiteral(1)

            assertEquals(1, select(expression).single()[expression])
            assertEquals(1, selectValue(expression))

            assertEquals(1, selectValue(1))
            assertEquals("ready", selectValue("ready"))
            assertEquals(7, selectValue(intParam(7)))
            assertContentEquals(byteArrayOf(0x01, 0x7F), selectValue(byteArrayOf(0x01, 0x7F)))
        }
    }

    @Test
    fun testTablelessSelectFromDynamicExpressionList() {
        withDb {
            val first = intLiteral(1)
            val second = stringLiteral("two")
            val row = select(listOf(first, second)).single()

            assertEquals(1, row[first])
            assertEquals("two", row[second])
        }

        assertFailsWith<IllegalArgumentException> {
            select(emptyList())
        }
    }

    @Test
    fun testTablelessSelectHasOneImplicitRow() {
        withDb {
            assertEquals(false, select(literal(1)).empty())
            assertEquals(true, select(literal(1)).where { Op.FALSE }.empty())
        }
    }

    @Test
    fun testTablelessSelectCustomFunction() {
        withDb {
            val absolute = CustomFunction("ABS", IntegerColumnType(), intLiteral(-3))
            assertEquals(3, selectValue(absolute))
        }
    }

    @Test
    fun testTablelessSelectSequenceValue() {
        val sequence = Sequence("tl_seq")
        withDb(listOf(TestDB.POSTGRESQL, TestDB.H2_V2)) {
            SchemaUtils.createSequence(sequence)
            try {
                assertEquals(1, selectValue(sequence.nextIntVal()))
            } finally {
                SchemaUtils.dropSequence(sequence)
            }
        }
    }

    @Test
    fun testSeveralScalarSubqueriesInOneRow() {
        withTables(Users, Posts) {
            Users.insert { it[name] = "a" }
            Posts.insert { it[title] = "p" }
            Posts.insert { it[title] = "q" }

            // wrapAsExpression has no column type to normalize the dialect-specific COUNT result.
            val usersCount = wrapAsExpression<Number>(Users.select(Users.name.count()))
            val postsCount = wrapAsExpression<Number>(Posts.select(Posts.title.count()))
            val row = select(usersCount, postsCount).single()

            assertEquals(1L, row[usersCount]?.toLong())
            assertEquals(2L, row[postsCount]?.toLong())
        }
    }

    @Test
    fun testExistsAsTablelessValue() {
        withTables(Users) { db ->
            Users.insert { it[name] = "a" }
            var hasA: Expression<Boolean> = exists(Users.selectAll().where { Users.name eq "a" })
            if (db in setOf(TestDB.ORACLE, TestDB.SQLSERVER)) {
                hasA = case().When(hasA, booleanLiteral(true)).Else(booleanLiteral(false))
            }

            assertTrue(selectValue(hasA))
        }
    }

    @Test
    fun testInsertFromConditionalTablelessQuery() {
        withTables(Users) {
            val source = select(literal("solo")).where {
                notExists(Users.selectAll().where { Users.name eq "solo" })
            }

            Users.insert(source, columns = listOf(Users.name))
            Users.insert(source, columns = listOf(Users.name))

            assertEquals(1L, Users.selectAll().where { Users.name eq "solo" }.count())
        }
    }

    @Test
    fun testSetOperationOverTablelessQueries() {
        withDb {
            val first = intLiteral(1).alias("n")
            val second = intLiteral(2).alias("n")
            val query = select(first).unionAll(select(second))

            assertEqualLists(query.map { it[first] }, listOf(1, 2))
        }
    }

    @Test
    fun testTablelessQueryAsJoinedDerivedTable() {
        withTables(Users) {
            Users.insert { it[name] = "a" }
            Users.insert { it[name] = "b" }
            val selectedName = stringLiteral("a").alias("n")
            val row = select(selectedName).alias("v")
            val joined = Users.join(row, JoinType.INNER, Users.name, row[selectedName])

            assertEquals(1L, joined.selectAll().count())
        }
    }
}

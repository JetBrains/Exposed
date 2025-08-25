package org.jetbrains.exposed.v1.tests.shared.dml

import junit.framework.TestCase.assertTrue
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.entities.EntityTests
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertNull

class SelectTests : DatabaseTestsBase() {
    @Test
    fun testSelect() {
        withCitiesAndUsers { _, users, _ ->
            users.selectAll().where { users.id.eq("andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectAnd() {
        withCitiesAndUsers { _, users, _ ->
            users.selectAll().where { users.id.eq("andrey") and users.name.eq("Andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectOr() {
        withCitiesAndUsers { _, users, _ ->
            users.selectAll().where { users.id.eq("andrey") or users.name.eq("Andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectNot() {
        withCitiesAndUsers { _, users, _ ->
            users.selectAll().where { not(users.id.eq("andrey")) }.forEach {
                val userId = it[users.id]
                if (userId == "andrey") {
                    error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSizedIterable() {
        withCitiesAndUsers { cities, users, _ ->
            assertEquals(false, cities.selectAll().empty())
            assertEquals(true, cities.selectAll().where { cities.name eq "Qwertt" }.empty())
            assertEquals(0L, cities.selectAll().where { cities.name eq "Qwertt" }.count())
            assertEquals(3L, cities.selectAll().count())
            val cityID: Int? = null
            assertEquals(2L, users.selectAll().where { users.cityId eq cityID }.count())
        }
    }

    @Test
    fun testInListWithSingleExpression01() {
        withCitiesAndUsers { _, users, _ ->
            val r1 = users.selectAll().where {
                users.id inList listOf("andrey", "alex")
            }.orderBy(users.name).toList()

            assertEquals(2, r1.size)
            assertEquals("Alex", r1[0][users.name])
            assertEquals("Andrey", r1[1][users.name])

            val r2 = users.selectAll().where { users.id notInList listOf("ABC", "DEF") }.toList()

            assertEquals(users.selectAll().count().toInt(), r2.size)
        }
    }

    @Test
    fun testInListWithSingleExpression02() {
        withCitiesAndUsers { cities, _, _ ->
            val cityIds = cities.selectAll().map { it[cities.id] }.take(2)
            val r = cities.selectAll().where { cities.id inList cityIds }

            assertEquals(2L, r.count())
        }
    }

    @Test
    fun testInListWithPairExpressions01() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER)) { _, users, _ ->
            val r = users.selectAll().where {
                users.id to users.name inList listOf("andrey" to "Andrey", "alex" to "Alex")
            }.orderBy(users.name).toList()

            assertEquals(2, r.size)
            assertEquals("Alex", r[0][users.name])
            assertEquals("Andrey", r[1][users.name])
        }
    }

    @Test
    fun testInListWithPairExpressions02() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER)) { _, users, _ ->
            val r = users.selectAll().where { users.id to users.name inList listOf("andrey" to "Andrey") }.toList()

            assertEquals(1, r.size)
            assertEquals("Andrey", r[0][users.name])
        }
    }

    @Test
    fun testInListWithPairExpressionsAndEmptyList() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER)) { _, users, _ ->
            val r = users.selectAll().where { users.id to users.name inList emptyList() }.toList()

            assertEquals(0, r.size)
        }
    }

    @Test
    fun testNotInListWithPairExpressionsAndEmptyList() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER)) { _, users, _ ->
            val r = users.selectAll().where { users.id to users.name notInList emptyList() }.toList()

            assertEquals(users.selectAll().count().toInt(), r.size)
        }
    }

    @Test
    fun testInListWithTripleExpressions() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER)) { _, users, _ ->
            val userExpressions = Triple(users.id, users.name, users.cityId)
            val r1 = users.selectAll().where {
                userExpressions notInList listOf(Triple("alex", "Alex", null))
            }.toList()

            assertEquals(users.selectAll().count().toInt() - 1, r1.size)

            val r2 = users.selectAll().where {
                userExpressions inList listOf(Triple("andrey", "Andrey", 1))
            }.toList()

            assertEquals(1, r2.size)
        }
    }

    @Test
    fun testInListWithMultipleColumns() {
        val tester = object : Table("tester") {
            val num1 = integer("num_1")
            val num2 = double("num_2")
            val num3 = varchar("num_3", 8)
            val num4 = long("num_4")
        }

        fun Int.toColumnValue(index: Int) = when (index) {
            1 -> toDouble()
            2 -> toString()
            3 -> toLong()
            else -> this
        }

        withTables(tester) {
            repeat(3) { n ->
                tester.insert {
                    it[num1] = n
                    it[num2] = n.toDouble()
                    it[num3] = n.toString()
                    it[num4] = n.toLong()
                }
            }
            val expected = tester.selectAll().count().toInt()

            val allSameNumbers = List(3) { n -> List(4) { n.toColumnValue(it) } }
            val result1 = tester.selectAll().where { tester.columns inList allSameNumbers }.toList()
            assertEquals(expected, result1.size)

            val result2 = tester.selectAll().where { tester.columns inList listOf(allSameNumbers.first()) }.toList()
            assertEquals(1, result2.size)

            val allDifferentNumbers = List(3) { n -> List(4) { (n + it).toColumnValue(it) } }
            val result3 = tester.selectAll().where { tester.columns notInList allDifferentNumbers }.toList()
            assertEquals(expected, result3.size)

            val result4 = tester.selectAll().where { tester.columns notInList emptyList() }.toList()
            assertEquals(expected, result4.size)

            val result5 = tester.selectAll().where { tester.columns notInList allSameNumbers }.toList()
            assertEquals(0, result5.size)

            val result6 = tester.selectAll().where { tester.columns inList emptyList() }.toList()
            assertEquals(0, result6.size)
        }
    }

    @Test
    fun testInListWithEntityIDColumns() {
        withTables(EntityTests.Posts, EntityTests.Boards, EntityTests.Categories) {
            val board1 = EntityTests.Board.new {
                this.name = "Board1"
            }

            val post1 = EntityTests.Post.new {
                this.board = board1
            }

            EntityTests.Post.new {
                category = EntityTests.Category.new { title = "Category1" }
            }

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

    @Test
    fun testInSubQuery01() {
        withCitiesAndUsers { cities, _, _ ->
            val r = cities.selectAll().where { cities.id inSubQuery cities.select(cities.id).where { cities.id eq 2 } }
            assertEquals(1L, r.count())
        }
    }

    @Test
    fun testNotInSubQueryNoData() {
        withCitiesAndUsers { cities, _, _ ->
            val r = cities.selectAll().where { cities.id notInSubQuery cities.select(cities.id) }
            // no data since all ids are selected
            assertEquals(0L, r.count())
        }
    }

    @Test
    fun testNotInSubQuery() {
        withCitiesAndUsers { cities, _, _ ->
            val cityId = 2
            val r = cities.selectAll().where {
                cities.id notInSubQuery cities.select(cities.id).where { cities.id eq cityId }
            }.map { it[cities.id] }.sorted()
            assertEquals(2, r.size)
            // only 2 cities with id 1 and 2 respectively
            assertEquals(1, r[0])
            assertEquals(3, r[1])
            // there is no city with id=2
            assertNull(r.find { it == cityId })
        }
    }

    @Test
    fun testEqSubQuery() {
        withCitiesAndUsers { _, _, userData ->
            val query = userData.selectAll().where {
                userData.value eqSubQuery userData.select(userData.value).where { userData.user_id eq "eugene" }
            }
            assertEquals(2, query.count())
        }
    }

    @Test
    fun testNotEqSubQuery() {
        withCitiesAndUsers { _, _, userData ->
            val query = userData.selectAll().where {
                userData.value notEqSubQuery userData.select(userData.value).where { userData.user_id eq "sergey" }
            }
            assertEquals(3, query.count())
        }
    }

    @Test
    fun testLessSubQuery() {
        withCitiesAndUsers { _, _, userData ->
            val query = userData.selectAll().where {
                userData.value lessSubQuery userData.select(userData.value).where { userData.user_id eq "sergey" }
            }
            assertEquals(3, query.count())
        }
    }

    @Test
    fun testLessEqSubQuery() {
        withCitiesAndUsers { _, _, userData ->
            val query = userData.selectAll().where {
                userData.value lessEqSubQuery userData.select(userData.value).where { userData.user_id eq "eugene" }
            }
            assertEquals(3, query.count())
        }
    }

    @Test
    fun testGreaterSubQuery() {
        withCitiesAndUsers { _, _, userData ->
            val query = userData.selectAll().where {
                userData.value greaterSubQuery userData.select(userData.value).where { userData.value eq 10 }
            }
            assertEquals(3, query.count())
        }
    }

    @Test
    fun testGreaterEqSubQuery() {
        withCitiesAndUsers { _, _, userData ->
            val query = userData.selectAll().where {
                userData.value greaterEqSubQuery userData.select(userData.value).where { userData.value eq 10 }
            }
            assertEquals(4, query.count())
        }
    }

    private val inAnyAllFromTablesNotSupported = TestDB.ALL - (TestDB.ALL_POSTGRES + TestDB.ALL_H2_V2 + TestDB.MYSQL_V8).toSet()

    @Test
    fun testInTable() {
        withSalesAndSomeAmounts(excludeSettings = inAnyAllFromTablesNotSupported) { _, sales, someAmounts ->
            val r = sales.selectAll().where { sales.amount inTable someAmounts }
            assertEquals(2, r.count())
        }
    }

    @Test
    fun testNotInTable() {
        withSalesAndSomeAmounts(excludeSettings = inAnyAllFromTablesNotSupported) { _, sales, someAmounts ->
            val r = sales.selectAll().where { sales.amount notInTable someAmounts }
            assertEquals(5, r.count())
        }
    }

    @Test
    fun testEqAnyFromSubQuery() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLITE)) { cities, _, _ ->
            val r = cities.selectAll().where {
                cities.id eq anyFrom(cities.select(cities.id).where { cities.id eq 2 })
            }
            assertEquals(1L, r.count())
        }
    }

    @Test
    fun testNeqAnyFromSubQuery() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLITE)) { cities, _, _ ->
            val r = cities.selectAll().where {
                cities.id neq anyFrom(cities.select(cities.id).where { cities.id eq 2 })
            }
            assertEquals(2, r.count())
        }
    }

    private val anyAndAllFromArraysNotSupported = TestDB.ALL - (TestDB.ALL_POSTGRES + TestDB.ALL_H2_V2).toSet()

    @Test
    fun testEqAnyFromArray() {
        withCitiesAndUsers(exclude = anyAndAllFromArraysNotSupported) { _, users, _ ->
            val r = users.selectAll().where {
                users.id eq anyFrom(arrayOf("andrey", "alex"))
            }.orderBy(users.name).toList()

            assertEquals(2, r.size)
            assertEquals("Alex", r[0][users.name])
            assertEquals("Andrey", r[1][users.name])
        }
    }

    @Test
    fun testEqAnyFromList() {
        withCitiesAndUsers(exclude = anyAndAllFromArraysNotSupported) { _, users, _ ->
            val r = users.selectAll().where {
                users.id eq anyFrom(listOf("andrey", "alex"))
            }.orderBy(users.name).toList()

            assertEquals(2, r.size)
            assertEquals("Alex", r[0][users.name])
            assertEquals("Andrey", r[1][users.name])
        }
    }

    @Test
    fun testNeqAnyFromArray() {
        withCitiesAndUsers(exclude = anyAndAllFromArraysNotSupported) { _, users, _ ->
            val r = users.selectAll().where {
                users.id neq anyFrom(arrayOf("andrey"))
            }.orderBy(users.name)
            assertEquals(4, r.count())
        }
    }

    @Test
    fun testNeqAnyFromList() {
        withCitiesAndUsers(exclude = anyAndAllFromArraysNotSupported) { _, users, _ ->
            val r = users.selectAll().where {
                users.id neq anyFrom(listOf("andrey"))
            }.orderBy(users.name)
            assertEquals(4, r.count())
        }
    }

    @Test
    fun testNeqAnyFromEmptyArray() {
        withCitiesAndUsers(exclude = anyAndAllFromArraysNotSupported) { _, users, _ ->
            val r = users.selectAll().where { users.id neq anyFrom(emptyArray()) }.orderBy(users.name)
            assert(r.empty())
        }
    }

    @Test
    fun testNeqAnyFromEmptyList() {
        withCitiesAndUsers(exclude = anyAndAllFromArraysNotSupported) { _, users, _ ->
            val r = users.selectAll().where { users.id neq anyFrom(emptyList()) }.orderBy(users.name)
            assert(r.empty())
        }
    }

    @Test
    fun testGreaterEqAnyFromArray() {
        withSales(excludeSettings = anyAndAllFromArraysNotSupported) { _, sales ->
            val amounts = arrayOf(100, 1000).map { it.toBigDecimal() }.toTypedArray()
            val r = sales.selectAll().where { sales.amount greaterEq anyFrom(amounts) }
                .orderBy(sales.amount)
                .map { it[sales.product] }
            assertEquals(6, r.size)
            r.subList(0, 3).forEach { assertEquals("tea", it) }
            r.subList(3, 6).forEach { assertEquals("coffee", it) }
        }
    }

    @Test
    fun testGreaterEqAnyFromList() {
        withSales(excludeSettings = anyAndAllFromArraysNotSupported) { _, sales ->
            val amounts = listOf(100, 1000).map { it.toBigDecimal() }
            val r = sales.selectAll().where { sales.amount greaterEq anyFrom(amounts) }
                .orderBy(sales.amount)
                .map { it[sales.product] }
            assertEquals(6, r.size)
            r.subList(0, 3).forEach { assertEquals("tea", it) }
            r.subList(3, 6).forEach { assertEquals("coffee", it) }
        }
    }

    @Test
    fun testEqAnyFromTable() {
        withSalesAndSomeAmounts(excludeSettings = inAnyAllFromTablesNotSupported) { _, sales, someAmounts ->
            val r = sales.selectAll().where { sales.amount eq anyFrom(someAmounts) }
            assertEquals(2, r.count())
        }
    }

    @Test
    fun testNeqAllFromTable() {
        withSalesAndSomeAmounts(excludeSettings = inAnyAllFromTablesNotSupported) { _, sales, someAmounts ->
            val r = sales.selectAll().where { sales.amount neq allFrom(someAmounts) }
            assertEquals(5, r.count())
        }
    }

    @Test
    fun testGreaterEqAllFromSubQuery() {
        withSales(excludeSettings = listOf(TestDB.SQLITE)) { _, sales ->
            val r = sales.selectAll().where {
                sales.amount greaterEq allFrom(sales.select(sales.amount).where { sales.product eq "tea" })
            }
                .orderBy(sales.amount).map { it[sales.product] }
            assertEquals(4, r.size)
            assertEquals("tea", r.first())
            r.drop(1).forEach { assertEquals("coffee", it) }
        }
    }

    @Test
    fun testGreaterEqAllFromArray() {
        withSales(excludeSettings = anyAndAllFromArraysNotSupported) { _, sales ->
            val amounts = arrayOf(100, 1000).map { it.toBigDecimal() }.toTypedArray()
            val r = sales.selectAll().where { sales.amount greaterEq allFrom(amounts) }.toList()
            assertEquals(3, r.size)
            r.forEach { assertEquals("coffee", it[sales.product]) }
        }
    }

    @Test
    fun testGreaterEqAllFromList() {
        withSales(excludeSettings = anyAndAllFromArraysNotSupported) { _, sales ->
            val amounts = listOf(100, 1000).map { it.toBigDecimal() }
            val r = sales.selectAll().where { sales.amount greaterEq allFrom(amounts) }.toList()
            assertEquals(3, r.size)
            r.forEach { assertEquals("coffee", it[sales.product]) }
        }
    }

    @Test
    fun testGreaterEqAllFromTable() {
        withSalesAndSomeAmounts(excludeSettings = inAnyAllFromTablesNotSupported) { _, sales, someAmounts ->
            val r = sales.selectAll().where { sales.amount greaterEq allFrom(someAmounts) }.toList()
            assertEquals(3, r.size)
            r.forEach { assertEquals("coffee", it[sales.product]) }
        }
    }

    @Test
    fun testSelectDistinct() {
        val tbl = DMLTestsData.Cities
        withTables(tbl) {
            tbl.insert { it[tbl.name] = "test" }
            tbl.insert { it[tbl.name] = "test" }

            assertEquals(2L, tbl.selectAll().count())
            assertEquals(2L, tbl.selectAll().withDistinct().count())
            assertEquals(1L, tbl.select(tbl.name).withDistinct().count())
            assertEquals("test", tbl.select(tbl.name).withDistinct().single()[tbl.name])
        }
    }

    @Test
    fun testCompoundOp() {
        withCitiesAndUsers { _, users, _ ->
            val allUsers = setOf(
                "Andrey",
                "Sergey",
                "Eugene",
                "Alex",
                "Something"
            )
            val orOp = allUsers.map { users.name eq it }.compoundOr()
            val userNamesOr = users.selectAll().where(orOp).map { it[users.name] }.toSet()
            assertEquals(allUsers, userNamesOr)

            val andOp = allUsers.map { users.name eq it }.compoundAnd()
            assertEquals(0L, users.selectAll().where(andOp).count())
        }
    }

    @Test
    fun `test select on nullable reference column`() {
        val firstTable = object : IntIdTable("firstTable") {}
        val secondTable = object : IntIdTable("secondTable") {
            val firstOpt = optReference("first", firstTable)
        }

        withTables(firstTable, secondTable) {
            val firstId = firstTable.insertAndGetId { }
            secondTable.insert {
                it[firstOpt] = firstId
            }
            secondTable.insert { }

            assertEquals(2L, secondTable.selectAll().count())
            assertEquals(1, secondTable.selectAll().where { secondTable.firstOpt eq firstId.value }.toList().size)
            assertEquals(0, secondTable.selectAll().where { secondTable.firstOpt neq firstId.value }.toList().size)
            assertEquals(1, secondTable.selectAll().where { secondTable.firstOpt eq null }.count())
            assertEquals(1, secondTable.selectAll().where { secondTable.firstOpt neq null }.count())
        }
    }

    @Test
    fun `test that column length check is not affects select queries`() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = varchar("name", 10)
        }

        withTables(stringTable) {
            stringTable.insert {
                it[name] = "TestName"
            }
            assertEquals(1, stringTable.selectAll().where { stringTable.name eq "TestName" }.count())

            val veryLongString = "1".repeat(255)
            assertEquals(0, stringTable.selectAll().where { stringTable.name eq veryLongString }.count())
        }
    }

    @Test
    fun testSelectWithComment() {
        val text = "additional_info"
        val updatedText = "${text}_updated"

        withCitiesAndUsers { cities, _, _ ->
            val query = cities.selectAll().where { cities.name eq "Munich" }.limit(1).groupBy(cities.id, cities.name)
            val originalQuery = query.copy() // this remains unchanged by later chaining
            val originalSql = query.prepareSQL(this, false)

            val commentedFrontSql = query.comment(text).prepareSQL(this, false)
            assertEquals("/*$text*/ $originalSql", commentedFrontSql)

            val commentedTwiceSql = query.comment(text, AbstractQuery.CommentPosition.BACK).prepareSQL(this, false)
            assertEquals("/*$text*/ $originalSql /*$text*/", commentedTwiceSql)

            expectException<IllegalStateException> { // comment already exists at start of query
                query.comment("Testing").toList()
            }

            val commentedBackSql = query
                .adjustComments(AbstractQuery.CommentPosition.FRONT) // not setting new content removes comment at that position
                .adjustComments(AbstractQuery.CommentPosition.BACK, updatedText)
                .prepareSQL(this, false)
            assertEquals("$originalSql /*$updatedText*/", commentedBackSql)

            assertEquals(originalQuery.count(), originalQuery.comment(text).count())
            assertEquals(originalQuery.count(), originalQuery.comment(text, AbstractQuery.CommentPosition.BACK).count())
        }
    }

    @Test
    fun testSelectWithLimitAndOffset() {
        val alphabet = object : Table("alphabet") {
            val letter = char("letter")
        }

        withTables(alphabet) { testDb ->
            val allLetters = ('A'..'Z').toList()
            val amount = 10
            val start = 8L

            alphabet.batchInsert(allLetters) { letter ->
                this[alphabet.letter] = letter
            }

            val limitResult = alphabet.selectAll().limit(amount).map { it[alphabet.letter] }
            assertEqualLists(allLetters.take(amount), limitResult)

            val limitOffsetResult = alphabet.selectAll().limit(amount).offset(start).map { it[alphabet.letter] }
            assertEqualLists(allLetters.drop(start.toInt()).take(amount), limitOffsetResult)

            if (testDb != TestDB.SQLITE && testDb !in TestDB.ALL_MYSQL_MARIADB) {
                val offsetResult = alphabet.selectAll().offset(start).map { it[alphabet.letter] }
                assertEqualLists(allLetters.drop(start.toInt()), offsetResult)
            }
        }
    }

    @Test
    fun testSelectForUpdate() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER, TestDB.SQLITE)) { cities, _, _ ->
            val query = cities.selectAll()
                .forUpdate()

            assertTrue(query.prepareSQL(TransactionManager.current()).contains("FOR UPDATE"))
        }
    }
}

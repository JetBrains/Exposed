package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertNull

class SelectTests : DatabaseTestsBase() {

    val assertAndrey: DmlTestRuntime.(String, String) -> Unit = { actualUserId, actualUserName ->
        when (actualUserId) {
            "andrey" -> assertEquals("Andrey", actualUserName)
            else -> error("Unexpected user $actualUserId")
        }
    }

    val assertNotAndrey:  DmlTestRuntime.(String, String) -> Unit = { actualUserId, actualUserName ->
        when (actualUserId) {
            "andrey" -> error("Unexpected user $actualUserName")
            else -> {}
        }
    }

    // select expressions
    @Test
    fun testSelect() {
        withCitiesAndUsers {
            users.select { users.id.eq("andrey") }
                .forEach { assertAndrey(it[users.id], it[users.name]) }

            scopedUsers.select { scopedUsers.name eq "Eugene" }
                .forEach {
                    val userId = it[scopedUsers.id]
                    val userName = it[scopedUsers.name]
                    when (userId) {
                        "eugene" -> assertEquals("Eugene", userName)
                        else -> error("Unexpected user $userId")
                    }
                }

            scopedUsers.select { scopedUsers.name eq "andrey" }
                .toList().let { assertTrue(it.isEmpty()) }

            scopedUsers.stripDefaultScope()
                .select { scopedUsers.id.eq("andrey") }
                .forEach { assertAndrey(it[scopedUsers.id], it[scopedUsers.name]) }
        }
    }

    @Test
    fun testSelectAnd() {
        withCitiesAndUsers {
            users.select { users.id.eq("andrey") and users.name.eq("Andrey") }
                .forEach { assertAndrey(it[users.id], it[users.name]) }

            scopedUsers
                .select { scopedUsers.id.eq("eugene") and scopedUsers.name.eq("Eugene") }
                .forEach {
                    val userId = it[scopedUsers.id]
                    val userName = it[scopedUsers.name]
                    when (userId) {
                        "eugene" -> assertEquals("Eugene", userName)
                        else -> error("Unexpected user $userId")
                    }
                }

            scopedUsers.select {
                scopedUsers.id.eq("andrey") and
                scopedUsers.name.eq("Andrey")
            }.toList().apply { assertTrue(isEmpty()) }


            scopedUsers.stripDefaultScope()
                .select {
                    scopedUsers.id.eq("andrey") and
                    scopedUsers.name.eq("Andrey")
                }.forEach { assertAndrey(it[scopedUsers.id], it[scopedUsers.name]) }
        }
    }

    @Test
    fun testSelectOr() {
        withCitiesAndUsers {
            users.select {
                users.id.eq("andrey") or
                users.name.eq("Andrey")
            }.forEach { assertAndrey(it[users.id], it[users.name]) }

            scopedUsers.select {
                scopedUsers.id.eq("eugene") or
                scopedUsers.name.eq("Eugene")
            }.forEach {
                val userId = it[scopedUsers.id]
                val userName = it[scopedUsers.name]
                when (userId) {
                    "eugene" -> assertEquals("Eugene", userName)
                    else -> error("Unexpected user $userId")
                }
            }

            scopedUsers.select {
                scopedUsers.id.eq("andrey") or
                scopedUsers.name.eq("Andrey")
            }.toList().apply { assertTrue(isEmpty()) }


            scopedUsers.stripDefaultScope().select {
                scopedUsers.id.eq("andrey") or
                    scopedUsers.name.eq("Andrey")
            }.forEach { assertAndrey(it[scopedUsers.id], it[scopedUsers.name]) }
        }
    }

    @Test
    fun testSelectNot() {
        withCitiesAndUsers {
            users.select { not(users.id.eq("andrey")) }
                .forEach { assertNotAndrey(it[users.id], it[users.name]) }

            scopedUsers.select { not(scopedUsers.id.eq("eugene")) }
                .forEach {
                    val userId = it[scopedUsers.id]
                    val userName = it[scopedUsers.name]
                    if (userId != "sergey") {
                        error("Unexpected user $userName")
                    }
                    assertNotAndrey(userId, userName)
                }

            scopedUsers.stripDefaultScope()
                .select { not(scopedUsers.id.eq("eugene")) }
                .map { it[scopedUsers.id] }.toSet()
                .let { assertEquals(it, setOf("andrey", "sergey", "alex", "smth")) }
        }
    }

    @Test
    fun testSizedIterable() {
        withCitiesAndUsers {
            assertEquals(false, cities.selectAll().empty())
            assertEquals(true, cities.select { cities.name eq "Qwertt" }.empty())
            assertEquals(0L, cities.select { cities.name eq "Qwertt" }.count())
            assertEquals(3L, cities.selectAll().count())

            assertEquals(false, scopedUsers.selectAll().empty())
            assertEquals(false, scopedUsers.stripDefaultScope().selectAll().empty())

            assertEquals(2L, scopedUsers.selectAll().count())
            assertEquals(5L, scopedUsers.stripDefaultScope().selectAll().count())

            assertEquals(true,
                         scopedUsers
                             .select { scopedUsers.cityId neq munichId() }
                             .empty())
            assertEquals(false,
                         scopedUsers.stripDefaultScope()
                             .select { scopedUsers.cityId neq munichId() }
                             .empty())

            assertEquals(0L,
                         scopedUsers
                             .select { scopedUsers.cityId neq munichId() }
                             .count())
            assertEquals(3L,
                         scopedUsers
                             .stripDefaultScope()
                             .select {
                                 scopedUsers.cityId neq munichId() or
                                     scopedUsers.cityId.isNull()
                             }.count())
        }
    }

    @Test
    fun testInList01() {
        withCitiesAndUsers {
            users.select { users.id inList listOf("andrey", "alex") }
                .orderBy(users.name)
                .toList()
                .let { r ->
                    assertEquals(2, r.size)
                    assertEquals("Alex", r[0][users.name])
                    assertEquals("Andrey", r[1][users.name])
                }

            scopedUsers
                .select { scopedUsers.id inList listOf("sergey", "andrey") }
                .orderBy(scopedUsers.name)
                .toList()
                .let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Sergey", r[0][scopedUsers.name])
                }

            scopedUsers
                .stripDefaultScope()
                .select { scopedUsers.id inList listOf("sergey", "andrey") }
                .orderBy(scopedUsers.name)
                .toList()
                .let { r ->
                    assertEquals(2, r.size)
                    assertEquals("Andrey", r[0][scopedUsers.name])
                    assertEquals("Sergey", r[1][scopedUsers.name])
                }
        }
    }

    @Test
    fun testInList02() {
        withCitiesAndUsers {
            val cityIds = cities.selectAll().map { it[cities.id] }.take(2)
            val r = cities.select { cities.id inList cityIds }

            assertEquals(2L, r.count())

            scopedUsers
                .selectAll()
                .map { it[scopedUsers.id] }
                .take(2)
                .let { scopedUserIds ->
                    scopedUsers
                        .select { scopedUsers.id inList scopedUserIds }
                        .let { query -> assertEquals(2L, query.count()) }
                }
        }
    }

    @Test
    fun testInList03() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER)) {
            users.select {
                    users.id to users.name inList listOf(
                        "andrey" to "Andrey",
                           "alex" to "Alex"
                    )
                }.orderBy(users.name)
                .toList().let { r ->
                    assertEquals(2, r.size)
                    assertEquals("Alex", r[0][users.name])
                    assertEquals("Andrey", r[1][users.name])
                }

            scopedUsers.select {
                    scopedUsers.id to scopedUsers.name inList listOf(
                        "andrey" to "Andrey",
                        "sergey" to "Sergey"
                    )
                }.orderBy(scopedUsers.name)
                .toList()
                .let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Sergey", r[0][scopedUsers.name])
                }

            scopedUsers.stripDefaultScope()
                .select {
                    scopedUsers.id to scopedUsers.name inList listOf(
                        "andrey" to "Andrey",
                        "sergey" to "Sergey"
                    )
                }.orderBy(scopedUsers.name)
                .toList()
                .let { r ->
                    assertEquals(2, r.size)
                    assertEquals("Andrey", r[0][scopedUsers.name])
                    assertEquals("Sergey", r[1][scopedUsers.name])
                }
        }
    }

    @Test
    fun testInList04() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) {
            users.select {
                users.id to users.name inList
                    listOf("andrey" to "Andrey")
            }.toList().let { r ->
                assertEquals(1, r.size)
                assertEquals("Andrey", r[0][users.name])
            }


            scopedUsers.select {
                scopedUsers.id to scopedUsers.name inList
                    listOf("sergey" to "Sergey")
            }.toList().let { r ->
                assertEquals(1, r.size)
                assertEquals("Sergey", r[0][scopedUsers.name])
            }


            scopedUsers.select {
                scopedUsers.id to scopedUsers.name inList
                    listOf("andrey" to "Andrey")
            }.toList().let { r -> assertEquals(0, r.size) }


            scopedUsers.stripDefaultScope()
                .select { scopedUsers.id to scopedUsers.name inList listOf("andrey" to "Andrey") }
                .toList().let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Andrey", r[0][scopedUsers.name])
                }
        }
    }

    @Test
    fun testInList05() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) {
            users.select { users.id to users.name inList emptyList() }
                .toList().let { r -> assertEquals(0, r.size) }

            scopedUsers.select { scopedUsers.id to scopedUsers.name inList emptyList() }
                .toList().let { r -> assertEquals(0, r.size) }

            scopedUsers.stripDefaultScope()
                .select { scopedUsers.id to scopedUsers.name inList emptyList() }
                .toList().let { r -> assertEquals(0, r.size) }
        }
    }

    @Test
    fun testInList06() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) {
            users.select { users.id to users.name notInList emptyList() }
                .toList().let { r -> assertEquals(5, r.size) }

            scopedUsers.select { scopedUsers.id to scopedUsers.name notInList emptyList() }
                .toList().let { r -> assertEquals(2, r.size)  }

            scopedUsers.stripDefaultScope()
                .select { scopedUsers.id to scopedUsers.name notInList emptyList() }
                .toList().let { r -> assertEquals(5, r.size)  }
        }
    }

    @Test
    fun testInList07() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) {
            users.select {
                Triple(users.id, users.name, users.cityId) notInList
                    listOf(Triple("alex", "Alex", null))
            }.toList()
            .let { r -> assertEquals(4, r.size) }

            scopedUsers.select {
                Triple(scopedUsers.id, scopedUsers.name, scopedUsers.cityId) notInList
                    listOf(Triple("sergey", "Sergey", munichId()))
            }.toList()
            .let { r -> assertEquals(1, r.size) }

            scopedUsers.stripDefaultScope().select {
                Triple(scopedUsers.id, scopedUsers.name, scopedUsers.cityId) notInList
                    listOf(Triple("sergey", "Sergey", munichId()))
            }.toList()
            .let { r -> assertEquals(4, r.size) }
        }
    }

    @Test
    fun testInSubQuery01() {
        withCitiesAndUsers {
            cities.select {
                cities.id inSubQuery (cities
                    .slice(cities.id)
                    .select { cities.id eq 2 })
            }.let { r -> assertEquals(1L, r.count()) }

            scopedUsers.select {
                scopedUsers.id inSubQuery (scopedUsers
                    .slice(scopedUsers.id)
                    .select { scopedUsers.id eq "sergey" })
            }.let { r -> assertEquals(1L, r.count()) }

            scopedUsers.stripDefaultScope()
                .select {
                    scopedUsers.id inSubQuery
                        (scopedUsers.slice(scopedUsers.id)
                            .select { scopedUsers.id eq "sergey" })
                }.let { r -> assertEquals(1L, r.count()) }
        }
    }

    @Test  // no data since all ids are selected
    fun testNotInSubQueryNoData() {
        withCitiesAndUsers {
            cities.select {
                cities.id notInSubQuery
                    (cities.slice(cities.id).selectAll())
            }.let { r -> assertEquals(0L, r.count()) }

            scopedUsers.select {
                scopedUsers.id notInSubQuery
                    (scopedUsers.slice(scopedUsers.id).selectAll())
            }.let { r -> assertEquals(0L, r.count()) }

            scopedUsers.stripDefaultScope()
                .select {
                    scopedUsers.id notInSubQuery
                        (scopedUsers.slice(scopedUsers.id)
                            .selectAll())
                }.let { r -> assertEquals(3L, r.count()) }
        }
    }

    @Test
    fun testNotInSubQuery() {
        withCitiesAndUsers {
            val cityId = 2

            cities.select {
                    cities.id notInSubQuery
                        (cities.slice(cities.id)
                            .select { cities.id eq cityId })
                }.map { it[cities.id] }
                .sorted().let { r ->
                    assertEquals(2, r.size)
                    // only 2 cities with id 1 and 2 respectively
                    assertEquals(1, r[0])
                    assertEquals(3, r[1])
                    // there is no city with id=2
                    assertNull(r.find { it == cityId })
                }

            scopedUsers.select {
                scopedUsers.id notInSubQuery
                    (scopedUsers.slice(scopedUsers.id)
                        .select { scopedUsers.id eq "sergey" })
            }.map { it[scopedUsers.id] }
            .let { r ->
                assertEquals(1, r.size)
                assertEquals("eugene", r[0])

            }

            scopedUsers.stripDefaultScope()
                .select {
                    scopedUsers.id notInSubQuery
                        (scopedUsers.slice(scopedUsers.id)
                            .select { scopedUsers.id eq "sergey" })
                }.map { it[scopedUsers.id] }
                .toSet().let { r ->
                    assertEquals(4, r.size)
                    assertEquals(r, setOf("andrey", "eugene", "alex", "smth"))
                }
        }
    }

    @Test
    fun testSelectDistinct() {
        withTables(Cities) {
            Cities.deleteAll()
            Cities.insert { it[name] = "test" }
            Cities.insert { it[name] = "test" }

            assertEquals(2L, Cities.selectAll().count())
            assertEquals(2L, Cities.selectAll().withDistinct().count())
            assertEquals(1L, Cities.slice(Cities.name).selectAll().withDistinct().count())
            assertEquals("test", Cities.slice(Cities.name).selectAll().withDistinct().single()[Cities.name])
        }
    }

    @Test
    fun testCompoundOp() {
        withCitiesAndUsers {
            val allUsers = setOf("Andrey", "Sergey", "Eugene", "Alex", "Something")
            val orOp = allUsers.map { Op.build { users.name eq it } }.compoundOr()
            val userNamesOr = users.select(orOp).map { it[users.name] }.toSet()
            assertEquals(allUsers, userNamesOr)

            val andOp = allUsers.map { Op.build { users.name eq it } }.compoundAnd()
            assertEquals(0L, users.select(andOp).count())

            allUsers.map { Op.build { scopedUsers.name eq it } }
                .compoundOr()
                .let { scopedOrOp ->
                    scopedUsers.select(scopedOrOp)
                        .map { it[scopedUsers.name] }
                        .toSet()
                        .let { names -> assertEquals(setOf( "Sergey", "Eugene"), names) }
                }

            allUsers.map { Op.build { scopedUsers.name eq it } }
                .compoundOr()
                .let { op ->
                    scopedUsers.stripDefaultScope()
                        .select(op)
                        .map { it[scopedUsers.name] }
                        .toSet()
                        .let { names ->
                            assertEquals(setOf("Andrey", "Eugene", "Alex", "Something", "Sergey")
                                         , names)
                        }
                }
        }
    }

    @Test
    fun `test select on nullable reference column`() {
        val firstTable = object : IntIdTable("first") {}
        val secondTable = object : IntIdTable("second") {
            val firstOpt = optReference("first", firstTable)
        }

        withTables(firstTable, secondTable) {
            val firstId = firstTable.insertAndGetId { }
            secondTable.insert {
                it[firstOpt] = firstId
            }
            secondTable.insert { }

            assertEquals(2L, secondTable.selectAll().count())
            val secondEntries = secondTable.select { secondTable.firstOpt eq firstId.value }.toList()

            assertEquals(1, secondEntries.size)
        }
    }

    @Test
    fun `test select on nullable reference column with a default scope`() {
        val actualTenantId = "tenant 1"
        val firstTable = object : IntIdTable("first") {}
        val secondTable = object : IntIdTable("second") {
            val tenantId = varchar("TENANT_ID", 50).nullable()
            val firstOpt = optReference("first", firstTable)
            override val defaultScope = { Op.build { tenantId eq actualTenantId } }
        }

        withTables(firstTable, secondTable) {
            val firstId = firstTable.insertAndGetId { }
            secondTable.insert {
                it[firstOpt] = firstId
                it[tenantId] = actualTenantId
            }
            secondTable.insert {  it[tenantId] = actualTenantId }
            secondTable.insert {  it[tenantId] = "other tenant id" }
            secondTable.insert {  it[tenantId] = null }

            assertEquals(2L, secondTable.selectAll().count())
            secondTable.select { secondTable.firstOpt eq firstId.value }
                .toList().let { secondEntries -> assertEquals(1, secondEntries.size) }

            secondTable.stripDefaultScope()
                .select { secondTable.firstOpt eq firstId.value }
                .toList().let { secondEntries -> assertEquals(1, secondEntries.size) }
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
            assertEquals(1, stringTable.select { stringTable.name eq "TestName" }.count())

            val veryLongString = "1".repeat(255)
            assertEquals(0, stringTable.select { stringTable.name eq veryLongString }.count())
        }
    }
}

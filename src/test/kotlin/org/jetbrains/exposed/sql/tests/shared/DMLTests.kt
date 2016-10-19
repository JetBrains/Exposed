package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.joda.time.DateTime
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

object DMLTestsData {
    object Cities : Table() {
        val id = integer("id").autoIncrement().primaryKey() // PKColumn<Int>
        val name = varchar("name", 50) // Column<String>
    }

    object Users : Table() {
        val id = varchar("id", 10).primaryKey() // PKColumn<String>
        val name = varchar("name", length = 50) // Column<String>
        val cityId = (integer("city_id") references Cities.id).nullable() // Column<Int?>
    }

    object UserData : Table() {
        val user_id = varchar("user_id", 10) references Users.id
        val comment = varchar("comment", 30)
        val value = integer("value")
    }

    enum class E {
        ONE,
        TWO,
        THREE
    }

    object Misc : Table() {
        val n = integer("n")
        val nn = integer("nn").nullable()

        val d = date("d")
        val dn = date("dn").nullable()

        val t = datetime("t")
        val tn = datetime("tn").nullable()

        val e = enumeration("e", E::class.java)
        val en = enumeration("en", E::class.java).nullable()

        val s = varchar("s", 100)
        val sn = varchar("sn", 100).nullable()

        val dc = decimal("dc", 12, 2)
        val dcn = decimal("dcn", 12, 2).nullable()

    }
}

class DMLTests() : DatabaseTestsBase() {
    fun withCitiesAndUsers(exclude: List<TestDB> = emptyList(), statement: Transaction.(cities: DMLTestsData.Cities, users: DMLTestsData.Users, userData: DMLTestsData.UserData) -> Unit) {
        val Users = DMLTestsData.Users;
        val Cities = DMLTestsData.Cities;
        val UserData = DMLTestsData.UserData;

        withTables(exclude, Cities, Users, UserData) {
            val saintPetersburgId = Cities.insert {
                it[name] = "St. Petersburg"
            } get Cities.id

            val munichId = Cities.insert {
                it[name] = "Munich"
            } get Cities.id

            Cities.insert {
                it[name] = "Prague"
            }

            Users.insert {
                it[id] = "andrey"
                it[name] = "Andrey"
                it[cityId] = saintPetersburgId
            }

            Users.insert {
                it[id] = "sergey"
                it[name] = "Sergey"
                it[cityId] = munichId
            }

            Users.insert {
                it[id] = "eugene"
                it[name] = "Eugene"
                it[cityId] = munichId
            }

            Users.insert {
                it[id] = "alex"
                it[name] = "Alex"
                it[cityId] = null
            }

            Users.insert {
                it[id] = "smth"
                it[name] = "Something"
                it[cityId] = null
            }

            UserData.insert {
                it[user_id] = "smth"
                it[comment] = "Something is here"
                it[value] = 10
            }

            UserData.insert {
                it[user_id] = "eugene"
                it[comment] = "Comment for Eugene"
                it[value] = 20
            }

            UserData.insert {
                it[user_id] = "sergey"
                it[comment] = "Comment for Sergey"
                it[value] = 30
            }
            statement (Cities, Users, UserData)
        }
    }

    @Test fun testUpdate01() {
        withCitiesAndUsers { cities, users, userData ->
            val alexId = "alex"
            val alexName = users.slice(users.name).select {users.id.eq(alexId)}.first()[users.name]
            assertEquals("Alex", alexName);

            val newName = "Alexey"
            users.update({users.id.eq(alexId)}) {
                it[users.name] = newName
            }

            val alexNewName = users.slice(users.name).select {users.id.eq(alexId)}.first()[users.name]
            assertEquals(newName, alexNewName);
        }
    }

    @Test fun testPreparedStatement() {
        withCitiesAndUsers { cities, users, userData ->
            val name = users.select{users.id eq "eugene"}.first()[users.name]
            assertEquals("Eugene", name)
        }
    }

    @Test fun testDelete01() {
        withCitiesAndUsers { cities, users, userData ->
            userData.deleteAll()
            val userDataExists = userData.selectAll().any()
            assertEquals(false, userDataExists)

            val smthId = users.slice(users.id).select{users.name.like("%thing")}.single()[users.id]
            assertEquals ("smth", smthId)

            users.deleteWhere {users.name like  "%thing"}
            val hasSmth = users.slice(users.id).select{users.name.like("%thing")}.any()
            assertEquals(false, hasSmth)
        }
    }

    // manual join
    @Test fun testJoin01() {
        withCitiesAndUsers { cities, users, userData ->
            (users innerJoin cities).slice(users.name, cities.name).
            select{(users.id.eq("andrey") or users.name.eq("Sergey")) and users.cityId.eq(cities.id)}.forEach {
                val userName = it[users.name]
                val cityName = it[cities.name]
                when (userName) {
                    "Andrey" -> assertEquals("St. Petersburg", cityName)
                    "Sergey" -> assertEquals("Munich", cityName)
                    else -> error ("Unexpected user $userName")
                }
            }
        }
    }

    // join with foreign key
    @Test fun testJoin02() {
        withCitiesAndUsers { cities, users, userData ->
            val stPetersburgUser = (users innerJoin cities).slice(users.name, users.cityId, cities.name).
            select{cities.name.eq("St. Petersburg") or users.cityId.isNull()}.single()
            assertEquals("Andrey", stPetersburgUser[users.name])
            assertEquals("St. Petersburg", stPetersburgUser[cities.name])
        }
    }

    // triple join
    @Test fun testJoin03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users innerJoin userData).selectAll().orderBy(users.id).toList()
            assertEquals (2, r.size)
            assertEquals("Eugene", r[0][users.name])
            assertEquals("Comment for Eugene", r[0][userData.comment])
            assertEquals("Munich", r[0][cities.name])
            assertEquals("Sergey", r[1][users.name])
            assertEquals("Comment for Sergey", r[1][userData.comment])
            assertEquals("Munich", r[1][cities.name])
        }
    }

    // triple join
    @Test fun testJoin04() {
        val Numbers = object : Table() {
            val id = integer("id").primaryKey()
        }

        val Names = object : Table() {
            val name = varchar("name", 10).primaryKey()
        }

        val Map = object: Table () {
            val id_ref = integer("id_ref") references Numbers.id
            val name_ref = varchar("name_ref", 10) references Names.name
        }

        withTables (Numbers, Names, Map) {
            Numbers.insert { it[id] = 1 }
            Numbers.insert { it[id] = 2 }
            Names.insert { it[name] = "Foo"}
            Names.insert { it[name] = "Bar"}
            Map.insert {
                it[id_ref] = 2
                it[name_ref] = "Foo"
            }

            val r = (Numbers innerJoin Map innerJoin Names).selectAll().toList()
            assertEquals(1, r.size)
            assertEquals(2, r[0][Numbers.id])
            assertEquals("Foo", r[0][Names.name])
        }
    }

    @Test fun testGroupBy01() {
        withCitiesAndUsers { cities, users, userData ->
            ((cities innerJoin users).slice(cities.name, users.id.count()).selectAll().groupBy(cities.name)).forEach {
                val cityName = it[cities.name]
                val userCount = it[users.id.count()]

                when (cityName) {
                    "Munich" -> assertEquals(2, userCount)
                    "Prague" -> assertEquals(0, userCount)
                    "St. Petersburg" -> assertEquals(1, userCount)
                    else -> error ("Unknow city $cityName")
                }
            }
        }
    }

    @Test fun testGroupBy02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, users.id.count()).selectAll().groupBy(cities.name).having{users.id.count() eq 1}.toList()
            assertEquals(1, r.size)
            assertEquals("St. Petersburg", r[0][cities.name])
            val count = r[0][users.id.count()]
            assertEquals(1, count)
        }
    }

    @Test fun testGroupBy03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, users.id.count(), cities.id.max()).selectAll()
                    .groupBy(cities.name)
                    .having{users.id.count().eq(cities.id.max())}
                    .orderBy(cities.name)
                    .toList()

            assertEquals(2, r.size)
            0.let {
                assertEquals("Munich", r[it][cities.name])
                val count = r[it][users.id.count()]
                assertEquals(2, count)
                val max = r[it][cities.id.max()]
                assertEquals(2, max)
            }
            1.let {
                assertEquals("St. Petersburg", r[it][cities.name])
                val count = r[it][users.id.count()]
                assertEquals(1, count)
                val max = r[it][cities.id.max()]
                assertEquals(1, max)
            }
        }
    }

    @Test fun testGroupBy04() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, users.id.count(), cities.id.max()).selectAll()
                    .groupBy(cities.name)
                    .having{users.id.count() lessEq 42}
                    .orderBy(cities.name)
                    .toList()

            assertEquals(2, r.size)
            0.let {
                assertEquals("Munich", r[it][cities.name])
                val count = r[it][users.id.count()]
                assertEquals(2, count)
            }
            1.let {
                assertEquals("St. Petersburg", r[it][cities.name])
                val count = r[it][users.id.count()]
                assertEquals(1, count)
            }
        }
    }

    @Test fun testGroupBy05() {
        withCitiesAndUsers { cities, users, userData ->
            val maxNullableCityId = users.cityId.max()

            users.slice(maxNullableCityId).selectAll()
                    .map { it[maxNullableCityId] }.let { result ->
                assertTrue(result.size == 1)
                assertNotNull(result.single())
            }

            users.slice(maxNullableCityId).select { users.cityId.isNull() }
                    .map { it[maxNullableCityId] }.let { result ->
                assertTrue(result.size == 1)
                assertNull(result.single())
            }
        }
    }

    @Test fun testGroupBy06() {
        withCitiesAndUsers { cities, users, userData ->
            val maxNullableId: Max<Int> = cities.id.max()

            cities.slice(maxNullableId).selectAll()
                    .map { it[maxNullableId] }.let { result ->
                assertTrue(result.size == 1)
                assertNotNull(result.single())
            }

            cities.slice(maxNullableId).select { cities.id.isNull() }
                    .map { it[maxNullableId] }.let { result: List<Int?> ->
                assertTrue(result.size == 1)
                assertNull(result.single())
            }
        }
    }

    @Test fun testGroupBy07() {
        withCitiesAndUsers { cities, users, userData ->
            val avgIdExpr = cities.id.avg()
            val avgId = BigDecimal.valueOf(cities.selectAll().map { it[cities.id]}.average())

            cities.slice(avgIdExpr).selectAll()
                    .map { it[avgIdExpr] }.let { result ->
                assertTrue(result.size == 1)
                assertTrue(result.single()!!.compareTo(avgId) == 0)
            }

            cities.slice(avgIdExpr).select { cities.id.isNull() }
                    .map { it[avgIdExpr] }.let { result ->
                assertTrue(result.size == 1)
                assertNull(result.single())
            }
        }
    }

    @Test fun orderBy01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy (users.id).toList()
            assertEquals(5, r.size)
            assertEquals("alex", r[0][users.id])
            assertEquals("andrey", r[1][users.id])
            assertEquals("eugene", r[2][users.id])
            assertEquals("sergey", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    @Test fun orderBy02() {
        withCitiesAndUsers(exclude = listOf(TestDB.POSTGRESQL)) { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId, false).orderBy (users.id).toList()
            assertEquals(5, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals("sergey", r[1][users.id])
            assertEquals("andrey", r[2][users.id])
            assertEquals("alex", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    @Test fun orderBy03() {
        withCitiesAndUsers(exclude = listOf(TestDB.POSTGRESQL)) { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId to false, users.id to true).toList()
            assertEquals(5, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals("sergey", r[1][users.id])
            assertEquals("andrey", r[2][users.id])
            assertEquals("alex", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    @Test fun testOrderBy04() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, users.id.count()).selectAll(). groupBy(cities.name).orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals(2, r[0][users.id.count()])
            assertEquals("St. Petersburg", r[1][cities.name])
            assertEquals(1, r[1][users.id.count()])
        }
    }

    @Test fun testSizedIterable() {
        withCitiesAndUsers { cities, users, userData ->
            assertEquals(false, cities.selectAll().empty())
            assertEquals(true, cities.select { cities.name eq "Qwertt" }.empty())
            assertEquals(0, cities.select { cities.name eq "Qwertt" }.count())
            assertEquals(3, cities.selectAll().count())
        }
    }

    @Test fun testExists01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select{ exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%"))) }.toList()
            assertEquals(1, r.size)
            assertEquals("Something", r[0][users.name])
        }
    }

    @Test fun testExists02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select{ exists(userData.select((userData.user_id eq users.id) and ((userData.comment like "%here%") or (userData.comment like "%Sergey")))) }
                    .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }

    @Test fun testExists03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select{
                        exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%"))) or
                                exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%Sergey")))
            }
                    .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }

    @Test fun testInList01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select { users.id inList listOf("andrey","alex") }.orderBy(users.name).toList()

            assertEquals(2, r.size)
            assertEquals("Alex", r[0][users.name])
            assertEquals("Andrey", r[1][users.name])
        }
    }

    @Test fun testInList02() {
        withCitiesAndUsers { cities, users, userData ->
            val cityIds = cities.selectAll().map { it[cities.id] }.take(2)
            val r = cities.select { cities.id inList cityIds }

            assertEquals(2, r.count())
        }
    }

    @Test fun testCalc01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = cities.slice(cities.id.sum()).selectAll().toList()
            assertEquals(1, r.size)
            assertEquals(6, r[0][cities.id.sum()])
        }
    }

    @Test fun testCalc02() {
        withCitiesAndUsers { cities, users, userData ->
            val sum = Expression.build {
                Sum(cities.id + userData.value, IntegerColumnType())
            }
            val r = (users innerJoin userData innerJoin cities).slice(users.id, sum)
                    .selectAll().groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(22, r[0][sum])
            assertEquals("sergey", r[1][users.id])
            assertEquals(32, r[1][sum])
        }
    }

    @Test fun testCalc03() {
        withCitiesAndUsers { cities, users, userData ->
            val sum = Expression.build { Sum(cities.id * 100 + userData.value / 10, IntegerColumnType()) }
            val r = (users innerJoin userData innerJoin cities).slice(users.id, sum)
                    .selectAll().groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(202, r[0][sum])
            assertEquals("sergey", r[1][users.id])
            assertEquals(203, r[1][sum])
        }
    }

    @Test fun testSubstring01() {
        withCitiesAndUsers { cities, users, userData ->
            val substring = users.name.substring(1, 2)
            val r = (users).slice(users.id, substring)
                    .selectAll().orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals("Al", r[0][substring])
            assertEquals("An", r[1][substring])
            assertEquals("Eu", r[2][substring])
            assertEquals("Se", r[3][substring])
            assertEquals("So", r[4][substring])
        }
    }

    @Test fun testInsertSelect01() {
        withCitiesAndUsers { cities, users, userData ->
            val substring = users.name.substring(1, 2)
            cities.insert(users.slice(substring).selectAll().orderBy(users.id).limit(2))

            val r = cities.slice(cities.name).selectAll().orderBy(cities.id, false).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("An", r[0][cities.name])
            assertEquals("Al", r[1][cities.name])
        }
    }

    @Test fun testInsertSelect02() {
        withCitiesAndUsers { cities, users, userData ->
            userData.insert(userData.slice(userData.user_id, userData.comment, intParam(42)).selectAll())

            val r = userData.select {userData.value eq 42}.orderBy(userData.user_id).toList()
            assertEquals(3, r.size)
        }
    }

    @Test fun testSelectCase01() {
        withCitiesAndUsers { cities, users, userData ->
            val field = Expression.build { case().When(users.id eq "alex", stringLiteral("11")).Else (stringLiteral("22"))}
            val r = users.slice(users.id, field).selectAll().orderBy(users.id).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("11", r[0][field])
            assertEquals("alex", r[0][users.id])
            assertEquals("22", r[1][field])
            assertEquals("andrey", r[1][users.id])
        }
    }

    private fun DMLTestsData.Misc.checkRow(row: ResultRow, n: Int, nn: Int?, d: DateTime, dn: DateTime?, t: DateTime, tn: DateTime?, e: DMLTestsData.E, en: DMLTestsData.E?, s: String, sn: String?, dc: BigDecimal, dcn: BigDecimal?) {
        assertEquals(row[this.n], n)
        assertEquals(row[this.nn], nn)
        assertEqualDateTime(row[this.d], d)
        assertEqualDateTime(row[this.dn], dn)
        assertEqualDateTime(row[this.t], t)
        assertEqualDateTime(row[this.tn], tn)
        assertEquals(row[this.e], e)
        assertEquals(row[this.en], en)
        assertEquals(row[this.s], s)
        assertEquals(row[this.sn], sn)
        assertEquals(row[this.dc], dc)
        assertEquals(row[this.dcn], dcn)
    }

    @Test fun testInsert01() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now();

        withTables(tbl) {
            tbl.insert {
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = DMLTestsData.E.ONE
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, null, date, null, time, null, DMLTestsData.E.ONE, null, "test", null, BigDecimal("239.42"), null)
        }
    }

    @Test fun testInsert02() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now();

        withTables(tbl) {
            tbl.insert {
                it[n] = 42
                it[nn] = null
                it[d] = date
                it[dn] = null
                it[t] = time
                it[tn] = null
                it[e] = DMLTestsData.E.ONE
                it[en] = null
                it[s] = "test"
                it[sn] = null
                it[dc] = BigDecimal("239.42")
                it[dcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, null, date, null, time, null, DMLTestsData.E.ONE, null, "test", null, BigDecimal("239.42"), null)
        }
    }
    @Test fun testInsert03() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now();

        withTables(tbl) {
            tbl.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = DMLTestsData.E.ONE
                it[en] = DMLTestsData.E.ONE
                it[s] = "test"
                it[sn] = "test"
                it[dc] = BigDecimal("239.42")
                it[dcn] = BigDecimal("239.42")
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, 42, date, date, time, time, DMLTestsData.E.ONE, DMLTestsData.E.ONE, "test", "test", BigDecimal("239.42"), BigDecimal("239.42"))
        }
    }

    @Test fun testInsert04() {
        val stringThatNeedsEscaping = "A'braham Barakhyahu"
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now();
        withTables(tbl) {
            tbl.insert {
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = DMLTestsData.E.ONE
                it[s] = stringThatNeedsEscaping
                it[dc] = BigDecimal("239.42")
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, null, date, null, time, null, DMLTestsData.E.ONE, null, stringThatNeedsEscaping, null, BigDecimal("239.42"), null)
        }
    }

    @Test fun testGeneratedKey01() {
        withTables(DMLTestsData.Cities){
            val id = DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "FooCity"
            } get DMLTestsData.Cities.id
            assertEquals(DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.id], id)
        }
    }

    @Test fun testGeneratedKey02() {
        val LongIdTable = object : Table() {
            val id = long("id").autoIncrement().primaryKey()
            val name = text("name")
        }
        withTables(LongIdTable){
            val id = LongIdTable.insert {
                it[LongIdTable.name] = "Foo"
            } get LongIdTable.id
            assertEquals(LongIdTable.selectAll().last()[LongIdTable.id], id)
        }
    }

    @Test fun testGeneratedKey03() {
        val IntIdTable = object : IntIdTable() {
            val name = text("name")
        }
        withTables(IntIdTable){
            val id = IntIdTable.insertAndGetId {
                it[IntIdTable.name] = "Foo"
            }
            assertEquals(IntIdTable.selectAll().last()[IntIdTable.id], id)
        }
    }

/*
    Test fun testInsert05() {
        val stringThatNeedsEscaping = "multi\r\nline"
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[d] = today
                it[e] = DMLTestsData.E.ONE
                it[s] = stringThatNeedsEscaping
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today, null, DMLTestsData.E.ONE, null, stringThatNeedsEscaping, null)
        }
    }
*/

    @Test fun testSelectDistinct() {
        val tbl = DMLTestsData.Cities
        withTables(tbl) {
            tbl.insert { it[tbl.name] = "test" }
            tbl.insert { it[tbl.name] = "test" }

            assertEquals(2, tbl.selectAll().count())
            assertEquals(2, tbl.selectAll().withDistinct().count())
            assertEquals(1, tbl.slice(tbl.name).selectAll().withDistinct().count())
            assertEquals("test", tbl.slice(tbl.name).selectAll().withDistinct().single()[tbl.name])
        }
    }

    @Test fun testSelect01() {
        val tbl = DMLTestsData.Misc
        withTables(tbl) {
            val date = today
            val time = DateTime.now()
            val sTest = "test"
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = DMLTestsData.E.ONE
                it[s] = sTest
                it[dc] = dec
            }

            tbl.checkRow(tbl.select{tbl.n.eq(42)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.nn.isNull()}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.nn.eq(null as Int?)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)

            tbl.checkRow(tbl.select{tbl.d.eq(date)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.dn.isNull()}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.dn.eq(null as DateTime?)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)

            tbl.checkRow(tbl.select{tbl.t.eq(time)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.tn.isNull()}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.tn.eq(null as DateTime?)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)

            tbl.checkRow(tbl.select{tbl.e.eq(DMLTestsData.E.ONE)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.en.isNull()}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.en.eq(null as DMLTestsData.E?)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)

            tbl.checkRow(tbl.select{tbl.s.eq(sTest)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.sn.isNull()}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
            tbl.checkRow(tbl.select{tbl.sn.eq(null as String?)}.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, sTest, null, dec, null)
        }
    }

    @Test fun testSelect02() {
        val tbl = DMLTestsData.Misc
        withTables(tbl) {
            val date = today
            val time = DateTime.now()
            val sTest = "test"
            val eOne = DMLTestsData.E.ONE
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = eOne
                it[en] = eOne
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec

            }

            tbl.checkRow(tbl.select{tbl.nn.eq(42)}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)
            tbl.checkRow(tbl.select{tbl.nn neq null }.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)

            tbl.checkRow(tbl.select{tbl.dn.eq(date)}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)
            tbl.checkRow(tbl.select{tbl.dn.isNotNull()}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)

            tbl.checkRow(tbl.select{tbl.t.eq(time)}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)
            tbl.checkRow(tbl.select{tbl.tn.isNotNull()}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)

            tbl.checkRow(tbl.select{tbl.en.eq(eOne)}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)
            tbl.checkRow(tbl.select{tbl.en.isNotNull()}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)

            tbl.checkRow(tbl.select{tbl.sn.eq(sTest)}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)
            tbl.checkRow(tbl.select{tbl.sn.isNotNull()}.single(), 42, 42, date, date, time, time, eOne, eOne, sTest, sTest, dec, dec)
        }
    }

    @Test fun testUpdate02() {
        val tbl = DMLTestsData.Misc
        withTables(tbl) {
            val date = today
            val time = DateTime.now()
            val eOne = DMLTestsData.E.ONE
            val sTest = "test"
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = eOne
                it[en] = eOne
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec
            }

            tbl.update({tbl.n.eq(42)}) {
                it[nn] = null
                it[dn] = null
                it[tn] = null
                it[en] = null
                it[sn] = null
                it[dcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, null, date, null, time, null, eOne, null, sTest, null, dec, null)
        }
    }

    @Test fun testUpdate03() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now()
        val eOne = DMLTestsData.E.ONE
        val dec = BigDecimal("239.42")
        withTables(excludeSettings = listOf(TestDB.MYSQL), tables = tbl) {
            tbl.insert {
                it[n] = 101
                it[s] = "123456789"
                it[sn] = "123456789"
                it[d] = date
                it[t] = time
                it[e] = eOne
                it[dc] = dec
            }

            tbl.update({tbl.n.eq(101)}) {
                it.update(s, tbl.s.substring(2, 255))
                it.update(sn, tbl.s.substring(3, 255))
            }

            val row = tbl.select { tbl.n eq 101 }.single()
            tbl.checkRow(row, 101, null, date, null, time, null, eOne, null, "23456789", "3456789", dec, null)
        }
    }

    @Test fun testJoinWithAlias01() {
        withCitiesAndUsers {  cities, users, userData ->
            val usersAlias = users.alias("u2")
            val resultRow = Join(users).join(usersAlias, JoinType.LEFT, usersAlias[users.id], stringLiteral("smth"))
                    .select { users.id eq "alex" }.single()

            assert(resultRow[users.name] == "Alex")
            assert(resultRow[usersAlias[users.name]] == "Something")
        }
    }

    @Test fun testStringFunctions() {
        withCitiesAndUsers { cities, users, userData ->

            val lcase = DMLTestsData.Cities.name.lowerCase()
            assert(cities.slice(lcase).selectAll().any{ it[lcase] == "prague"})

            val ucase = DMLTestsData.Cities.name.upperCase()
            assert(cities.slice(ucase).selectAll().any{ it[ucase] == "PRAGUE"})
        }
    }

    @Test fun testJoinSubQuery01() {
        withCitiesAndUsers { cities, users, userData ->
            val expAlias = users.name.max().alias("m")
            val usersAlias = users.slice(users.cityId, expAlias).selectAll().groupBy(users.cityId).alias("u2")
            val resultRows = Join(users).join(usersAlias, JoinType.INNER, usersAlias[expAlias], users.name).selectAll().toList()
            assertEquals(3, resultRows.size)
        }
    }

    @Test fun testJoinSubQuery02() {
        withCitiesAndUsers { cities, users, userData ->
            val expAlias = users.name.max().alias("m")

            val query = Join(users).joinQuery(on = { it[expAlias].eq(users.name) }) {
                users.slice(users.cityId, expAlias).selectAll().groupBy(users.cityId)
            }
            val innerExp = query.lastQueryAlias!![expAlias]

            assertEquals("q0", query.lastQueryAlias?.alias)
            assertEquals(3, query.selectAll().count())
            assertNotNull(query.slice(users.columns + innerExp).selectAll().first().get(innerExp))
        }
    }

    @Test fun testDefaultExpressions01() {

        fun abs(value: Int) = object : ExpressionWithColumnType<Int>() {
            override fun toSQL(queryBuilder: QueryBuilder): String {
                return "ABS($value)"
            }

            override val columnType: ColumnType = IntegerColumnType()
        }

        val foo = object : IntIdTable() {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime())
            val defaultInt = integer("defaultInteger").defaultExpression(abs(-100))
        }

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
            }
            val result = foo.select { foo.id eq id!! }.single()

            assertEquals(today, result[foo.defaultDateTime].withTimeAtStartOfDay())
            assertEquals(100, result[foo.defaultInt])
        }
    }

}


interface Foo<out T> {}

open class F<out T> : Foo<T> {
}


interface Foo2<out T> : Foo<T> {
}

fun<T> Foo2<T>.test() : String {
    return "test"
}

class FooImpl<T> : F<T>(), Foo2<T> {}

class FooTests {
    @Test fun test01() {
        var foo = FooImpl<Int>()
        assertEquals("test", foo.test())
    }
}

val today: DateTime = DateTime.now().withTimeAtStartOfDay()

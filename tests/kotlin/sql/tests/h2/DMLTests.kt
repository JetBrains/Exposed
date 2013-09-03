package kotlin.sql.tests.h2

import kotlin.sql.*
import org.junit.Test
import kotlin.test.assertEquals
import org.joda.time.DateTime

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
    }

    enum class E {
        ONE
        TWO
        THREE
    }

    object Misc : Table() {
        val n = integer("n")
        val nn = integer("nn").nullable()

        val d = date("d")
        val dn = date("dn").nullable()

        val e = enumeration("e", javaClass<E>())
        val en = enumeration("en", javaClass<E>()).nullable()

        val s = varchar("s", 100)
        val sn = varchar("sn", 100).nullable()
    }
}

class DMLTests : DatabaseTestsBase() {
    fun withCitiesAndUsers(statement: Session.(cities: DMLTestsData.Cities, users: DMLTestsData.Users, userData: DMLTestsData.UserData) -> Unit) {
        val Users = DMLTestsData.Users;
        val Cities = DMLTestsData.Cities;
        val UserData = DMLTestsData.UserData;

        withTables(Cities, Users, UserData) {
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
            }

            UserData.insert {
                it[user_id] = "eugene"
                it[comment] = "Comment for Eugene"
            }

            UserData.insert {
                it[user_id] = "sergey"
                it[comment] = "Comment for Sergey"
            }
            statement (Cities, Users, UserData)
        }
    }

    Test fun testUpdate01() {
        withCitiesAndUsers { cities, users, userData ->
            val alexId = "alex"
            val alexName = users.slice(users.name).select (users.id.eq(alexId)).first()[users.name]
            assertEquals("Alex", alexName);

            val newName = "Alexey"
            users.update(users.id.eq(alexId)) {
                it[users.name] = newName
            }

            val alexNewName = users.slice(users.name).select (users.id.eq(alexId)).first()[users.name]
            assertEquals(newName, alexNewName);
        }
    }

    Test fun testDelete01() {
        withCitiesAndUsers { cities, users, userData ->
            delete(userData).all()
            val userDataExists = userData.selectAll().any()
            assertEquals(false, userDataExists)

            val smthId = users.slice(users.id).select(users.name.like("%thing")).single()[users.id]
            assertEquals ("smth", smthId)

            delete (users) where users.name.like("%thing")
            val hasSmth = users.slice(users.id).select(users.name.like("%thing")).any()
            assertEquals(false, hasSmth)
        }
    }

    // manual join
    Test fun testJoin01() {
        withCitiesAndUsers { cities, users, userData ->
            (users join cities).slice(users.name, cities.name).
            select((users.id.eq("andrey") or users.name.eq("Sergey")) and users.cityId.eq(cities.id)) forEach {
                val userName = it[users.name]
                val cityName = it[cities.name]
                when (userName) {
                    "Andrey" -> assertEquals("St. Petersburg", cityName)
                    "Sergey" -> assertEquals("Munich", cityName)
                    else -> throw RuntimeException ("Unexpected user $userName")
                }
            }
        }
    }

    // join with foreign key
    Test fun testJoin02() {
        withCitiesAndUsers { cities, users, userData ->
            val stPetersburgUser = (users innerJoin cities).slice(users.name, users.cityId, cities.name).
            select(cities.name.eq("St. Petersburg") or users.cityId.isNull()).single()
            assertEquals("Andrey", stPetersburgUser[users.name])
            assertEquals("St. Petersburg", stPetersburgUser[cities.name])
        }
    }

    // triple join
    Test fun testJoin03() {
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
    Test fun testJoin04() {
        object Numbers : Table() {
            val id = integer("id").primaryKey()
        }

        object Names : Table() {
            val name = varchar("name", 10).primaryKey()
        }

        object Map: Table () {
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

    Test fun testGroupBy01() {
        withCitiesAndUsers { cities, users, userData ->
            (cities join users).slice(cities.name, count(users.id)).selectAll() groupBy cities.name forEach {
                val cityName = it[cities.name]
                val userCount = it[count(users.id)]

                when (cityName) {
                    "Munich" -> assertEquals(2, userCount)
                    "Prague" -> assertEquals(0, userCount)
                    "St. Petersburg" -> assertEquals(1, userCount)
                    else -> throw RuntimeException ("Unknow city $cityName")
                }
            }
        }
    }

    Test fun orderBy01() {
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

    Test fun orderBy02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId, false).orderBy (users.id).toList()
            assertEquals(5, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals("sergey", r[1][users.id])
            assertEquals("andrey", r[2][users.id])
            assertEquals("alex", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    Test fun orderBy03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId to false, users.id to true).toList()
            assertEquals(5, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals("sergey", r[1][users.id])
            assertEquals("andrey", r[2][users.id])
            assertEquals("alex", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    Test fun testOrderBy04() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, count(users.id)).selectAll(). groupBy(cities.name).orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals(2 as Long, r[0][count(users.id)])
            assertEquals("St. Petersburg", r[1][cities.name])
            assertEquals(1 as Long, r[1][count(users.id)])
        }
    }

    private fun DMLTestsData.Misc.checkRow(row: ResultRow, n: Int, nn: Int?, d: DateTime, dn: DateTime?, e: DMLTestsData.E, en: DMLTestsData.E?, s: String, sn: String?) {
        assertEquals(row[this.n], n)
        assertEquals(row[this.nn], nn)
        assertEquals(row[this.d], d)
        assertEquals(row[this.dn], dn)
        assertEquals(row[this.e], e)
        assertEquals(row[this.en], en)
        assertEquals(row[this.s], s)
        assertEquals(row[this.sn], sn)
    }

    Test fun testInsert01() {
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[d] = today()
                it[e] = DMLTestsData.E.ONE
                it[s] = "test"
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today(), null, DMLTestsData.E.ONE, null, "test", null)
        }
    }

    Test fun testInsert02() {
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[nn] = null
                it[d] = today()
                it[dn] = null
                it[e] = DMLTestsData.E.ONE
                it[en] = null
                it[s] = "test"
                it[sn] = null
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today(), null, DMLTestsData.E.ONE, null, "test", null)
        }
    }
    Test fun testInsert03() {
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = today()
                it[dn] = today()
                it[e] = DMLTestsData.E.ONE
                it[en] = DMLTestsData.E.ONE
                it[s] = "test"
                it[sn] = "test"
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, 42, today(), today(), DMLTestsData.E.ONE, DMLTestsData.E.ONE, "test", "test")
        }
    }

    Test fun testSelect01() {
        val t = DMLTestsData.Misc
        withTables(t) {
            val date = today()
            val sTest = "test"
            t.insert {
                it[n] = 42
                it[d] = date
                it[e] = DMLTestsData.E.ONE
                it[s] = sTest
            }

            t.checkRow(t.select(t.n.eq(42)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
            t.checkRow(t.select(t.nn.isNull()).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
            t.checkRow(t.select(t.nn.eq(null)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)

            t.checkRow(t.select(t.d.eq(date)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
            t.checkRow(t.select(t.dn.isNull()).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
            t.checkRow(t.select(t.dn.eq(null)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)

            t.checkRow(t.select(t.e.eq(DMLTestsData.E.ONE)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
            t.checkRow(t.select(t.en.isNull()).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
            t.checkRow(t.select(t.en.eq(null)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)

            t.checkRow(t.select(t.s.eq(sTest)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
            t.checkRow(t.select(t.sn.isNull()).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
            t.checkRow(t.select(t.sn.eq(null)).single(), 42, null, date, null, DMLTestsData.E.ONE, null, sTest, null)
        }
    }

    Test fun testSelect02() {
        val t = DMLTestsData.Misc
        withTables(t) {
            val date = today()
            val sTest = "test"
            val eOne = DMLTestsData.E.ONE
            t.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[e] = eOne
                it[en] = eOne
                it[s] = sTest
                it[sn] = sTest
            }

            t.checkRow(t.select(t.nn.eq(42)).single(), 42, 42, date, date, eOne, eOne, sTest, sTest)
            t.checkRow(t.select(t.nn.isNotNull()).single(), 42, 42, date, date, eOne, eOne, sTest, sTest)

            t.checkRow(t.select(t.dn.eq(date)).single(), 42, 42, date, date, eOne, eOne, sTest, sTest)
            t.checkRow(t.select(t.dn.isNotNull()).single(), 42, 42, date, date, eOne, eOne, sTest, sTest)

            t.checkRow(t.select(t.en.eq(eOne)).single(), 42, 42, date, date, eOne, eOne, sTest, sTest)
            t.checkRow(t.select(t.en.isNotNull()).single(), 42, 42, date, date, eOne, eOne, sTest, sTest)

            t.checkRow(t.select(t.sn.eq(sTest)).single(), 42, 42, date, date, eOne, eOne, sTest, sTest)
            t.checkRow(t.select(t.sn.isNotNull()).single(), 42, 42, date, date, eOne, eOne, sTest, sTest)
        }
    }

    Test fun testUpdate02() {
        val t = DMLTestsData.Misc
        withTables(t) {
            val date = today()
            val eOne = DMLTestsData.E.ONE
            val sTest = "test"
            t.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[e] = eOne
                it[en] = eOne
                it[s] = sTest
                it[sn] = sTest
            }

            t.update(t.n.eq(42)) {
                it[nn] = null
                it[dn] = null
                it[en] = null
                it[sn] = null
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, date, null, eOne, null, sTest, null)
        }
    }
}

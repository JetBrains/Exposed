package kotlin.sql.tests.h2

import kotlin.sql.*;
import org.junit.Test
import kotlin.test.assertEquals

object DMLTestsData {
    object Cities : Table() {
        val id = integer("id").autoIncrement().primaryKey() // PKColumn<Int>
        val name = varchar("name", 50) // Column<String>
    }

    object Users : Table() {
        val id = varchar("id", 10).primaryKey() // PKColumn<String>
        val name = varchar("name", length = 50) // Column<String>
        val cityId = integer("city_id").nullable() references Cities.id // Column<Int?>
    }
}

class DMLTests : DatabaseTestsBase() {
    fun withCitiesAndUsers(statement: Session.(cities : DMLTestsData.Cities, users : DMLTestsData.Users) -> Unit) {
        val Users =    DMLTestsData.Users;
        val Cities =    DMLTestsData.Cities;

        withTables(Cities, Users) {
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

            statement (Cities, Users)
        }
    }

    Test fun testUpdate01() {
        withCitiesAndUsers { cities, users ->
            val alexId = "alex"
            val alexName = users.slice(users.name).select (users.id.equals(alexId)).first()[users.name]
            assertEquals("Alex", alexName);

            val newName = "Alexey"
            users.update(users.id.equals(alexId)) {
                it[users.name] = newName
            }

            val alexNewName = users.slice(users.name).select (users.id.equals(alexId)).first()[users.name]
            assertEquals(newName, alexNewName);
        }
    }

    Test fun testDelete01() {
        withCitiesAndUsers { cities, users ->
            val smthId = users.slice(users.id).select(users.name.like("%thing")).single()[users.id]
            assertEquals ("smth", smthId)

            delete (users) where users.name.like("%thing")
            val hasSmth = users.slice(users.id).select(users.name.like("%thing")).any()
            assertEquals(false, hasSmth)
        }
    }

    // manual join
    Test fun testJoin01() {
        withCitiesAndUsers { cities, users ->
            (users join cities).slice(users.name, cities.name).
            select((users.id.equals("andrey") or users.name.equals("Sergey")) and users.cityId.equals(cities.id)) forEach {
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
        withCitiesAndUsers { cities, users ->
            val stPetersburgUser = (users innerJoin cities).slice(users.name, users.cityId, cities.name).
            select(cities.name.equals("St. Petersburg") or users.cityId.isNull()).single()
            assertEquals("Andrey", stPetersburgUser[users.name])
            assertEquals("St. Petersburg", stPetersburgUser[cities.name])
            }
        }

    Test fun testGroupBy01() {
        withCitiesAndUsers { cities, users ->
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
}
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

    fun<T> Iterable<T>.single() : T {
        var answer: T? = null;
        var found: Boolean = false;
        for (t in this) {
          if (found) throw RuntimeException ("Duplicate items")

          answer = t;
          found = true;
        }

        if (!found) throw RuntimeException ("No items found")
        return answer!!;
    }

    fun<T> Iterable<T>.any() : Boolean {
        for (t in this) {
            return true
        }
        return false
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
}
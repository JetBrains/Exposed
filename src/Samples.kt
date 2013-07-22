package demo

import kotlin.sql.*

object Users : Table() {
    val id = id("id", autoIncrement = true)
    val name = varchar("name", length = 50)
    val cityId = integerNullable("city_id", references = Cities.id)
}

object Cities : Table() {
    val id = id("id")
    val name = varchar("name", 50)

    val all = id + name
}

fun main(args: Array<String>) {
    var db = Database("jdbc:h2:mem:test", driver = "org.h2.Driver")
    // var db = Database("jdbc:mysql://localhost/test", driver = "com.mysql.jdbc.Driver", user = "root")

    db.withSession {
        create (Cities, Users)

        insert (Cities.id(1), Cities.name("St. Petersburg"))
        insert (Cities.id(2), Cities.name to "Munich")
        insert (Cities.id(3), Cities.name to "Prague")

        insert (Users.name("Andrey"), Users.cityId(1))

        insert (Users.name("Sergey"), Users.cityId(2))
        insert (Users.name("Eugene"), Users.cityId(2))
        insert (Users.name("Alex"))
        insert (Users.name("Something"))

        update (Users.name("Alexey")) where Users.name.equals("Alex")

        delete(Users) where Users.name.equals("Something")

        println("All cities:")

        select (Cities.all) forEach {
            val (id, name) = it
            println("$id: $name")
        }

        println("Manual join:")

        select (Users.name, Cities.name) where (Users.id.equals(1) or Users.name.equals("Sergey")) and
                Users.id.equals(2) and Users.cityId.equals(Cities.id) forEach {
            val (userName, cityName) = it
            println("$userName lives in $cityName")
        }

        println("Join with foreign key:")

        select (Users.name, Users.cityId, Cities.name) from Users join Cities where
                Cities.name.equals("St. Petersburg") or Users.cityId.isNull() forEach {
            val (userName, cityId, cityName) = it
            if (cityId != null) {
                println("$userName lives in $cityName")
            } else {
                println("$userName lives nowhere")
            }
        }

        println("Functions and group by:")

        select (Cities.name, count(Users.id)) from Cities join Users groupBy Cities.name forEach {
            val (cityName, userCount) = it
            if (userCount > 0) {
                println("$userCount user(s) live(s) in $cityName")
            } else {
                println("Nobody lives in $cityName")
            }
        }

        drop (Users, Cities)
    }
}
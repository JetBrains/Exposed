package demo

import kotlin.sql.*

object Users : Table() {
    val id = varchar("id", 10).primaryKey() // PKColumn<String>
    val name = varchar("name", length = 50) // Column<String>
    val cityId = integer("city_id").nullable() references Cities.id // Column<Int?>
}

object Cities : Table() {
    val id = integer("id").autoIncrement().primaryKey() // PKColumn<Int>
    val name = varchar("name", 50) // Column<String>
}

fun main(args: Array<String>) {
    var db = Database("jdbc:h2:mem:test", driver = "org.h2.Driver")
    // var db = Database("jdbc:mysql://localhost/test", driver = "com.mysql.jdbc.Driver", user = "root")

    db.withSession {
        create (Cities, Users)

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

        Users.update(Users.id.equals("alex")) {
            it[name] = "Alexey"
        }

        delete (Users) where Users.name.like("%thing")

        println("All cities:")

        for (city in Cities.selectAll()) {
            println("${city[Cities.id]}: ${city[Cities.name]}")
        }

        println("Manual join:")
        (Users join Cities).slice(Users.name, Cities.name).
            select((Users.id.equals("andrey") or Users.name.equals("Sergey")) and
                    Users.id.equals("sergey") and Users.cityId.equals(Cities.id)) forEach {
            println("${it[Users.name]} lives in ${it[Cities.name]}")
        }

        println("Join with foreign key:")


        (Users join Cities).slice(Users.name, Users.cityId, Cities.name).
                select(Cities.name.equals("St. Petersburg") or Users.cityId.isNull()) forEach {
            if (it[Users.cityId] != null) {
                println("${it[Users.name]} lives in ${it[Cities.name]}")
            }
            else {
                println("${it[Users.name]} lives nowhere")
            }
        }

    }

    db.withSession {
        println("Functions and group by:")

        (Cities join Users).slice(Cities.name, count(Users.id)).selectAll() groupBy Cities.name forEach {
            val cityName = it[Cities.name]
            val userCount = it[count(Users.id)]

            if (userCount > 0) {
                println("$userCount user(s) live(s) in $cityName")
            } else {
                println("Nobody lives in $cityName")
            }
        }

        drop (Users, Cities)
    }

    db.shutDown()
}

package demo

import kotlin.sql.*

object Users : Table() {
    val id = varchar("id", 10).primaryKey() // PKColumn<String>
    val name = varchar("name", length = 50) // Column<String>
    val cityId = integer("city_id").nullable() references Cities.id // Column<Int?>

    val all = id + name + cityId // Column3<String, String, Int?>
    val values = id + name + cityId // The columns required for insert statement
}

object Cities : Table() {
    val id = integer("id").autoIncrement().primaryKey() // PKColumn<Int>
    val name = varchar("name", 50) // Column<String>

    val all = id + name // Column2<Int, String>
    val values = name // The columns required for insert statement
}

fun main(args: Array<String>) {
    var db = Database("jdbc:h2:mem:test", driver = "org.h2.Driver")
    // var db = Database("jdbc:mysql://localhost/test", driver = "com.mysql.jdbc.Driver", user = "root")

    db.withSession {
        create (Cities, Users)

        val saintPetersburgId = insert (Cities.values("St. Petersburg")) get Cities.id
        val munichId = insert (Cities.values("Munich")) get Cities.id
        insert (Cities.values("Prague"))

        insert (Users.values("andrey", "Andrey", saintPetersburgId))

        insert (Users.values("sergey", "Sergey", munichId))
        insert (Users.values("eugene", "Eugene", munichId))
        insert (Users.values("alex", "Alex", null))
        insert (Users.values("smth", "Something", null))

        update (Users) {
            set(name("Alexey"))
        } where Users.id.equals("alex")

        delete (Users) where Users.name.like("%thing")

        println("All cities:")

        select (Cities.all) forEach {
            val (id, name) = it
            println("$id: $name")
        }

        println("Manual join:")

        select (Users.name, Cities.name) where (Users.id.equals("andrey") or Users.name.equals("Sergey")) and
                Users.id.equals("sergey") and Users.cityId.equals(Cities.id) forEach {
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

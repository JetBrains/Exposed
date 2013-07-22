Kotlin SQL Library
==================

_Exposed_ is a prototype for a lightweight SQL library written over JDBC driver for [Kotlin](https://github.com/JetBrains/kotlin) language.

```java
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
```

Outputs:

    SQL: CREATE TABLE Cities (id INT PRIMARY KEY NOT NULL, name VARCHAR(50) NOT NULL)
    SQL: CREATE TABLE Users (id INT PRIMARY KEY AUTO_INCREMENT NOT NULL, name VARCHAR(50) NOT NULL, city_id INT NULL)
    SQL: ALTER TABLE Users ADD CONSTRAINT "fk_Users_Cities_city_id" FOREIGN KEY (city_id) REFERENCES Cities(id)
    SQL: INSERT INTO Cities (id, name) VALUES (1, 'St. Petersburg')
    SQL: INSERT INTO Cities (id, name) VALUES (2, 'Munich')
    SQL: INSERT INTO Cities (id, name) VALUES (3, 'Prague')
    SQL: INSERT INTO Users (name, city_id) VALUES ('Andrey', 1)
    SQL: INSERT INTO Users (name, city_id) VALUES ('Sergey', 2)
    SQL: INSERT INTO Users (name, city_id) VALUES ('Eugene', 2)
    SQL: INSERT INTO Users (name) VALUES ('Alex')
    SQL: INSERT INTO Users (name) VALUES ('Something')
    SQL: UPDATE Users SET name = 'Alexey' WHERE Users.name = 'Alex'
    SQL: DELETE FROM Users WHERE Users.name = 'Something'
    All cities:
    SQL: SELECT Cities.id, Cities.name FROM Cities
    1: St. Petersburg
    2: Munich
    3: Prague
    Manual join:
    SQL: SELECT Users.name, Cities.name FROM Cities, Users WHERE (Users.id = 1 or Users.name = 'Sergey') and Users.id = 2 and Users.city_id = Cities.id
    Sergey lives in Munich
    Join with foreign key:
    SQL: SELECT Users.name, Users.city_id, Cities.name FROM Users LEFT JOIN Cities ON Users.city_id = Cities.id WHERE Cities.name = 'St. Petersburg' or Users.city_id IS NULL
    Andrey lives in St. Petersburg
    Alexey lives nowhere
    Functions and group by:
    SQL: SELECT Cities.name, COUNT(Users.id) FROM Cities LEFT JOIN Users ON Users.city_id = Cities.id GROUP BY Cities.name
    Nobody lives in Prague
    1 user(s) live(s) in St. Petersburg
    2 user(s) live(s) in Munich
    SQL: DROP TABLE Users
    SQL: DROP TABLE Cities
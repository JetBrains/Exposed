Kotlin SQL Library
==================

_Exposed_ is a prototype for a lightweight SQL library written over JDBC driver for [Kotlin](https://github.com/JetBrains/kotlin) language.

```java
object Users : Table() {
    val id = primaryKey("id")
    val name = columnString("name")
    val cityId = columnNullableInt("city_id")

    val city = foreignKey(cityId, Cities)
}

object Cities : Table() {
    val id = primaryKey("id")
    val name = columnString("name")
}

fun main(args: Array<String>) {
    var db = Database("jdbc:h2:mem:test", driver = "org.h2.Driver")

    db.withSession {
        create(Cities)
        create(Users)

        insert(Cities.id(1), Cities.name("St. Petersburg"))
        insert(Cities.id(2), Cities.name to "Munich")

        insert(Users.id(1), Users.name("Andrey"), Users.cityId(1))
        insert(Users.id(2), Users.name("Sergey"), Users.cityId(2))
        insert(Users.id(3), Users.name("Alex"))

        println("All cities:")

        select (Cities.name) forEach {
            println(it[Cities.name])
        }

        println("Manual join:")
        select (Users.id, Users.name, Cities.name) where
                ((Users.id.equals(1) or Users.name.equals("Sergey")) and Users.id.equals(2) and
                    Users.cityId.equals(Cities.id)) forEach {
            println("${it[Users.name]} lives in ${it[Cities.name]}")
        }

        println("Join with foreign key:")
        select (Users.name, Cities.name) join (Users.city) where (Cities.name.equals("St. Petersburg") or (Users.cityId.isNull())) forEach {
            if (it.has(Users.city)) {
                println("${it[Users.name]} lives in ${it[Cities.name]}")
            } else {
                println("${it[Users.name]} lives nowhere")
            }
        }
    }
}
```

Outputs:

    SQL: CREATE TABLE Cities (id INT PRIMARY KEY NOT NULL, name VARCHAR(50) NOT NULL);
    SQL: CREATE TABLE Users (id INT PRIMARY KEY NOT NULL, name VARCHAR(50) NOT NULL, city_id INT NULL); ALTER TABLE Users ADD FOREIGN KEY (city_id) REFERENCES Users(id);
    SQL: INSERT INTO Cities (id, name) VALUES (1, 'St. Petersburg')
    SQL: INSERT INTO Cities (id, name) VALUES (2, 'Munich')
    SQL: INSERT INTO Users (id, name, city_id) VALUES (1, 'Andrey', 1)
    SQL: INSERT INTO Users (id, name, city_id) VALUES (2, 'Sergey', 2)
    SQL: INSERT INTO Users (id, name) VALUES (3, 'Alex')
    All cities:
    SQL: SELECT Cities.name FROM Cities
    St. Petersburg
    Munich
    Manual join:
    SQL: SELECT Users.id, Users.name, Cities.name FROM Users, Cities WHERE (Users.id = 1 or Users.name = 'Sergey') and Users.id = 2 and Users.city_id = Cities.id
    Sergey lives in Munich
    Join with foreign key:
    SQL: SELECT Users.name, Cities.name, Users.city_id FROM Users LEFT JOIN Cities ON Cities.id = Users.city_id WHERE Cities.name = 'St. Petersburg' or Users.city_id IS NULL
    Andrey lives in St. Petersburg
    Alex lives nowhere

Kotlin SQL Library
==================

This is an early prototype for a library to access SQL over JDBC, written for Kotlin language.

```java
object Users : Table() {
    val id = primaryKey("id")
    val name = columnString("name")
    val cityId = columnInt("city_id")

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
        insert(Cities.id to 2, Cities.name to "Munich")

        insert(Users.id(1), Users.name("Andrey"), Users.cityId(1))
        insert(Users.id(2), Users.name("Sergey"), Users.cityId(2))

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
        select (Users.name, Cities.name) join (Users.city) where (Cities.name.equals("St. Petersburg")) forEach {
            println("${it[Users.name]} lives in ${it[Cities.name]}")
        }
    }
}
```

Outputs:

    SQL: CREATE TABLE Cities (id INT PRIMARY KEY, name VARCHAR(50));
    SQL: CREATE TABLE Users (id INT PRIMARY KEY, name VARCHAR(50), city_id INT); ALTER TABLE Users ADD FOREIGN KEY (city_id) REFERENCES Users(id);
    SQL: INSERT INTO Cities (id, name) VALUES (1, 'St. Petersburg')
    SQL: INSERT INTO Cities (id, name) VALUES (2, 'Munich')
    SQL: INSERT INTO Users (id, name, city_id) VALUES (1, 'Andrey', 1)
    SQL: INSERT INTO Users (id, name, city_id) VALUES (2, 'Sergey', 2)
    All cities:
    SQL: SELECT Cities.name FROM Cities
    St. Petersburg
    Munich
    Manual join:
    SQL: SELECT Users.id, Users.name, Cities.name FROM Cities, Users WHERE (Users.id = 1 or Users.name = 'Sergey') and Users.id = 2 and Users.city_id = Cities.id
    Sergey lives in Munich
    Join with foreign key:
    SQL: SELECT Users.name, Cities.name FROM Users JOIN Cities ON Cities.id = Users.city_id WHERE Cities.name = 'St. Petersburg'
    Andrey lives in St. Petersburg


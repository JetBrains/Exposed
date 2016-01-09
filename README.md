Kotlin SQL Library
==================

_Exposed_ is a prototype for a lightweight SQL library written over JDBC driver for [Kotlin](https://github.com/JetBrains/kotlin) language.

```java
object Users : Table() {
    val id = varchar("id", ColumnType.PRIMARY_KEY, length = 10) // PKColumn<String>
    val name = varchar("name", length = 50) // Column<String>
    val cityId = integer("city_id", ColumnType.NULLABLE, references = Cities.id) // Column<Int?>

    val all = id + name + cityId // Column3<String, String, Int?>
    val values = id + name + cityId // The columns required for insert statement
}

object Cities : Table() {
    val id = integer("id", ColumnType.PRIMARY_KEY, autoIncrement = true) // PKColumn<Int>
    val name = varchar("name", 50) // Column<String>

    val all = id + name // Column2<Int, String>
    val values = name // The columns required for insert statement
}

fun main(args: Array<String>) {
    var db = Database("jdbc:h2:mem:test", driver = "org.h2.Driver")
    // var db = Database("jdbc:mysql://localhost/test", driver = "com.mysql.jdbc.Driver", user = "root")

    db.transaction {
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
```

Outputs:

    SQL: CREATE TABLE Cities (id INT PRIMARY KEY AUTO_INCREMENT NOT NULL, name VARCHAR(50) NOT NULL)
    SQL: CREATE TABLE Users (id VARCHAR(10) PRIMARY KEY NOT NULL, name VARCHAR(50) NOT NULL, city_id INT NULL)
    SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg')
    SQL: INSERT INTO Cities (name) VALUES ('Munich')
    SQL: INSERT INTO Cities (name) VALUES ('Prague')
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('andrey', 'Andrey', 1)
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('sergey', 'Sergey', 2)
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('eugene', 'Eugene', 2)
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('alex', 'Alex', null)
    SQL: INSERT INTO Users (id, name, city_id) VALUES ('smth', 'Something', null)
    SQL: UPDATE Users SET name = 'Alexey' WHERE Users.id = 'alex'
    SQL: DELETE FROM Users WHERE Users.name LIKE '%thing'
    All cities:
    SQL: SELECT Cities.id, Cities.name FROM Cities
    1: St. Petersburg
    2: Munich
    3: Prague
    Manual join:
    SQL: SELECT Users.name, Cities.name FROM Cities, Users WHERE (Users.id = 'andrey' or Users.name = 'Sergey') and Users.id = 'sergey' and Users.city_id = Cities.id
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

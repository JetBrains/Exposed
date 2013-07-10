package demo

object Users : Table() {
    // Boilerplate #1. We cannot guess the column type in runtime :-(
    val id = column<Int>("id", ColumnType.PRIMARY_KEY)
    val name = column<String>("name", ColumnType.STRING)
    val cityId = column<Int>("city_id", ColumnType.INT)

    val city = foreignKey(cityId, Cities)
}

object Cities : Table() {
    val id = column<String>("id", ColumnType.PRIMARY_KEY)
    val name = column<String>("name", ColumnType.STRING)
}


fun main(args: Array<String>) {
    var db = Database("jdbc:h2:mem:test", driver = "org.h2.Driver")

    db.withSession {
        it.create(Cities)
        it.create(Users)

        it.insert(Cities.id to 1, Cities.name to "St. Petersburg")
        it.insert(Cities.id to 2, Cities.name to "Munich")

        it.insert(Users.id to 1, Users.name to "Andrey", Users.cityId to 1)
        it.insert(Users.id to 2, Users.name to "Sergey", Users.cityId to 2)
        // Unsafe code #1. We cannot check if the value is of column's type
        // or all required columns are specified :-(

        println("All cities:")

        it.select (Cities.name) forEach {
            println(it[Cities.name])
        }

        println("Manual join:")
        it.select (Users.id, Users.name, Cities.name) where
                ((Users.id.equals(1) or Users.name.equals("Sergey")) and Users.id.equals(2) and
                    Users.cityId.equals(Cities.id)) forEach {
            // Boilerplate # 2. We cannot write Users.id == 1 || Users.name == "Andrey"
            // and we cannot use the precedence of operators :-(
            println("${it[Users.name]} lives in ${it[Cities.name]}") // Unsafe code #2. We cannot check if row has this column
        }

        println("Join with foreign key:")
        it.select (Users.name, Cities.name) join (Users.city) where (Cities.name.equals("St. Petersburg")) forEach {
            println("${it[Users.name]} lives in ${it[Cities.name]}")
        }
    }

    // Outputs:

    // SQL: CREATE TABLE Cities (id INT PRIMARY KEY, name VARCHAR(50));
    // SQL: CREATE TABLE Users (id INT PRIMARY KEY, name VARCHAR(50), city_id INT); ALTER TABLE Users ADD FOREIGN KEY (city_id) REFERENCES Users(id);
    // SQL: INSERT INTO Cities (id, name) VALUES (1, 'St. Petersburg')
    // SQL: INSERT INTO Cities (id, name) VALUES (2, 'Munich')
    // SQL: INSERT INTO Users (id, name, city_id) VALUES (1, 'Andrey', 1)
    // SQL: INSERT INTO Users (id, name, city_id) VALUES (2, 'Sergey', 2)
    // All cities:
    // SQL: SELECT Cities.name FROM Cities
    // St. Petersburg
    // Munich
    // Manual join:
    // SQL: SELECT Users.id, Users.name, Cities.name FROM Cities, Users WHERE (Users.id = 1 or Users.name = 'Sergey') and Users.id = 2 and Users.city_id = Cities.id
    // Sergey lives in Munich
    // Join with foreign key:
    // SQL: SELECT Users.name, Cities.name FROM Users JOIN Cities ON Cities.id = Users.city_id WHERE Cities.name = 'St. Petersburg'
    // Andrey lives in St. Petersburg
}
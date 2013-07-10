package demo

object Users : Table() {
    // Boilerplate #1. We cannot guess the column type in runtime :-(
    val id = column<Int>("id", ColumnType.INT)
    val name = column<String>("name", ColumnType.STRING)
    val cityId = column<Int>("city_id", ColumnType.INT)

    val city = foreignKey("city", cityId, Cities)
}

object Cities : Table() {
    // Boilerplate #1. We cannot guess the column type in runtime :-(
    val id = column<String>("id", ColumnType.INT)
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

        println("All cities: ")

        it.select (Cities.name) forEach {
            println(it[Cities.name])
        }

        println("Look up users: ")
        it.select (Users.id, Users.name, Cities.name) where
                ((Users.id.equals(1) or Users.name.equals("Sergey")) and Users.id.equals(2) and
                    Users.cityId.equals(Cities.id)) forEach {
            // Boilerplate # 2. We cannot write Users.id == 1 || Users.name == "Andrey"
            // and we cannot use the precedence of operators :-(
            println("${it[Users.name]} lives in ${it[Cities.name]}") // Unsafe code #2. We cannot check if row has this column
        }

        // Outputs:

        // SQL: CREATE TABLE Users (id INT, name VARCHAR(50))
        // SQL: INSERT INTO Users (id, name) VALUES (1, 'Andrey')
        // SQL: SELECT id, name FROM Users
        // Andrey's id is 1
    }
}
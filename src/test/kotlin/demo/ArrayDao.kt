package demo.dao

import demo.dao.ArrayEntity.Companion.find
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction


object Arrays: IntIdTable() {
    var values = array<String>("values", VarCharColumnType())
}

class ArrayEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ArrayEntity>(Arrays)

    var values by Arrays.values
}




fun main(args: Array<String>) {
    Database.connect("jdbc:postgresql://localhost:5432/core", user = "core", driver = "org.postgresql.Driver")

    transaction {
        logger.addLogger(StdOutSqlLogger)

        SchemaUtils.create(Arrays)

        val first = ArrayEntity.new {
            values = arrayOf("hoge", "fuga")
        }

        val entry = ArrayEntity.all().first()
        println("Arrays: $entry")
        entry.values.forEach (::println)

        val eqSelected = find {
            Arrays.values.eq(arrayOf("hoge", "fuga"))
        }
        eqSelected.forEach(::println)

        println("-----")

        val anySelected = find {
            Arrays.values.any("hoge")
        }
        println(anySelected.first().values[0])

        println("-----")
        val containsSelected = find {
            Arrays.values.contains(arrayOf("hoge"))
        }
        println(containsSelected.first().values[0])

    }
}

package org.example

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection

const val NAME_LIMIT = 50

object UsersTable : Table("Users") {
    val id = integer("id").autoIncrement()
    val firstName = varchar("name", NAME_LIMIT)
}

fun main() {
    val h2db = Databases().getH2DB()
    transaction(h2db) {
        // DSL/DAO operations go here
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(UsersTable)
        UsersTable.insert {
            it[firstName] = "James"
        }

        val jamesList = transaction(db) {
            UsersTable.selectAll().where { UsersTable.firstName eq "James" }.toList()
        }
        println(jamesList)
    }
}

fun openTransactionWithParams(db: Database) {
    transaction(Connection.TRANSACTION_SERIALIZABLE, true, db = db) {
        // DSL/DAO operations go here
        addLogger(StdOutSqlLogger)
    }
}

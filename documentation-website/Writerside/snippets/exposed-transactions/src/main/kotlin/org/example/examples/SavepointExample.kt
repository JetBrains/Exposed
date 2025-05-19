package org.example.examples

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/*
    Important: This file is referenced by line number in `Transactions.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/
@Suppress("MagicNumber")
class SavepointExample {
    object TestTable : Table("test_table") {
        val amount = integer("amount").uniqueIndex()
    }

    fun nestedTransaction() {
        transaction {
            println("Transaction # ${this.id}") // Transaction # 1
            TestTable.insert { it[amount] = 12 }
            println(TestTable.selectAll().count()) // 1

            transaction {
                println("Transaction # ${this.id}") // Transaction # 2
                TestTable.insert { it[amount] = 2 }
                println(TestTable.selectAll().count()) // 2

                rollback()
            }

            println(TestTable.selectAll().count()) // 1
        }
    }

    fun setSavepoint() {
        transaction {
            SchemaUtils.create(TestTable)
            TestTable.insert { it[amount] = 99 }
            val firstInsert = connection.setSavepoint("first_insert")
            TestTable.insert { it[amount] = 100 }
            connection.rollback(firstInsert)
        }

        transaction {
            // If the savepoint was not set in above tx, all inserts would roll back, returning an empty list
            println(TestTable.selectAll().map { it[TestTable.amount] }) // [99]
        }
    }
}

package org.jetbrains.exposed.v1.perf

import org.jetbrains.exposed.v1.core.Table

object Customers : Table("customers") {
    val id = integer("id")
    val name = varchar("cust_name", 64)
    val age = integer("age")
    val email = varchar("email", 128)
    override val primaryKey = PrimaryKey(id)
}

const val CREATE_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS customers (
        id INT PRIMARY KEY,
        cust_name VARCHAR(64) NOT NULL,
        age INT NOT NULL,
        email VARCHAR(128) NOT NULL
    )
"""

const val DROP_TABLE_SQL = "DROP TABLE IF EXISTS customers"
const val INSERT_SQL = "INSERT INTO customers (id, cust_name, age, email) VALUES (?, ?, ?, ?)"
const val SELECT_BY_PK_SQL = "SELECT id, cust_name, age, email FROM customers WHERE id = ?"
const val SELECT_LIMIT_SQL = "SELECT id, cust_name, age, email FROM customers WHERE id <= ?"
const val UPDATE_SQL = "UPDATE customers SET age = ? WHERE id = ?"
const val DELETE_ALL_SQL = "DELETE FROM customers"

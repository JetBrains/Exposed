package org.jetbrains.exposed.v1.tests

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.vendors.cosineDistance
import org.jetbrains.exposed.v1.core.vendors.vector
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcPreparedStatementImpl
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Items : Table("ITEMS") {
    val id = integer("ID")
    val name = varchar("NAME", 20)
    val embedding = vector("EMBEDDING", dimensions = 3)  // native, no boilerplate
}

fun main() {

    val db = Database.connect(
        // Issue for Kotlin devs, they need to know this connection property (btw: hard to find online the possible values,
        // I needed to look into include.m4
        //url = "jdbc:oracle:thin:@localhost:1521/freepdb1?oracle.jdbc.vectorDefaultGetObjectType=float[]",
        url = "jdbc:oracle:thin:@localhost:1521/freepdb1",
        user = "scott",
        password = "tiger"
    )

    println("Driver version: ${db.vendor} ${db.version}")

    transaction(db) {

        // Like hibernate's show_sql
        addLogger(StdOutSqlLogger)

        // Helper function to calculate distance and show it
        // We could also have done:
        //      .orderBy(Items.embedding.cosineDistance(floatArrayOf(1.0f, 1.0f, 1.0f)))
        // If only the sorting was important
        val distance = Items.embedding.cosineDistance(floatArrayOf(0.9f, 0.1f, 0.1f))

        Items
        //.selectAll()
            .select(Items.id, Items.name, Items.embedding, distance)
        //    .select(Items.id, Items.name)
            .limit(5)
            .orderBy(distance)
            .forEach { row ->
                println("ID: ${row[Items.id]} | Name: ${row[Items.name]} | Distance: ${row[distance]}")
        //            println("ID: ${row[Items.id]} | Name: ${row[Items.name]}")
            }
    }
}

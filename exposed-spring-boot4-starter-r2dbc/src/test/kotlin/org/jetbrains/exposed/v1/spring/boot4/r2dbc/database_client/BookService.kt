@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import java.util.UUID

@Component
open class BookService(
    @param:Qualifier("operator1")
    private val operator1: TransactionalOperator,
    @param:Qualifier("operator2")
    private val operator2: TransactionalOperator,
    private val databaseClient: DatabaseClient
) {

    fun testWithSpringAndExposedTransactions() = runTest {
        suspendTransaction {
            Book.new { description = "123" }
        }
        operator1.execute {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            databaseClient.sql(query).then()
        }
    }

    fun testWithSpringTransaction() = runTest {
        operator1.execute {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            databaseClient.sql(query).then()
        }
    }

    fun testWithExposedTransaction() = runTest {
        suspendTransaction {
            Book.new { description = "1234" }
        }
    }

    fun testWithoutSpringTransaction() = runTest {
        suspendTransaction {
            Book.new { description = "1234" }
        }
        operator2.execute {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            databaseClient.sql(query).then()
        }
    }
}

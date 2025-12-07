@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.UUID

@Component
open class AuthorService(
    @Qualifier("operator1")
    private val operator1: TransactionalOperator,
    @Qualifier("operator2")
    private val operator2: TransactionalOperator,
    private val r2dbcDatabaseClient: DatabaseClient
) {

    suspend fun testWithSpringAndExposedTransactions() {
        suspendTransaction {
            AuthorTable.insert { it[description] = "123" } get AuthorTable.id
        }
        operator1.executeAndAwait {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            r2dbcDatabaseClient
                .sql(query)
                .fetch()
                .rowsUpdated()
                .subscribe()
        }
    }

    suspend fun testWithSpringTransaction() {
        operator1.executeAndAwait {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            r2dbcDatabaseClient
                .sql(query)
                .fetch()
                .rowsUpdated()
                .subscribe()
        }
    }

    suspend fun testWithExposedTransaction() {
        suspendTransaction {
            AuthorTable.insert { it[description] = "1234" }
        }
    }

    suspend fun testWithoutSpringTransaction() {
        suspendTransaction {
            AuthorTable.insert { it[description] = "1234" }
        }
        operator2.executeAndAwait {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            r2dbcDatabaseClient
                .sql(query)
                .fetch()
                .rowsUpdated()
                .subscribe()
        }
    }
}

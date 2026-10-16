@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import org.springframework.r2dbc.core.awaitOne
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.UUID
import kotlin.test.assertEquals

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
        operator1.executeAndAwait {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            databaseClient.sql(query).await()
        }
        assertEquals(2, getCount())
        assertEquals(2, reset())
    }

    fun testWithSpringTransaction() = runTest {
        operator1.executeAndAwait {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            databaseClient.sql(query).await()
        }
        assertEquals(1, getCount())
        assertEquals(1, reset())
    }

    fun testWithExposedTransaction() = runTest {
        suspendTransaction {
            Book.new { description = "1234" }
        }
        assertEquals(1, getCount())
        assertEquals(1, reset())
    }

    fun testWithoutSpringTransaction() = runTest {
        suspendTransaction {
            Book.new { description = "1234" }
        }
        operator2.executeAndAwait {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            databaseClient.sql(query).await()
        }
        assertEquals(2, getCount())
        assertEquals(2, reset())
    }

    private suspend fun getCount(): Long = databaseClient
        .sql("select count(*) from authors")
        .mapValue(Long::class.javaObjectType)
        .awaitOne()

    // clears inserted data and returns number of deleted rows
    private suspend fun reset(): Long = databaseClient
        .sql("delete from authors")
        .fetch()
        .rowsUpdated()
        .awaitSingle()
}

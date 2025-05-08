@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`jdbc-template`

import org.jetbrains.exposed.v1.sql.transactions.transaction
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID

@Component
open class BookService(
    @Qualifier("operations1")
    private val operations1: TransactionOperations,
    @Qualifier("operations2")
    private val operations2: TransactionOperations,
    private val jdbcTemplate: JdbcTemplate
) {

    fun testWithSpringAndExposedTransactions() {
        transaction {
            Book.new { description = "123" }
        }
        operations1.execute {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            jdbcTemplate.execute(query)
        }
    }

    fun testWithSpringTransaction() {
        operations1.execute {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            jdbcTemplate.execute(query)
        }
    }

    fun testWithExposedTransaction() {
        transaction {
            Book.new { description = "1234" }
        }
    }

    fun testWithoutSpringTransaction() {
        transaction {
            Book.new { description = "1234" }
        }
        operations2.execute {
            val id = UUID.randomUUID().toString()
            val query = "insert into authors(id, description) values ('$id', '234234')"
            jdbcTemplate.execute(query)
        }
    }
}

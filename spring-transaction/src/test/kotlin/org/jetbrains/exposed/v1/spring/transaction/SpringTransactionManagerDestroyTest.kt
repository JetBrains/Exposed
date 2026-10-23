package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.support.TransactionTemplate
import java.util.function.Supplier
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SpringTransactionManagerDestroyTest {

    @Test
    fun `closing the context unregisters the database`() {
        val db = newContext().use { it.inTransaction { this.db } }

        assertFailsWith<IllegalStateException> { TransactionManager.managerFor(db) }
        assertNotEquals(db, TransactionManager.primaryDatabase)
    }

    @Test
    fun `closing one context keeps the database of another context registered`() {
        newContext().use { other ->
            val closedDb = newContext().use { it.inTransaction { db } }
            val otherDb = other.inTransaction { db }

            assertFailsWith<IllegalStateException> { TransactionManager.managerFor(closedDb) }
            assertEquals(otherDb, TransactionManager.managerFor(otherDb).db)
            assertEquals(true, other.inTransaction { exec("SELECT 1") { it.next() } })
        }
    }

    private fun newContext() = AnnotationConfigApplicationContext().apply {
        registerBean(DataSource::class.java, Supplier<DataSource> { newDataSource() })
        registerBean(SpringTransactionManager::class.java, Supplier { SpringTransactionManager(getBean(DataSource::class.java)) })
        refresh()
    }

    private fun newDataSource() = EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build()

    private fun <T> AnnotationConfigApplicationContext.inTransaction(block: JdbcTransaction.() -> T): T =
        TransactionTemplate(getBean(SpringTransactionManager::class.java)).execute { TransactionManager.current().block() }!!
}

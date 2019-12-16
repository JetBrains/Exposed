package org.jetbrains.exposed.sql.tests.sqlite

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.sqlite.SQLiteDataSource
import java.sql.Connection

/**
 *
 * Issue #91: TransactionManager.currentThreadManager not always reset with multiple Database.connect calls
 * https://github.com/JetBrains/Exposed/issues/91
 *
 * Demonstrates bug for following sequence:
 *
 * 1. Connect to Database
 * 2. Access Database.url
 * 3. Discard underlying database (e.g. delete file)
 * 4. Connect to Database (with new underlying file)
 * 5. Perform a transaction which fails
 *
 */
class MultipleDatabaseBugTest {

    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private var db: Database? = null

    @Before
    fun before() {
        val filename = folder.newFile("foo.db").absolutePath
        val ds = SQLiteDataSource()
        ds.url = "jdbc:sqlite:" + filename
        db = Database.connect(ds)

        // SQLite supports only TRANSACTION_SERIALIZABLE and TRANSACTION_READ_UNCOMMITTED
        db.transactionManager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    }

    @Test
    fun test1() {
        printStuff(1)
    }

    @Test
    fun test2() {
        printStuff(2)
        initDb()
    }

    private fun printStuff(test: Int) {
        // Accessing "db?.url" lazily initializes Database.url by calling Database.metadata
        // which first calls TransactionManager.currentOrNull which initializes the
        // TransactionManager.currentThreadManager ThreadLocal value which is never removed if
        // there is no subsequent transaction
        println("test$test url: ${db?.url}")
    }

    private fun initDb() {
        transaction(db) {
            println("TransactionManager: ${db.transactionManager}")
            println("Transaction connection url: ${connection.metadata { url }}")
        }
    }
}
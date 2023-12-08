package org.jetbrains.exposed.sql.tests.shared.types

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test
import java.sql.Connection
import kotlin.test.assertNotNull

class ConnectionPerformanceTest {
    object Users : IntIdTable("users") {
        val username = varchar("username", 64)
    }

    class User(id: EntityID<Int>) : IntEntity(id) {
        var username by Users.username
        companion object : IntEntityClass<User>(Users)
    }

    @Test
    fun testConnectionWithHikariConfig() {
        Assume.assumeTrue(setOf(TestDB.MYSQL, TestDB.MARIADB, TestDB.POSTGRESQL, TestDB.SQLSERVER).containsAll(TestDB.enabledDialects()))
        val dialect = TestDB.enabledDialects().first()

        val hikari = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = dialect.connection.invoke()
                driverClassName = dialect.driver
                username = dialect.user
                password = dialect.pass
                maximumPoolSize = 6
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )
        val db = Database.connect(hikari)

        // SQLite, Oracle, and H2 have limited isolation levels (?)
        val (sql, repeatable, committed) = when (dialect) {
            TestDB.POSTGRESQL -> Triple("SHOW TRANSACTION ISOLATION LEVEL", "repeatable read", "read committed")
            TestDB.MYSQL, TestDB.MARIADB -> Triple("SELECT @@tx_isolation", "REPEATABLE-READ", "READ-COMMITTED")
            TestDB.SQLSERVER -> Triple("SELECT transaction_isolation_level FROM sys.dm_exec_sessions WHERE session_id = @@SPID", "3", "2")
            else -> Triple("", "", "")
        }

        transaction(db) {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)

            assertTransactionIsolationLevel(sql, repeatable)
        }

        transaction(db) {
            User.new { username = "A" }

            assertTransactionIsolationLevel(sql, repeatable)
        }

        // transaction level setting should override datasource setting
        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED, db = db) {
            User.findById(1)

            assertTransactionIsolationLevel(sql, committed)
        }

        transaction(db) {
            SchemaUtils.drop(Users)

            assertTransactionIsolationLevel(sql, repeatable)
        }
    }

    private fun Transaction.assertTransactionIsolationLevel(sql: String, expected: String) {
        val actual = exec("""$sql;""") {
            it.next()
            it.getString(1)
        }
        assertNotNull(actual)
        assertEquals(expected, actual)
    }
}

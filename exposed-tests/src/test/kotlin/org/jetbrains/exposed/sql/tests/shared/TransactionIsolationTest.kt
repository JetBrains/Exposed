package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.junit.Test
import java.sql.Connection

class TransactionIsolationTest : DatabaseTestsBase() {
    @Test
    fun `test what transaction isolation was applied`() {
        withDb {
            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                repetitionAttempts = 1
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, this.connection.transactionIsolation)
            }
        }
    }
}

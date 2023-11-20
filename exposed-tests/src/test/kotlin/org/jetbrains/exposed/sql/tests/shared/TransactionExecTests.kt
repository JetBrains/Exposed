package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.inProperCase
import org.junit.Test

class TransactionExecTests : DatabaseTestsBase() {
    object ExecTable : Table("exec_table") {
        val id = integer("id").autoIncrement(idSeqName = "exec_id_seq")
        val amount = integer("amount")
        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testExecWithSingleStatementQuery() {
        withTables(ExecTable) {
            val amounts = (90..99).toList()
            ExecTable.batchInsert(amounts, shouldReturnGeneratedValues = false) { amount ->
                this[ExecTable.id] = (amount % 10 + 1)
                this[ExecTable.amount] = amount
            }

            exec(
                "SELECT * FROM ${ExecTable.tableName.inProperCase()}",
                explicitStatementType = StatementType.SELECT
            ) { resultSet ->
                val allAmounts = mutableListOf<Int>()
                while (resultSet.next()) {
                    allAmounts.add(resultSet.getInt("amount"))
                }
                assertEqualLists(amounts, allAmounts)
            }
        }
    }
}

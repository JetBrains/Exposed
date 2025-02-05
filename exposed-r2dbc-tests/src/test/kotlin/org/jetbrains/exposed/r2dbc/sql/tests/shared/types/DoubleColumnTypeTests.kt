package org.jetbrains.exposed.r2dbc.sql.tests.shared.types

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEquals
import org.junit.Test

class DoubleColumnTypeTests : R2dbcDatabaseTestsBase() {
    object TestTable : IntIdTable("double_table") {
        val amount = double("amount")
    }

    @Test
    fun testInsertAndSelectFromDoubleColumn() {
        withTables(TestTable) {
            val id = TestTable.insertAndGetId {
                it[amount] = 9.23
            }

            TestTable.selectAll().where { TestTable.id eq id }.singleOrNull()?.let {
                assertEquals(9.23, it[TestTable.amount])
            }
        }
    }

    @Test
    fun testInsertAndSelectFromRealColumn() {
        withDb {
            val originalColumnDDL = TestTable.amount.descriptionDdl()
            val realColumnDDL = originalColumnDDL.replace(" DOUBLE PRECISION ", " REAL ")

            // create table with double() column that uses SQL type REAL
            TestTable.ddl
                .map { it.replace(originalColumnDDL, realColumnDDL) }
                .forEach { exec(it) }

            val id = TestTable.insertAndGetId {
                it[amount] = 9.23
            }

            TestTable.selectAll().where { TestTable.id eq id }.singleOrNull()?.let {
                assertEquals(9.23, it[TestTable.amount])
            }

            SchemaUtils.drop(TestTable)
        }
    }
}

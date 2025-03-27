package org.jetbrains.exposed.r2dbc.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.r2dbc.sql.batchInsert
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.junit.Test

class CountTests : R2dbcDatabaseTestsBase() {
    @Test
    fun `test that count() works with Query that contains distinct and columns with same name from different tables`() {
        withCitiesAndUsers { cities, users, _ ->
            assertEquals(3L, cities.innerJoin(users).selectAll().withDistinct().count())
        }
    }

    @Test
    fun `test that count() works with Query that contains distinct and columns with same name from different tables and already defined alias`() {
        withCitiesAndUsers { cities, users, _ ->
            assertEquals(3L, cities.innerJoin(users).select(users.id.alias("usersId"), cities.id).withDistinct().count())
        }
    }

    @Test
    fun testCountAliasWithTableSchema() {
        val custom = prepareSchemaForTest("custom")
        val tester = object : Table("custom.tester") {
            val amount = integer("amount")
        }

        withSchemas(custom) {
            SchemaUtils.create(tester)

            repeat(3) {
                tester.insert {
                    it[amount] = 99
                }
            }

            // count alias is generated for any query with distinct/groupBy/limit & throws if schema name included
            assertEquals(1, tester.select(tester.amount).withDistinct().count())

            if (currentDialectTest is SQLServerDialect) {
                SchemaUtils.drop(tester)
            }
        }
    }

    @Test
    fun testCountWithOffsetWithoutLimit() {
        val tester = object : IntIdTable("users") {
            val value = integer("value")
        }

        // MariaDB, Mysql do not support OFFSET clause without LIMIT
        withTables(excludeSettings = TestDB.ALL_MYSQL_MARIADB, tester) {
            tester.batchInsert(listOf(1, 2, 3, 4, 5)) {
                this[tester.value] = it
            }

            assertEquals(5, tester.selectAll().count())

            assertEquals(2, tester.selectAll().offset(1).limit(2).count())

            assertEquals(2, tester.selectAll().limit(2).count())

            assertEquals(3, tester.selectAll().offset(2).count())
        }
    }
}

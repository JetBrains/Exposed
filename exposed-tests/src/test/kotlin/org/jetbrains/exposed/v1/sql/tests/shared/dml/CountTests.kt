package org.jetbrains.exposed.v1.sql.tests.shared.dml

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.currentDialectTest
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.junit.Test

class CountTests : DatabaseTestsBase() {
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
    fun `test that count() returns right value for Query with group by`() {
        withCitiesAndUsers { _, _, userData ->
            val uniqueUsersInData = userData.select(userData.user_id).withDistinct().count()
            val sameQueryWithGrouping = userData.select(userData.value.max()).groupBy(userData.user_id).count()
            assertEquals(uniqueUsersInData, sameQueryWithGrouping)
        }

        withTables(OrgMemberships, Orgs) {
            val org1 = Org.new {
                name = "FOo"
            }
            OrgMembership.new {
                org = org1
            }

            assertEquals(1L, OrgMemberships.selectAll().count())
        }
    }

    @Test
    fun testCountAliasWithTableSchema() {
        val custom = prepareSchemaForTest("custom")
        val tester = object : Table("custom.tester") {
            val amount = integer("amount")
        }

        withSchemas(custom) {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(tester)

            repeat(3) {
                tester.insert {
                    it[amount] = 99
                }
            }

            // count alias is generated for any query with distinct/groupBy/limit & throws if schema name included
            assertEquals(1, tester.select(tester.amount).withDistinct().count())

            if (currentDialectTest is SQLServerDialect) {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tester)
            }
        }
    }

    @Test
    fun testCountWithOffsetWithoutLimit() {
        val tester = object : IntIdTable("users") {
            val value = integer("value")
        }

        // SQLite, MariaDB, Mysql do not support OFFSET clause without LIMIT
        withTables(excludeSettings = TestDB.ALL_MYSQL_MARIADB + TestDB.SQLITE, tester) {
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

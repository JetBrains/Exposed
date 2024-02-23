package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.*
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
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
}

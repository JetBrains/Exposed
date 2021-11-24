package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.*
import org.junit.Test

class CountTests : DatabaseTestsBase() {
    @Test
    fun `test that count() works with Query that contains distinct and columns with same name from different tables`() {
        withCitiesAndUsers {
            assertEquals(3L, cities.innerJoin(users).selectAll().withDistinct().count())

            assertEquals(2L, cities.innerJoin(scopedUsers).selectAll().withDistinct().count())
        }
    }

    @Test
    fun `test that count() works with Query that contains distinct and columns with same name from different tables and already defined alias`() {
        withCitiesAndUsers {
            cities.innerJoin(users)
                .slice(users.id.alias("usersId"), cities.id)
                .selectAll()
                .withDistinct()
                .count().let { assertEquals(3L, it) }

            cities.innerJoin(scopedUsers)
                .slice(scopedUsers.id.alias("user_ids"), cities.id)
                .selectAll()
                .withDistinct()
                .count().let { assertEquals(2L, it) }

        }
    }

    @Test
    fun `test that count() returns right value for Query with group by`() {
        withCitiesAndUsers {
            val uniqueUsersInData = userData.slice(userData.user_id).selectAll().withDistinct().count()
            val sameQueryWithGrouping = userData.slice(userData.value.max()).selectAll().groupBy(userData.user_id).count()
            assertEquals(uniqueUsersInData, sameQueryWithGrouping)

            val scopedUniqueUsersInData = scopedUserData.slice(scopedUserData.userId).selectAll().withDistinct().count()
            val scopedSameQueryWithGrouping = scopedUserData.slice(scopedUserData.value.max()).selectAll().groupBy(scopedUserData.userId).count()
            assertEquals(scopedUniqueUsersInData, scopedSameQueryWithGrouping)
        }

        withTables(OrgMemberships, Orgs) {
            val org1 = Org.new {
                name = "FOo"
            }
            val membership = OrgMembership.new {
                org = org1
            }

            assertEquals(1L, OrgMemberships.selectAll().count())
        }
    }
}

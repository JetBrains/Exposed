package org.jetbrains.exposed.v1.sql.tests.shared.entities

import org.jetbrains.exposed.v1.core.SortOrder.DESC
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.tests.shared.assertTrue
import org.junit.Test

class OrderedReferenceTest : DatabaseTestsBase() {
    object Users : IntIdTable()

    object UserRatings : IntIdTable() {
        val value = integer("value")
        val user = reference("user", Users)
    }

    object UserNullableRatings : IntIdTable() {
        val value = integer("value")
        val user = reference("user", Users).nullable()
    }

    class UserRatingDefaultOrder(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserRatingDefaultOrder>(UserRatings)

        var value by UserRatings.value
        var user by UserDefaultOrder referencedOn UserRatings.user
    }

    class UserNullableRatingDefaultOrder(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserNullableRatingDefaultOrder>(UserNullableRatings)

        var value by UserNullableRatings.value
        var user by UserDefaultOrder optionalReferencedOn UserNullableRatings.user
    }

    class UserDefaultOrder(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserDefaultOrder>(Users)

        val ratings by UserRatingDefaultOrder referrersOn UserRatings.user orderBy UserRatings.value
        val nullableRatings by UserNullableRatingDefaultOrder optionalReferrersOn UserNullableRatings.user orderBy UserNullableRatings.value
    }

    @Test
    fun testDefaultOrder() {
        withOrderedReferenceTestTables {
            val user = UserDefaultOrder.all().first()

            unsortedRatingValues.sorted().zip(user.ratings).forEach { (value, rating) ->
                assertEquals(value, rating.value)
            }
            unsortedRatingValues.sorted().zip(user.nullableRatings).forEach { (value, rating) ->
                assertEquals(value, rating.value)
            }
        }
    }

    class UserRatingMultiColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserRatingMultiColumn>(UserRatings)

        var value by UserRatings.value
        var user by UserMultiColumn referencedOn UserRatings.user
    }

    class UserNullableRatingMultiColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserNullableRatingMultiColumn>(UserNullableRatings)

        var value by UserNullableRatings.value
        var user by UserMultiColumn optionalReferencedOn UserNullableRatings.user
    }

    class UserMultiColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserMultiColumn>(Users)

        val ratings by UserRatingMultiColumn
            .referrersOn(UserRatings.user)
            .orderBy(UserRatings.value to DESC, UserRatings.id to DESC)
        val nullableRatings by UserNullableRatingMultiColumn
            .optionalReferrersOn(UserNullableRatings.user)
            .orderBy(
                UserNullableRatings.value to DESC,
                UserNullableRatings.id to DESC
            )
    }

    @Test
    fun testMultiColumnOrder() {
        withOrderedReferenceTestTables {
            val ratings = UserMultiColumn.all().first().ratings.toList()
            val nullableRatings = UserMultiColumn.all().first().nullableRatings.toList()

            // Ensure each value is less than the one before it.
            // IDs should be sorted within groups of identical values.
            fun assertRatingsOrdered(current: UserRatingMultiColumn, prev: UserRatingMultiColumn) {
                assertTrue(current.value <= prev.value)
                if (current.value == prev.value) {
                    assertTrue(current.id.value <= prev.id.value)
                }
            }

            fun assertNullableRatingsOrdered(current: UserNullableRatingMultiColumn, prev: UserNullableRatingMultiColumn) {
                assertTrue(current.value <= prev.value)
                if (current.value == prev.value) {
                    assertTrue(current.id.value <= prev.id.value)
                }
            }

            for (i in 1..<ratings.size) {
                assertRatingsOrdered(ratings[i], ratings[i - 1])
                assertNullableRatingsOrdered(nullableRatings[i], nullableRatings[i - 1])
            }
        }
    }

    class UserRatingChainedColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserRatingChainedColumn>(UserRatings)

        var value by UserRatings.value
        var user by UserChainedColumn referencedOn UserRatings.user
    }

    class UserChainedColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserChainedColumn>(Users)

        val ratings by UserRatingChainedColumn referrersOn UserRatings.user orderBy (UserRatings.value to DESC) orderBy (UserRatings.id to DESC)
    }

    @Test
    fun testChainedOrderBy() {
        withOrderedReferenceTestTables {
            val ratings = UserChainedColumn.all().first().ratings.toList()

            fun assertRatingsOrdered(current: UserRatingChainedColumn, prev: UserRatingChainedColumn) {
                assertTrue(current.value <= prev.value)
                if (current.value == prev.value) {
                    assertTrue(current.id.value <= prev.id.value)
                }
            }

            for (i in 1..<ratings.size) {
                assertRatingsOrdered(ratings[i], ratings[i - 1])
            }
        }
    }

    private val unsortedRatingValues = listOf(0, 3, 1, 2, 4, 4, 5, 4, 5, 6, 9, 8)

    private fun withOrderedReferenceTestTables(statement: JdbcTransaction.(TestDB) -> Unit) {
        withTables(Users, UserRatings, UserNullableRatings) { db ->
            val userId = Users.insertAndGetId { }
            unsortedRatingValues.forEach { value ->
                UserRatings.insert {
                    it[user] = userId
                    it[UserRatings.value] = value
                }
                UserNullableRatings.insert {
                    it[user] = userId
                    it[UserRatings.value] = value
                }
                UserNullableRatings.insert {
                    it[user] = null
                    it[UserRatings.value] = value
                }
            }
            statement(db)
        }
    }
}

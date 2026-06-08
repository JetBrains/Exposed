package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.relationships.load
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertNotNull

class OrderedReferenceTest : R2dbcDatabaseTestsBase() {
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
        val user by UserDefaultOrder referencedOn UserRatings.user
    }

    class UserNullableRatingDefaultOrder(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserNullableRatingDefaultOrder>(UserNullableRatings)

        var value by UserNullableRatings.value
        val user by UserDefaultOrder optionalReferencedOn UserNullableRatings.user
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

            unsortedRatingValues.sorted().toList().zip(user.ratings.toList()).forEach { (value, rating) ->
                assertEquals(value, rating.value)
            }
            unsortedRatingValues.sorted().zip(user.nullableRatings.toList()).forEach { (value, rating) ->
                assertEquals(value, rating.value)
            }
        }
    }

    @Test
    fun testNoDuplicatedOrderByPartsInQuery() {
        // This interceptor counts duplicated ORDER BY parts in the sql sent to database.
        // We want to be sure that DAO doesn't create duplicated parts.
        val interceptor = object : StatementInterceptor {
            var maxDuplicates = 0
            override fun beforeExecution(transaction: Transaction, context: StatementContext) {
                val duplicatedPartsAmount = context.statement.prepareSQL(transaction)
                    // Get all the parts from order by section
                    .lowercase()
                    .substringAfter("order by")
                    .split(",")
                    .map { it.trim() }
                    // Count the occurrences of each part and take maximum
                    .groupBy { it }
                    .mapValues { (_, list) -> list.size }
                    .maxByOrNull { it.value }
                    ?.value ?: 0

                maxDuplicates = max(maxDuplicates, duplicatedPartsAmount)
            }
        }

        withOrderedReferenceTestTables {
            registerInterceptor(interceptor)
            // `orderBy` on references in DAO Entity classes could collect duplicated parts.
            // That method is executed on every access to the field, so every query has
            // one more duplicated part
            // It's mentioned in the original issue
            // 'EXPOSED-950 Order by clause is repeated hundredfold'
            repeat(5) {
                val user = UserDefaultOrder.all().first()
                entityCache.clear()

                // This sections needs only to force DAO fetch the data to execute SQL queries
                user.ratings.forEach { rating ->
                    assertNotNull(rating.value)
                }

                assertEquals(1, interceptor.maxDuplicates)
            }
        }
    }

    class UserRatingMultiColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserRatingMultiColumn>(UserRatings)

        var value by UserRatings.value
        val user by UserMultiColumn referencedOn UserRatings.user
    }

    class UserNullableRatingMultiColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserNullableRatingMultiColumn>(UserNullableRatings)

        var value by UserNullableRatings.value
        val user by UserMultiColumn optionalReferencedOn UserNullableRatings.user
    }

    class UserMultiColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserMultiColumn>(Users)

        val ratings by UserRatingMultiColumn
            .referrersOn(UserRatings.user)
            .orderBy(UserRatings.value to SortOrder.DESC, UserRatings.id to SortOrder.DESC)
        val nullableRatings by UserNullableRatingMultiColumn
            .optionalReferrersOn(UserNullableRatings.user)
            .orderBy(
                UserNullableRatings.value to SortOrder.DESC,
                UserNullableRatings.id to SortOrder.DESC
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
        val user by UserChainedColumn referencedOn UserRatings.user
    }

    class UserChainedColumn(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserChainedColumn>(Users)

        val ratings by UserRatingChainedColumn referrersOn UserRatings.user orderBy
            (UserRatings.value to SortOrder.DESC) orderBy (UserRatings.id to SortOrder.DESC)
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

    @Test
    fun testOrderByWithEagerLoad() {
        withOrderedReferenceTestTables {
            // Clearing cache is not critical, just to be sure that there are no caches from
            //  creating entities step
            entityCache.clear()

            val user = UserDefaultOrder.all().first().load(UserDefaultOrder::ratings)

            val expected = user.ratings.map { it.value }.toList().sorted()
            val actual = user.ratings.map { it.value }

            assertEqualLists(expected, actual)
        }
    }

    private val unsortedRatingValues = listOf(0, 3, 1, 2, 4, 4, 5, 4, 5, 6, 9, 8)

    private fun withOrderedReferenceTestTables(statement: suspend R2dbcTransaction.(TestDB) -> Unit) {
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

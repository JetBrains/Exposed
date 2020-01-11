package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test

object EntityHookTestData {
    object Users : IntIdTable() {
        val name = varchar("name", 50).index()
        val age = integer("age")
    }

    object Cities: IntIdTable() {
        val name = varchar("name", 50)
        val country = reference("country", Countries)
    }

    object Countries: IntIdTable() {
        val name = varchar("name", 50)
    }

    object UsersToCities: org.jetbrains.exposed.sql.Table() {
        val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
        val city = reference("city", Cities, onDelete = ReferenceOption.CASCADE)
    }

    class User(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<User>(Users)

        var name by Users.name
        var age by Users.age
        var cities by City via UsersToCities
    }

    class City(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<City>(Cities)

        var name by Cities.name
        var users by User via UsersToCities
        var country by Country referencedOn Cities.country
    }

    class Country(id: EntityID<Int>): IntEntity(id) {
        companion object : IntEntityClass<Country>(Countries)

        var name by Countries.name
        val cities by City referrersOn Cities.country
    }

    val  allTables = arrayOf(Users, Cities, UsersToCities, Countries)
}

class EntityHookTest: DatabaseTestsBase() {

    private fun <T> trackChanges(statement: Transaction.() -> T): Triple<T, Collection<EntityChange>, String> {
        val alreadyChanged = TransactionManager.current().registeredChanges().size
        return transaction {
            val result = statement()
            flushCache()
            Triple(result, registeredChanges().drop(alreadyChanged), id)
        }
    }

    @Test fun testCreated01() {
        withTables(*EntityHookTestData.allTables) {
            val (_, events, txId) = trackChanges {
                val ru = EntityHookTestData.Country.new {
                    name = "RU"
                }
                val x = EntityHookTestData.City.new {
                    name = "St. Petersburg"
                    country = ru
                }
            }

            assertEquals(2, events.count())
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.City)?.name }, "St. Petersburg")
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.Country)?.name }, "RU")
            events.forEach{
                assertEquals(txId, it.transactionId)
            }
        }
    }

    @Test fun testDeleted01() {
        withTables(*EntityHookTestData.allTables) {
            val spbId = transaction {
                val ru = EntityHookTestData.Country.new {
                    name = "RU"
                }
                val x = EntityHookTestData.City.new {
                    name = "St. Petersburg"
                    country = ru
                }

                flushCache()
                x.id
            }

            val (_, events, txId) = trackChanges {
                val spb = EntityHookTestData.City.findById(spbId)!!
                spb.delete()
            }

            assertEquals(1, events.count())
            assertEquals(EntityChangeType.Removed, events.single().changeType)
            assertEquals(spbId, events.single().entityId)
            assertEquals(txId, events.single().transactionId)
        }
    }

    @Test fun testModifiedSimple01() {
        withTables(*EntityHookTestData.allTables) {
            transaction {
                val ru = EntityHookTestData.Country.new {
                    name = "RU"
                }
                val x = EntityHookTestData.City.new {
                    name = "St. Petersburg"
                    country = ru
                }

                flushCache()
            }

            val (_, events, txId) = trackChanges {
                val de = EntityHookTestData.Country.new {
                    name = "DE"
                }
                val x = EntityHookTestData.City.all().single()
                x.name = "Munich"
                x.country = de
            }
            // TODO: one may expect change for RU but we do not send it due to performance reasons
            assertEquals(2, events.count())
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.City)?.name }, "Munich")
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.Country)?.name }, "DE")
            events.forEach{
                assertEquals(txId, it.transactionId)
            }
        }
    }

    @Test fun testModifiedInnerTable01() {
        withTables(*EntityHookTestData.allTables) {
            transaction {
                val ru = EntityHookTestData.Country.new {
                    name = "RU"
                }
                val de = EntityHookTestData.Country.new {
                    name = "DE"
                }
                EntityHookTestData.City.new {
                    name = "St. Petersburg"
                    country = ru
                }
                EntityHookTestData.City.new {
                    name = "Munich"
                    country = de
                }
                EntityHookTestData.User.new {
                    name = "John"
                    age = 30
                }

                flushCache()
            }

            val (_, events, txId) = trackChanges {
                val spb = EntityHookTestData.City.find { EntityHookTestData.Cities.name eq "St. Petersburg" }.single()
                val john = EntityHookTestData.User.all().single()
                john.cities = SizedCollection(listOf(spb))
            }

            assertEquals(2, events.count())
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.City)?.name }, "St. Petersburg")
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.User)?.name }, "John")
            events.forEach{
                assertEquals(txId, it.transactionId)
            }
        }
    }

    @Test fun testModifiedInnerTable02() {
        withTables(*EntityHookTestData.allTables) {
            transaction {
                val ru = EntityHookTestData.Country.new {
                    name = "RU"
                }
                val de = EntityHookTestData.Country.new {
                    name = "DE"
                }
                val spb = EntityHookTestData.City.new {
                    name = "St. Petersburg"
                    country = ru
                }
                val muc = EntityHookTestData.City.new {
                    name = "Munich"
                    country = de
                }
                val john = EntityHookTestData.User.new {
                    name = "John"
                    age = 30
                }

                john.cities = SizedCollection(listOf(muc))
                flushCache()
            }

            val (_, events, txId) = trackChanges {
                val spb = EntityHookTestData.City.find({ EntityHookTestData.Cities.name eq "St. Petersburg" }).single()
                val john = EntityHookTestData.User.all().single()
                john.cities = SizedCollection(listOf(spb))
            }

            assertEquals(3, events.count())
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.City)?.name }, "St. Petersburg", "Munich")
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.User)?.name }, "John")
            events.forEach{
                assertEquals(txId, it.transactionId)
            }
        }
    }

    @Test fun testModifiedInnerTable03() {
        withTables(*EntityHookTestData.allTables) {
            transaction {
                val ru = EntityHookTestData.Country.new {
                    name = "RU"
                }
                val de = EntityHookTestData.Country.new {
                    name = "DE"
                }
                val spb = EntityHookTestData.City.new {
                    name = "St. Petersburg"
                    country = ru
                }
                val muc = EntityHookTestData.City.new {
                    name = "Munich"
                    country = de
                }
                val john = EntityHookTestData.User.new {
                    name = "John"
                    age = 30
                }

                john.cities = SizedCollection(listOf(spb))
                flushCache()
            }

            val (_, events, txId) = trackChanges {
                val john = EntityHookTestData.User.all().single()
                john.cities = SizedCollection(emptyList())
            }

            assertEquals(2, events.count())
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.City)?.name }, "St. Petersburg")
            assertEqualCollections(events.mapNotNull { it.toEntity(EntityHookTestData.User)?.name }, "John")
            events.forEach{
                assertEquals(txId, it.transactionId)
            }
        }
    }

    @Test fun `single entity flush should trigger events`() {
        withTables(EntityHookTestData.User.table) {
            val (user, events, _) = trackChanges {
                EntityHookTestData.User.new {
                    name = "John"
                    age = 30
                }.apply { flush() }
            }

            assertEquals(1, events.size)
            val createEvent = events.single()
            assertEquals(user.id, createEvent.entityId)
            assertEquals(EntityChangeType.Created, createEvent.changeType)

            val (_, events2, _) = trackChanges {
                user.name = "Carl"
                user.flush()
            }

            assertEquals("Carl", user.name)
            assertEquals(1, events2.size)
            val updateEvent = events2.single()
            assertEquals(user.id, updateEvent.entityId)
            assertEquals(EntityChangeType.Updated, updateEvent.changeType)
        }
    }

}

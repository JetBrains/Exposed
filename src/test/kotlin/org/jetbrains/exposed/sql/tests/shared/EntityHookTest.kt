package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
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
    @Test fun testCreated01() {
        withTables(*EntityHookTestData.allTables) {
            val entities = transactionWithEntityHook {
                val ru = EntityHookTestData.Country.new {
                    name = "RU"
                }
                val x = EntityHookTestData.City.new {
                    name = "St. Petersburg"
                    country = ru
                }
            }

            assertEquals(2, entities.second.count())
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.City)?.name }.filterNotNull(), "St. Petersburg")
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.Country)?.name }.filterNotNull(), "RU")
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

            val entities = transactionWithEntityHook {
                val spb = EntityHookTestData.City.findById(spbId)!!
                spb.delete()
            }

            assertEquals(1, entities.second.count())
            assertEquals(EntityChangeType.Removed, entities.second.single().changeType)
            assertEquals(spbId, entities.second.single().id)
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

            val entities = transactionWithEntityHook {
                val de = EntityHookTestData.Country.new {
                    name = "DE"
                }
                val x = EntityHookTestData.City.all().single()
                x.name = "Munich"
                x.country = de
            }
            // TODO: one may expect change for RU but we do not send it due to performance reasons
            assertEquals(2, entities.second.count())
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.City)?.name }.filterNotNull(), "Munich")
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.Country)?.name }.filterNotNull(), "DE")
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

            val entities = transactionWithEntityHook {
                val spb = EntityHookTestData.City.find({ EntityHookTestData.Cities.name eq "St. Petersburg" }).single()
                var john = EntityHookTestData.User.all().single()
                john.cities = SizedCollection(listOf(spb))
            }

            assertEquals(2, entities.second.count())
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.City)?.name }.filterNotNull(), "St. Petersburg")
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.User)?.name }.filterNotNull(), "John")
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

            val entities = transactionWithEntityHook {
                val spb = EntityHookTestData.City.find({ EntityHookTestData.Cities.name eq "St. Petersburg" }).single()
                var john = EntityHookTestData.User.all().single()
                john.cities = SizedCollection(listOf(spb))
            }

            assertEquals(3, entities.second.count())
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.City)?.name }.filterNotNull(), "St. Petersburg", "Munich")
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.User)?.name }.filterNotNull(), "John")
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

            val entities = transactionWithEntityHook {
                var john = EntityHookTestData.User.all().single()
                john.cities = SizedCollection(emptyList())
            }

            assertEquals(2, entities.second.count())
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.City)?.name }.filterNotNull(), "St. Petersburg")
            assertEqualCollections(entities.second.map { it.toEntity(EntityHookTestData.User)?.name }.filterNotNull(), "John")
        }
    }
}

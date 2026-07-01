package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.UuidEntity
import org.jetbrains.exposed.r2dbc.dao.UuidEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.tests.versionNumber
import kotlin.test.Test
import kotlin.uuid.Uuid

class UuidTableEntityTest : R2dbcDatabaseTestsBase() {
    @Suppress("MemberNameEqualsClassName")
    object UuidTables {
        object Cities : UuidTable() {
            val name = varchar("name", 50)
        }

        class City(id: EntityID<Uuid>) : UuidEntity(id) {
            companion object : UuidEntityClass<City>(Cities)

            var name by Cities.name
            val towns by Town referrersOn Towns.cityId
        }

        object People : UuidTable() {
            val name = varchar("name", 80)
            val cityId = reference("city_id", Cities)
        }

        class Person(id: EntityID<Uuid>) : UuidEntity(id) {
            companion object : UuidEntityClass<Person>(People)

            var name by People.name
            val city by City referencedOn People.cityId
        }

        object Addresses : UuidTable() {
            val person = reference("person_id", People)
            val city = reference("city_id", Cities)
            val address = varchar("address", 255)
        }

        class Address(id: EntityID<Uuid>) : UuidEntity(id) {
            companion object : UuidEntityClass<Address>(Addresses)

            val person by Person.referencedOn(Addresses.person)
            val city by City.referencedOn(Addresses.city)
            var address by Addresses.address
        }

        object Towns : UuidTable("towns") {
            val cityId: Column<Uuid> = uuid("city_id").references(Cities.id)
        }

        class Town(id: EntityID<Uuid>) : UuidEntity(id) {
            companion object : UuidEntityClass<Town>(Towns)

            val city by City referencedOn Towns.cityId
        }

        object Books : UuidTable(uuidVersion = UuidVersion.V7) { // id should use V7
            val title = varchar("title", 256)
            val ssid = uuid("ssid").autoGenerate() // should use V4
            val pubId = uuid("pub_id").autoGenerate(UuidVersion.V7) // should use V7
            val pubCityId = reference("pub_city_id", Cities) // should use V4 as Cities uses V4
        }

        class Book(id: EntityID<Uuid>) : UuidEntity(id) {
            companion object : UuidEntityClass<Book>(Books)

            var title by Books.title
            var ssid by Books.ssid
            var pubId by Books.pubId
            val pubCity by City referencedOn Books.pubCityId
        }
    }

    @Test
    fun `create tables`() {
        withTables(UuidTables.Cities, UuidTables.People) {
            assertEquals(true, UuidTables.Cities.exists())
            assertEquals(true, UuidTables.People.exists())
        }
    }

    @Test
    fun `create records`() {
        withTables(UuidTables.Cities, UuidTables.People) {
            val mumbai = UuidTables.City.new { name = "Mumbai" }.flush()
            val pune = UuidTables.City.new { name = "Pune" }.flush()
            UuidTables.Person.new(Uuid.random()) {
                name = "David D'souza"
                city.set(mumbai)
            }
            UuidTables.Person.new(Uuid.random()) {
                name = "Tushar Mumbaikar"
                city.set(mumbai)
            }
            UuidTables.Person.new(Uuid.random()) {
                name = "Tanu Arora"
                city.set(pune)
            }

            val allCities = UuidTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = UuidTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test
    fun `update and delete records`() {
        withTables(UuidTables.Cities, UuidTables.People) {
            val mumbai = UuidTables.City.new(Uuid.random()) { name = "Mumbai" }.flush()
            val pune = UuidTables.City.new(Uuid.random()) { name = "Pune" }.flush()
            UuidTables.Person.new(Uuid.random()) {
                name = "David D'souza"
                city.set(mumbai)
            }
            UuidTables.Person.new(Uuid.random()) {
                name = "Tushar Mumbaikar"
                city.set(mumbai)
            }
            val tanu = UuidTables.Person.new(Uuid.random()) {
                name = "Tanu Arora"
                city.set(pune)
            }.flush()

            tanu.delete()
            pune.delete()

            val allCities = UuidTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = UuidTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("Tanu Arora", "Pune")))
        }
    }

    @Test
    fun `insert with inner table`() {
        withTables(UuidTables.Addresses, UuidTables.Cities, UuidTables.People) {
            val city1 = UuidTables.City.new {
                name = "city1"
            }.flush()
            val person1 = UuidTables.Person.new {
                name = "person1"
                city.set(city1)
            }.flush()

            val address1 = UuidTables.Address.new {
                person.set(person1)
                city.set(city1)
                address = "address1"
            }.flush()

            val address2 = UuidTables.Address.new {
                person.set(person1)
                city.set(city1)
                address = "address2"
            }.flush()

            address1.refresh(flush = true)
            assertEquals("address1", address1.address)

            address2.refresh(flush = true)
            assertEquals("address2", address2.address)
        }
    }

    @Test
    fun testForeignKeyBetweenUuidAndEntityIDColumns() {
        withTables(UuidTables.Cities, UuidTables.Towns) {
            val cId = UuidTables.Cities.insertAndGetId {
                it[name] = "City A"
            }
            val tId = UuidTables.Towns.insertAndGetId {
                it[cityId] = cId.value
            }

            // lazy loaded referencedOn
            val town1 = UuidTables.Town.all().single()
            assertEquals(cId, town1.city().id)

            // eager loaded referencedOn
            val town1WithCity = UuidTables.Town.all().with(UuidTables.Town::city).single()
            assertEquals(cId, town1WithCity.city().id)

            // lazy loaded referrersOn
            val city1 = UuidTables.City.all().single()
            val towns = city1.towns
            assertEquals(cId, towns.first().city().id)

            // eager loaded referrersOn
            val city1WithTowns = UuidTables.City.all().with(UuidTables.City::towns).single()
            assertEquals(tId, city1WithTowns.towns.first().id)
        }
    }

    @Test
    fun testUuidVersionAutoGenerated() {
        withTables(UuidTables.Cities, UuidTables.Books) {
            // generateV4() used by default if UuidTable primary constructor used
            val munich = UuidTables.City.new { name = "Munich" }.flush()
            assertEquals(4, munich.id.value.versionNumber())

            // UuidTable secondary constructor used with generateV7() enabled
            val book1 = UuidTables.Book.new {
                title = "Joy of Kotlin"
                pubCity.set(munich)
            }.flush()
            // so only the UuidTable.id should automatically use V7 now
            assertEquals(7, book1.id.value.versionNumber())
            // other Uuid columns detected in the table should use whatever version they are defined to use
            assertEquals(4, book1.ssid.versionNumber())
            assertEquals(7, book1.pubId.versionNumber())
            assertEquals(4, book1.pubCity().id.value.versionNumber())

            Thread.sleep(100)

            val book2 = UuidTables.Book.new {
                title = "Kotlin in Action"
                pubCity.set(munich)
            }.flush()
            assertEquals(7, book2.id.value.versionNumber())
            // time-based Uuids are strictly ordered
            assertTrue(book1.id.value < book2.id.value)
        }
    }
}

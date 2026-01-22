package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.MISSING_R2DBC_TEST
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

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
        var city by City referencedOn People.cityId
    }

    object Addresses : UuidTable() {
        val person = reference("person_id", People)
        val city = reference("city_id", Cities)
        val address = varchar("address", 255)
    }

    class Address(id: EntityID<Uuid>) : UuidEntity(id) {
        companion object : UuidEntityClass<Address>(Addresses)

        var person by Person.referencedOn(Addresses.person)
        var city by City.referencedOn(Addresses.city)
        var address by Addresses.address
    }

    object Towns : UuidTable("towns") {
        val cityId: Column<Uuid> = uuid("city_id").references(Cities.id)
    }

    class Town(id: EntityID<Uuid>) : UuidEntity(id) {
        companion object : UuidEntityClass<Town>(Towns)

        var city by City referencedOn Towns.cityId
    }
}

@Tag(MISSING_R2DBC_TEST)
class UuidTableEntityTest : DatabaseTestsBase() {
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
            val mumbai = UuidTables.City.new { name = "Mumbai" }
            val pune = UuidTables.City.new { name = "Pune" }
            UuidTables.Person.new(Uuid.random()) {
                name = "David D'souza"
                city = mumbai
            }
            UuidTables.Person.new(Uuid.random()) {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            UuidTables.Person.new(Uuid.random()) {
                name = "Tanu Arora"
                city = pune
            }

            val allCities = UuidTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = UuidTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test
    fun `update and delete records`() {
        withTables(UuidTables.Cities, UuidTables.People) {
            val mumbai = UuidTables.City.new(Uuid.random()) { name = "Mumbai" }
            val pune = UuidTables.City.new(Uuid.random()) { name = "Pune" }
            UuidTables.Person.new(Uuid.random()) {
                name = "David D'souza"
                city = mumbai
            }
            UuidTables.Person.new(Uuid.random()) {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            val tanu = UuidTables.Person.new(Uuid.random()) {
                name = "Tanu Arora"
                city = pune
            }

            tanu.delete()
            pune.delete()

            val allCities = UuidTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = UuidTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("Tanu Arora", "Pune")))
        }
    }

    @Test
    fun `insert with inner table`() {
        withTables(UuidTables.Addresses, UuidTables.Cities, UuidTables.People) {
            val city1 = UuidTables.City.new {
                name = "city1"
            }
            val person1 = UuidTables.Person.new {
                name = "person1"
                city = city1
            }

            val address1 = UuidTables.Address.new {
                person = person1
                city = city1
                address = "address1"
            }

            val address2 = UuidTables.Address.new {
                person = person1
                city = city1
                address = "address2"
            }

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
            assertEquals(cId, town1.city.id)

            // eager loaded referencedOn
            val town1WithCity = UuidTables.Town.all().with(UuidTables.Town::city).single()
            assertEquals(cId, town1WithCity.city.id)

            // lazy loaded referrersOn
            val city1 = UuidTables.City.all().single()
            val towns = city1.towns
            assertEquals(cId, towns.first().city.id)

            // eager loaded referrersOn
            val city1WithTowns = UuidTables.City.all().with(UuidTables.City::towns).single()
            assertEquals(tId, city1WithTowns.towns.first().id)
        }
    }
}

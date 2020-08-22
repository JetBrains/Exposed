package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import java.util.*

object UUIDTables {
    object Cities : UUIDTable() {
        val name = varchar("name", 50)
    }
    class City(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<City>(Cities)
        var name by Cities.name
    }
    object People : UUIDTable() {
        val name = varchar("name", 80)
        val cityId = reference("city_id", Cities)
    }
    class Person(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<Person>(People)
        var name by People.name
        var city by City referencedOn People.cityId
    }
    object Addresses : UUIDTable() {
        val person = reference("person_id", People)
        val city = reference("city_id", Cities)
        val address = varchar("address", 255)
    }
    class Address(id: EntityID<UUID>) : UUIDEntity(id) {
        companion object : UUIDEntityClass<Address>(Addresses)
        var person by Person.referencedOn(Addresses.person)
        var city by City.referencedOn(Addresses.city)
        var address by Addresses.address
    }

}
class UUIDTableEntityTest : DatabaseTestsBase() {

    @Test fun `create tables`() {
        withTables(UUIDTables.Cities, UUIDTables.People) {
            assertEquals(true, UUIDTables.Cities.exists())
            assertEquals(true, UUIDTables.People.exists())
        }
    }

    @Test fun `create records`() {
        withTables(UUIDTables.Cities, UUIDTables.People) {
            val mumbai = UUIDTables.City.new { name = "Mumbai" }
            val pune = UUIDTables.City.new { name = "Pune" }
            UUIDTables.Person.new(UUID.randomUUID()) {
                name = "David D'souza"
                city = mumbai
            }
            UUIDTables.Person.new(UUID.randomUUID()) {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            UUIDTables.Person.new(UUID.randomUUID()) {
                name = "Tanu Arora"
                city = pune
            }

            val allCities = UUIDTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = UUIDTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test fun `update and delete records`() {
        withTables(UUIDTables.Cities, UUIDTables.People) {
            val mumbai = UUIDTables.City.new(UUID.randomUUID()) { name = "Mumbai" }
            val pune = UUIDTables.City.new(UUID.randomUUID()) { name = "Pune" }
            UUIDTables.Person.new(UUID.randomUUID()) {
                name = "David D'souza"
                city = mumbai
            }
            UUIDTables.Person.new(UUID.randomUUID()) {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            val tanu = UUIDTables.Person.new(UUID.randomUUID()) {
                name = "Tanu Arora"
                city = pune
            }

            tanu.delete()
            pune.delete()

            val allCities = UUIDTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = UUIDTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("Tanu Arora", "Pune")))
        }
    }

    @Test fun `insert with inner table`() {
        withTables(UUIDTables.Addresses, UUIDTables.Cities, UUIDTables.People) {
            val city1 = UUIDTables.City.new {
                name = "city1"
            }
            val person1 = UUIDTables.Person.new {
                name = "person1"
                city = city1
            }

            val address1 = UUIDTables.Address.new {
                person = person1
                city = city1
                address = "address1"
            }

            val address2 = UUIDTables.Address.new {
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
}
package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.UIntEntity
import org.jetbrains.exposed.dao.UIntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UIntIdTable
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class UIntIdTableEntityTest : DatabaseTestsBase() {

    @Test
    fun `create tables`() {
        withTables(UIntIdTables.Cities, UIntIdTables.People) {
            assertEquals(true, UIntIdTables.Cities.exists())
            assertEquals(true, UIntIdTables.People.exists())
        }
    }

    @Test
    fun `create records`() {
        withTables(UIntIdTables.Cities, UIntIdTables.People) {
            val mumbai = UIntIdTables.City.new { name = "Mumbai" }
            val pune = UIntIdTables.City.new { name = "Pune" }
            UIntIdTables.Person.new {
                name = "David D'souza"
                city = mumbai
            }
            UIntIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            UIntIdTables.Person.new {
                name = "Tanu Arora"
                city = pune
            }

            val allCities = UIntIdTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = UIntIdTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test
    fun `update and delete records`() {
        withTables(UIntIdTables.Cities, UIntIdTables.People) {
            val mumbai = UIntIdTables.City.new { name = "Mumbai" }
            val pune = UIntIdTables.City.new { name = "Pune" }
            UIntIdTables.Person.new {
                name = "David D'souza"
                city = mumbai
            }
            UIntIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            val tanu = UIntIdTables.Person.new {
                name = "Tanu Arora"
                city = pune
            }

            tanu.delete()
            pune.delete()

            val allCities = UIntIdTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = UIntIdTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("Tanu Arora", "Pune")))
        }
    }
}

object UIntIdTables {
    object Cities : UIntIdTable() {
        val name = varchar("name", 50)
    }

    class City(id: EntityID<UInt>) : UIntEntity(id) {
        companion object : UIntEntityClass<City>(Cities)

        var name by Cities.name
    }

    object People : UIntIdTable() {
        val name = varchar("name", 80)
        val cityId = reference("city_id", Cities)
    }

    class Person(id: EntityID<UInt>) : UIntEntity(id) {
        companion object : UIntEntityClass<Person>(People)

        var name by People.name
        var city by City referencedOn People.cityId
    }
}

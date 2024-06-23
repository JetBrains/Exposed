package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.ULongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class ULongIdTableEntityTest : DatabaseTestsBase() {

    @Test
    fun `create tables`() {
        withTables(ULongIdTables.Cities, ULongIdTables.People) {
            assertEquals(true, ULongIdTables.Cities.exists())
            assertEquals(true, ULongIdTables.People.exists())
        }
    }

    @Test
    fun `create records`() {
        withTables(ULongIdTables.Cities, ULongIdTables.People) {
            val mumbai = ULongIdTables.City.new { name = "Mumbai" }
            val pune = ULongIdTables.City.new { name = "Pune" }
            ULongIdTables.Person.new {
                name = "David D'souza"
                city = mumbai
            }
            ULongIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            ULongIdTables.Person.new {
                name = "Tanu Arora"
                city = pune
            }

            val allCities = ULongIdTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = ULongIdTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test
    fun `update and delete records`() {
        withTables(ULongIdTables.Cities, ULongIdTables.People) {
            val mumbai = ULongIdTables.City.new { name = "Mumbai" }
            val pune = ULongIdTables.City.new { name = "Pune" }
            ULongIdTables.Person.new {
                name = "David D'souza"
                city = mumbai
            }
            ULongIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            val tanu = ULongIdTables.Person.new {
                name = "Tanu Arora"
                city = pune
            }

            tanu.delete()
            pune.delete()

            val allCities = ULongIdTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = ULongIdTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("Tanu Arora", "Pune")))
        }
    }
}

object ULongIdTables {
    object Cities : ULongIdTable() {
        val name = varchar("name", 50)
    }

    class City(id: EntityID<ULong>) : ULongEntity(id) {
        companion object : ULongEntityClass<City>(Cities)

        var name by Cities.name
    }

    object People : ULongIdTable() {
        val name = varchar("name", 80)
        val cityId = reference("city_id", Cities)
    }

    class Person(id: EntityID<ULong>) : ULongEntity(id) {
        companion object : ULongEntityClass<Person>(People)

        var name by People.name
        var city by City referencedOn People.cityId
    }
}

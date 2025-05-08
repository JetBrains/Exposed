package org.jetbrains.exposed.v1.sql.tests.shared.entities

import org.jetbrains.exposed.v1.dao.UIntEntity
import org.jetbrains.exposed.v1.dao.UIntEntityClass
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.UIntIdTable
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.sql.Column
import org.jetbrains.exposed.v1.sql.exists
import org.jetbrains.exposed.v1.sql.insertAndGetId
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
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

    @Test
    fun testForeignKeyBetweenUIntAndEntityIDColumns() {
        withTables(UIntIdTables.Cities, UIntIdTables.Towns) {
            val cId = UIntIdTables.Cities.insertAndGetId {
                it[name] = "City A"
            }
            val tId = UIntIdTables.Towns.insertAndGetId {
                it[cityId] = cId.value
            }

            // lazy loaded referencedOn
            val town1 = UIntIdTables.Town.all().single()
            assertEquals(cId, town1.city.id)

            // eager loaded referencedOn
            val town1WithCity = UIntIdTables.Town.all().with(UIntIdTables.Town::city).single()
            assertEquals(cId, town1WithCity.city.id)

            // lazy loaded referrersOn
            val city1 = UIntIdTables.City.all().single()
            val towns = city1.towns
            assertEquals(cId, towns.first().city.id)

            // eager loaded referrersOn
            val city1WithTowns = UIntIdTables.City.all().with(UIntIdTables.City::towns).single()
            assertEquals(tId, city1WithTowns.towns.first().id)
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
        val towns by Town referrersOn Towns.cityId
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

    object Towns : UIntIdTable("towns") {
        val cityId: Column<UInt> = uinteger("city_id").references(Cities.id)
    }

    class Town(id: EntityID<UInt>) : UIntEntity(id) {
        companion object : UIntEntityClass<Town>(Towns)

        var city by City referencedOn Towns.cityId
    }
}

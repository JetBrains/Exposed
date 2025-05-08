package org.jetbrains.exposed.v1.sql.tests.shared.entities

import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.sql.Column
import org.jetbrains.exposed.v1.sql.exists
import org.jetbrains.exposed.v1.sql.insertAndGetId
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.junit.Test

object LongIdTables {
    object Cities : LongIdTable() {
        val name = varchar("name", 50)
    }

    class City(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<City>(Cities)

        var name by Cities.name
        val towns by Town referrersOn Towns.cityId
    }

    object People : LongIdTable() {
        val name = varchar("name", 80)
        val cityId = reference("city_id", Cities)
    }

    class Person(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<Person>(People)

        var name by People.name
        var city by City referencedOn People.cityId
    }

    object Towns : LongIdTable("towns") {
        val cityId: Column<Long> = long("city_id").references(Cities.id)
    }

    class Town(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<Town>(Towns)

        var city by City referencedOn Towns.cityId
    }
}

class LongIdTableEntityTest : DatabaseTestsBase() {
    @Test
    fun `create tables`() {
        withTables(LongIdTables.Cities, LongIdTables.People) {
            assertEquals(true, LongIdTables.Cities.exists())
            assertEquals(true, LongIdTables.People.exists())
        }
    }

    @Test
    fun `create records`() {
        withTables(LongIdTables.Cities, LongIdTables.People) {
            val mumbai = LongIdTables.City.new { name = "Mumbai" }
            val pune = LongIdTables.City.new { name = "Pune" }
            LongIdTables.Person.new {
                name = "David D'souza"
                city = mumbai
            }
            LongIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            LongIdTables.Person.new {
                name = "Tanu Arora"
                city = pune
            }

            val allCities = LongIdTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = LongIdTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test
    fun `update and delete records`() {
        withTables(LongIdTables.Cities, LongIdTables.People) {
            val mumbai = LongIdTables.City.new { name = "Mumbai" }
            val pune = LongIdTables.City.new { name = "Pune" }
            LongIdTables.Person.new {
                name = "David D'souza"
                city = mumbai
            }
            LongIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city = mumbai
            }
            val tanu = LongIdTables.Person.new {
                name = "Tanu Arora"
                city = pune
            }

            tanu.delete()
            pune.delete()

            val allCities = LongIdTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = LongIdTables.Person.all().map { Pair(it.name, it.city.name) }
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("Tanu Arora", "Pune")))
        }
    }

    @Test
    fun testForeignKeyBetweenLongAndEntityIDColumns() {
        withTables(LongIdTables.Cities, LongIdTables.Towns) {
            val cId = LongIdTables.Cities.insertAndGetId {
                it[name] = "City A"
            }
            val tId = LongIdTables.Towns.insertAndGetId {
                it[cityId] = cId.value
            }

            // lazy loaded referencedOn
            val town1 = LongIdTables.Town.all().single()
            assertEquals(cId, town1.city.id)

            // eager loaded referencedOn
            val town1WithCity = LongIdTables.Town.all().with(LongIdTables.Town::city).single()
            assertEquals(cId, town1WithCity.city.id)

            // lazy loaded referrersOn
            val city1 = LongIdTables.City.all().single()
            val towns = city1.towns
            assertEquals(cId, towns.first().city.id)

            // eager loaded referrersOn
            val city1WithTowns = LongIdTables.City.all().with(LongIdTables.City::towns).single()
            assertEquals(tId, city1WithTowns.towns.first().id)
        }
    }
}

package org.jetbrains.exposed.v1.sql.tests.shared.entities
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.dao.ULongEntity
import org.jetbrains.exposed.v1.dao.ULongEntityClass
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.insertAndGetId
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
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

    @Test
    fun testForeignKeyBetweenULongAndEntityIDColumns() {
        withTables(ULongIdTables.Cities, ULongIdTables.Towns) {
            val cId = ULongIdTables.Cities.insertAndGetId {
                it[name] = "City A"
            }
            val tId = ULongIdTables.Towns.insertAndGetId {
                it[cityId] = cId.value
            }

            // lazy loaded referencedOn
            val town1 = ULongIdTables.Town.all().single()
            assertEquals(cId, town1.city.id)

            // eager loaded referencedOn
            val town1WithCity = ULongIdTables.Town.all().with(ULongIdTables.Town::city).single()
            assertEquals(cId, town1WithCity.city.id)

            // lazy loaded referrersOn
            val city1 = ULongIdTables.City.all().single()
            val towns = city1.towns
            assertEquals(cId, towns.first().city.id)

            // eager loaded referrersOn
            val city1WithTowns = ULongIdTables.City.all().with(ULongIdTables.City::towns).single()
            assertEquals(tId, city1WithTowns.towns.first().id)
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
        val towns by Town referrersOn Towns.cityId
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

    object Towns : ULongIdTable("towns") {
        val cityId: Column<ULong> = ulong("city_id").references(Cities.id)
    }

    class Town(id: EntityID<ULong>) : ULongEntity(id) {
        companion object : ULongEntityClass<Town>(Towns)

        var city by City referencedOn Towns.cityId
    }
}

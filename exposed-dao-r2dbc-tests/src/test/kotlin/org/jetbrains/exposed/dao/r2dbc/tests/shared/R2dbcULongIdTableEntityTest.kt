package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.ULongEntity
import org.jetbrains.exposed.r2dbc.dao.ULongEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test

class ULongIdTableEntityTest : R2dbcDatabaseTestsBase() {

    @Test
    fun `create tables`() {
        withTables(ULongIdTables.People) {
            assertEquals(true, ULongIdTables.Cities.exists())
            assertEquals(true, ULongIdTables.People.exists())
        }
    }

    @Test
    fun `create records`() {
        withTables(ULongIdTables.People) {
            val mumbai = ULongIdTables.City.new { name = "Mumbai" }.flush()
            val pune = ULongIdTables.City.new { name = "Pune" }.flush()
            ULongIdTables.Person.new {
                name = "David D'souza"
                city.set(mumbai)
            }
            ULongIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city.set(mumbai)
            }
            ULongIdTables.Person.new {
                name = "Tanu Arora"
                city.set(pune)
            }

            val allCities = ULongIdTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = ULongIdTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test
    fun `update and delete records`() {
        withTables(ULongIdTables.People) {
            val mumbai = ULongIdTables.City.new { name = "Mumbai" }.flush()
            val pune = ULongIdTables.City.new { name = "Pune" }.flush()
            ULongIdTables.Person.new {
                name = "David D'souza"
                city.set(mumbai)
            }
            ULongIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city.set(mumbai)
            }
            val tanu = ULongIdTables.Person.new {
                name = "Tanu Arora"
                city.set(pune)
            }.flush()

            tanu.delete()
            pune.delete()

            val allCities = ULongIdTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = ULongIdTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
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
            assertEquals(cId, town1.city().id)

            // eager loaded referencedOn
            val town1WithCity =
                ULongIdTables.Town.all().with(ULongIdTables.Town::city)
                    .single()
            assertEquals(cId, town1WithCity.city().id)

            // lazy loaded referrersOn
            val city1 = ULongIdTables.City.all().single()
            val towns = city1.towns
            assertEquals(cId, towns.first().city().id)

            // eager loaded referrersOn
            val city1WithTowns =
                ULongIdTables.City.all().with(ULongIdTables.City::towns)
                    .single()
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
        val city by City referencedOn People.cityId
    }

    object Towns : ULongIdTable("towns") {
        val cityId: Column<ULong> = ulong("city_id").references(Cities.id)
    }

    class Town(id: EntityID<ULong>) : ULongEntity(id) {
        companion object : ULongEntityClass<Town>(Towns)

        val city by City referencedOn Towns.cityId
    }
}

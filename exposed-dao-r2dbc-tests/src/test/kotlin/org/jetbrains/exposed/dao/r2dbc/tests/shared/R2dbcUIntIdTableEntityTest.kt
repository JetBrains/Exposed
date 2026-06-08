package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.UIntEntity
import org.jetbrains.exposed.r2dbc.dao.UIntEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import kotlin.test.Test

class UIntIdTableEntityTest : R2dbcDatabaseTestsBase() {

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
            val mumbai = UIntIdTables.City.new { name = "Mumbai" }.flush()
            val pune = UIntIdTables.City.new { name = "Pune" }.flush()
            UIntIdTables.Person.new {
                name = "David D'souza"
                city.set(mumbai)
            }
            UIntIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city.set(mumbai)
            }
            UIntIdTables.Person.new {
                name = "Tanu Arora"
                city.set(pune)
            }

            val allCities = UIntIdTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = UIntIdTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test
    fun `update and delete records`() {
        withTables(UIntIdTables.Cities, UIntIdTables.People) {
            val mumbai = UIntIdTables.City.new { name = "Mumbai" }.flush()
            val pune = UIntIdTables.City.new { name = "Pune" }.flush()
            UIntIdTables.Person.new {
                name = "David D'souza"
                city.set(mumbai)
            }
            UIntIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city.set(mumbai)
            }
            val tanu = UIntIdTables.Person.new {
                name = "Tanu Arora"
                city.set(pune)
            }.flush()

            tanu.delete()
            pune.delete()

            val allCities = UIntIdTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = UIntIdTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
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
            assertEquals(cId, town1.city().id)

            // eager loaded referencedOn
            val town1WithCity = UIntIdTables.Town.all().with(UIntIdTables.Town::city).single()
            assertEquals(cId, town1WithCity.city().id)

            // lazy loaded referrersOn
            val city1 = UIntIdTables.City.all().single()
            val towns = city1.towns
            assertEquals(cId, towns.first().city().id)

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
        val city by City referencedOn People.cityId
    }

    object Towns : UIntIdTable("towns") {
        val cityId: Column<UInt> = uinteger("city_id").references(Cities.id)
    }

    class Town(id: EntityID<UInt>) : UIntEntity(id) {
        companion object : UIntEntityClass<Town>(Towns)

        val city by City referencedOn Towns.cityId
    }
}

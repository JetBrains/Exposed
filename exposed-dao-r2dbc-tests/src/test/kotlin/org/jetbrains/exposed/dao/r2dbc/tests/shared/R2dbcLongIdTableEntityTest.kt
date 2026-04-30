package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.LongR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.LongR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import kotlin.test.Test

class R2dbcLongIdTableEntityTest : R2dbcDatabaseTestsBase() {
    object LongIdTables {
        object Cities : LongIdTable() {
            val name = varchar("name", 50)
        }

        class City(id: EntityID<Long>) : LongR2dbcEntity(id) {
            companion object : LongR2dbcEntityClass<City>(Cities)

            var name by Cities.name
            val towns by Town referrersOnSuspend Towns.cityId
        }

        object People : LongIdTable() {
            val name = varchar("name", 80)
            val cityId = reference("city_id", Cities)
        }

        class Person(id: EntityID<Long>) : LongR2dbcEntity(id) {
            companion object : LongR2dbcEntityClass<Person>(People)

            var name by People.name
            val city by City referencedOnSuspend People.cityId
        }

        object Towns : LongIdTable("towns") {
            val cityId: Column<Long> = long("city_id").references(Cities.id)
        }

        class Town(id: EntityID<Long>) : LongR2dbcEntity(id) {
            companion object : LongR2dbcEntityClass<Town>(Towns)

            val city by City referencedOnSuspend Towns.cityId
        }
    }

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
                city set mumbai
            }
            LongIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city set mumbai
            }
            LongIdTables.Person.new {
                name = "Tanu Arora"
                city set pune
            }

            val allCities = LongIdTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = LongIdTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
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
                city set mumbai
            }
            LongIdTables.Person.new {
                name = "Tushar Mumbaikar"
                city set mumbai
            }
            val tanu = LongIdTables.Person.new {
                name = "Tanu Arora"
                city set pune
            }

            tanu.delete()
            pune.delete()

            val allCities = LongIdTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = LongIdTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
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
            assertEquals(cId, town1.city().id)

            // eager loaded referencedOn
            val town1WithCity = LongIdTables.Town.all().with(LongIdTables.Town::city).single()
            assertEquals(cId, town1WithCity.city().id)

            // lazy loaded referrersOn
            val city1 = LongIdTables.City.all().single()
            val towns = city1.towns()
            assertEquals(cId, towns.first().city().id)

            // eager loaded referrersOn
            val city1WithTowns = LongIdTables.City.all().with(LongIdTables.City::towns).single()
            assertEquals(tId, city1WithTowns.towns().first().id)
        }
    }
}

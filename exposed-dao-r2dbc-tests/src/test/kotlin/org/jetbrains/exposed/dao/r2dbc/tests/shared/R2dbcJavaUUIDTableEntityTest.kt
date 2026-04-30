package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.java.UUIDR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.java.UUIDR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import kotlin.test.Test
import java.util.UUID as JavaUUID

class R2dbcJavaUUIDTableEntityTest : R2dbcDatabaseTestsBase() {

    @Suppress("MemberNameEqualsClassName")
    object JavaUUIDTables {
        object Cities : UUIDTable() {
            val name = varchar("name", 50)
        }

        class City(id: EntityID<JavaUUID>) : UUIDR2dbcEntity(id) {
            companion object : UUIDR2dbcEntityClass<City>(Cities, null, null)

            var name by Cities.name
            val towns by Town referrersOnSuspend Towns.cityId
        }

        object People : UUIDTable() {
            val name = varchar("name", 80)
            val cityId = reference("city_id", Cities)
        }

        class Person(id: EntityID<JavaUUID>) : UUIDR2dbcEntity(id) {
            companion object : UUIDR2dbcEntityClass<Person>(People)

            var name by People.name
            val city by City referencedOnSuspend People.cityId
        }

        object Addresses : UUIDTable() {
            val person = reference("person_id", People)
            val city = reference("city_id", Cities)
            val address = varchar("address", 255)
        }

        class Address(id: EntityID<JavaUUID>) : UUIDR2dbcEntity(id) {
            companion object : UUIDR2dbcEntityClass<Address>(Addresses)

            val person by Person.referencedOnSuspend(Addresses.person)
            val city by City.referencedOnSuspend(Addresses.city)
            var address by Addresses.address
        }

        object Towns : UUIDTable("towns") {
            val cityId: Column<JavaUUID> = javaUUID("city_id").references(Cities.id)
        }

        class Town(id: EntityID<JavaUUID>) : UUIDR2dbcEntity(id) {
            companion object : UUIDR2dbcEntityClass<Town>(Towns)

            val city by City referencedOnSuspend Towns.cityId
        }
    }

    @Test
    fun `create tables`() {
        withTables(JavaUUIDTables.Cities, JavaUUIDTables.People) {
            assertEquals(true, JavaUUIDTables.Cities.exists())
            assertEquals(true, JavaUUIDTables.People.exists())
        }
    }

    @Test
    fun `create records`() {
        withTables(JavaUUIDTables.Cities, JavaUUIDTables.People) {
            val mumbai = JavaUUIDTables.City.new { name = "Mumbai" }
            val pune = JavaUUIDTables.City.new { name = "Pune" }
            JavaUUIDTables.Person.new(JavaUUID.randomUUID()) {
                name = "David D'souza"
                city set mumbai
            }
            JavaUUIDTables.Person.new(JavaUUID.randomUUID()) {
                name = "Tushar Mumbaikar"
                city set mumbai
            }
            JavaUUIDTables.Person.new(JavaUUID.randomUUID()) {
                name = "Tanu Arora"
                city set pune
            }

            val allCities = JavaUUIDTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(true, allCities.contains<String>("Pune"))
            assertEquals(false, allCities.contains<String>("Chennai"))

            val allPeople = JavaUUIDTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("David D'souza", "Pune")))
        }
    }

    @Test
    fun `update and delete records`() {
        withTables(JavaUUIDTables.Cities, JavaUUIDTables.People) {
            val mumbai = JavaUUIDTables.City.new(JavaUUID.randomUUID()) { name = "Mumbai" }
            val pune = JavaUUIDTables.City.new(JavaUUID.randomUUID()) { name = "Pune" }
            JavaUUIDTables.Person.new(JavaUUID.randomUUID()) {
                name = "David D'souza"
                city set mumbai
            }
            JavaUUIDTables.Person.new(JavaUUID.randomUUID()) {
                name = "Tushar Mumbaikar"
                city set mumbai
            }
            val tanu = JavaUUIDTables.Person.new(JavaUUID.randomUUID()) {
                name = "Tanu Arora"
                city set pune
            }

            tanu.delete()
            pune.delete()

            val allCities = JavaUUIDTables.City.all().map { it.name }.toList()
            assertEquals(true, allCities.contains<String>("Mumbai"))
            assertEquals(false, allCities.contains<String>("Pune"))

            val allPeople = JavaUUIDTables.Person.all().map { Pair(it.name, it.city().name) }.toList()
            assertEquals(true, allPeople.contains(Pair("David D'souza", "Mumbai")))
            assertEquals(false, allPeople.contains(Pair("Tanu Arora", "Pune")))
        }
    }

    @Test
    fun `insert with inner table`() {
        withTables(JavaUUIDTables.Addresses, JavaUUIDTables.Cities, JavaUUIDTables.People) {
            val city1 = JavaUUIDTables.City.new {
                name = "city1"
            }
            val person1 = JavaUUIDTables.Person.new {
                name = "person1"
                city set city1
            }

            val address1 = JavaUUIDTables.Address.new {
                person set person1
                city set city1
                address = "address1"
            }

            val address2 = JavaUUIDTables.Address.new {
                person set person1
                city set city1
                address = "address2"
            }

            address1.refresh(flush = true)
            assertEquals("address1", address1.address)

            address2.refresh(flush = true)
            assertEquals("address2", address2.address)
        }
    }

    @Test
    fun testForeignKeyBetweenUUIDAndEntityIDColumns() {
        withTables(JavaUUIDTables.Cities, JavaUUIDTables.Towns) {
            val cId = JavaUUIDTables.Cities.insertAndGetId {
                it[name] = "City A"
            }
            val tId = JavaUUIDTables.Towns.insertAndGetId {
                it[cityId] = cId.value
            }

            // lazy loaded referencedOn
            val town1 = JavaUUIDTables.Town.all().single()
            assertEquals(cId, town1.city().id)

            // eager loaded referencedOn
            val town1WithCity = JavaUUIDTables.Town.all().with(JavaUUIDTables.Town::city).single()
            assertEquals(cId, town1WithCity.city().id)

            // lazy loaded referrersOn
            val city1 = JavaUUIDTables.City.all().single()
            val towns = city1.towns()
            assertEquals(cId, towns.first().city().id)

            // eager loaded referrersOn
            val city1WithTowns = JavaUUIDTables.City.all().with(JavaUUIDTables.City::towns).single()
            assertEquals(tId, city1WithTowns.towns().first().id)
        }
    }
}

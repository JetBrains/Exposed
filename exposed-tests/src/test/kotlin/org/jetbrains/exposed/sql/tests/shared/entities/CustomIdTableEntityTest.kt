package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.entities.UUIDTables.Cities
import org.jetbrains.exposed.sql.tests.shared.entities.UUIDTables.City.Companion.referrersOn
import org.jetbrains.exposed.sql.tests.shared.entities.UUIDTables.Town
import org.jetbrains.exposed.sql.tests.shared.entities.UUIDTables.Towns
import org.junit.Test
import java.sql.ResultSet
import java.util.*
import kotlin.test.assertNotNull

data class CityId(
    val value: String
) : Comparable<CityId> {
    override fun toString() = value
    override fun compareTo(other: CityId) = value.compareTo(other.value)
}

fun Table.cityId(name: String): Column<CityId> = registerColumn(name, CityIdColumnType())

class CityIdColumnType : ColumnType<CityId>() {
    private val varcharColumnType = VarCharColumnType(10)

    override fun sqlType() = varcharColumnType.sqlType()

    override fun valueFromDB(value: Any): CityId = varcharColumnType.valueFromDB(value).let { CityId(it) }

    override fun nonNullValueToString(value: CityId) = varcharColumnType.nonNullValueToString(value.value)

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?): Unit =
        varcharColumnType.setParameter(stmt, index, value?.toString())

    override fun readObject(rs: ResultSet, index: Int) = varcharColumnType.readObject(rs, index)
}


@Suppress("MemberNameEqualsClassName")
object CustomIdTables {
    object Cities : IdTable<CityId>() {
        override val id = cityId("id").entityId()
        override val primaryKey = PrimaryKey(id)

        val name = varchar("name", 50)
    }

    class City(id: EntityID<CityId>) : Entity<CityId>(id) {
        companion object : EntityClass<CityId, City>(Cities)

        var name by Cities.name
    }
}

class CustomIdTableEntityTest : DatabaseTestsBase() {
    @Test
    fun `create tables`() {
        withTables(CustomIdTables.Cities) {
            assertEquals(true, CustomIdTables.Cities.exists())
        }
    }

    @Test
    fun `create records`() {
        withTables(CustomIdTables.Cities) {
            CustomIdTables.City.new(CityId("M")) { name = "Mumbai" }

            val allCities = CustomIdTables.City.all().map { it.name }
            assertEquals(true, allCities.contains<String>("Mumbai"))
        }
    }

    @Test
    fun `find by id`() {
        withTables(CustomIdTables.Cities) {
            CustomIdTables.City.new(CityId("M")) { name = "Mumbai" }

            val city = CustomIdTables.City.findById(CityId("M"))

            assertNotNull(city)
            assertEquals("Mumbai", city.name)
        }
    }

}

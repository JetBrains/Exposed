package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData.Users.default
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData.Users.nullable
import org.jetbrains.exposed.sql.tests.shared.entities.SortByReferenceTest
import java.util.*

fun munichId() = DMLTestsData.Cities
    .select { DMLTestsData.Cities.name eq "Munich" }
    .first()[DMLTestsData.Cities.id]

object DMLTestsData {
    object Cities : Table() {
        val id: Column<Int> = integer("cityId").autoIncrement()
        val name: Column<String> = varchar("name", 50)
        override val primaryKey = PrimaryKey(id)
    }

    object Users : Table() {
        val id: Column<String> = varchar("id", 10)
        val name: Column<String> = varchar("name", length = 50)
        val cityId: Column<Int?> = reference("city_id", Cities.id).nullable()
        val flags: Column<Int> = integer("flags").default(0)
        override val primaryKey = PrimaryKey(id)

        object Flags {
            const val IS_ADMIN = 0b1
            const val HAS_DATA = 0b1000
        }
    }

    object ScopedUsers : Table() {
        val id: Column<String> = varchar("id", 10)
        val name: Column<String> = varchar("name", length = 50)
        val cityId: Column<Int?> = reference("city_id", Cities.id).nullable()
        val flags: Column<Int> = integer("flags").default(0)
        override val primaryKey = PrimaryKey(id)
        override val defaultScope = { cityId eq munichId() }

        object Flags {
            const val IS_ADMIN = 0b1
            const val HAS_DATA = 0b1000
        }
    }

    object UserData : Table() {
        val user_id: Column<String> = reference("user_id", Users.id)
        val comment: Column<String> = varchar("comment", 30)
        val value: Column<Int> = integer("value")
    }

    object ScopedUserData : Table() {
        val user_id: Column<String> = reference("user_id", ScopedUsers.id)
        val comment: Column<String> = varchar("comment", 30)
        val value: Column<Int> = integer("value")
        override val defaultScope = { user_id eq "sergey" }
    }
}

@Suppress("LongMethod")
fun DatabaseTestsBase.withCitiesAndUsers(
    exclude: List<TestDB> = emptyList(),
    statement: Transaction.(cities: DMLTestsData.Cities,
                            users: DMLTestsData.Users,
                            userData: DMLTestsData.UserData,
                            scopedUsers: DMLTestsData.ScopedUsers,
                            scopedUserData: DMLTestsData.ScopedUserData) -> Unit
) {
    val Users = DMLTestsData.Users
    val UserFlags = DMLTestsData.Users.Flags
    val Cities = DMLTestsData.Cities
    val UserData = DMLTestsData.UserData
    val ScopedUsers = DMLTestsData.ScopedUsers
    val ScopedUserFlags = DMLTestsData.ScopedUsers.Flags
    val ScopedUserData = DMLTestsData.ScopedUserData

    withTables(exclude, Cities, Users, UserData, ScopedUsers, ScopedUserData) {
        val saintPetersburgId = Cities.insert {
            it[name] = "St. Petersburg"
        } get Cities.id

        val munichId = Cities.insert {
            it[name] = "Munich"
        } get Cities.id

        Cities.insert {
            it[name] = "Prague"
        }

        Users.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[cityId] = saintPetersburgId
            it[flags] = UserFlags.IS_ADMIN
        }

        ScopedUsers.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[cityId] = saintPetersburgId
            it[flags] = ScopedUserFlags.IS_ADMIN
        }

        Users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[cityId] = munichId
            it[flags] = UserFlags.IS_ADMIN or UserFlags.HAS_DATA
        }

        ScopedUsers.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[cityId] = munichId
            it[flags] = ScopedUserFlags.IS_ADMIN or ScopedUserFlags.HAS_DATA
        }

        Users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
            it[flags] = UserFlags.HAS_DATA
        }

        ScopedUsers.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
            it[flags] = ScopedUserFlags.HAS_DATA
        }

        Users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[cityId] = null
        }

        ScopedUsers.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[cityId] = null
        }

        Users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[cityId] = null
            it[flags] = UserFlags.HAS_DATA
        }

        ScopedUsers.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[cityId] = null
            it[flags] = ScopedUserFlags.HAS_DATA
        }

        UserData.insert {
            it[user_id] = "smth"
            it[comment] = "Something is here"
            it[value] = 10
        }

        ScopedUserData.insert {
            it[user_id] = "smth"
            it[comment] = "Something is here"
            it[value] = 10
        }

        UserData.insert {
            it[user_id] = "smth"
            it[comment] = "Comment #2"
            it[value] = 20
        }

        ScopedUserData.insert {
            it[user_id] = "smth"
            it[comment] =  "Comment #2"
            it[value] = 20
        }

        UserData.insert {
            it[user_id] = "eugene"
            it[comment] = "Comment for Eugene"
            it[value] = 20
        }

        ScopedUserData.insert {
            it[user_id] = "eugene"
            it[comment] =  "Comment for Eugene"
            it[value] = 20
        }

        UserData.insert {
            it[user_id] = "sergey"
            it[comment] = "Comment for Sergey"
            it[value] = 30
        }

        ScopedUserData.insert {
            it[user_id] = "sergey"
            it[comment] =  "Comment for Sergey"
            it[value] = 30
        }

        statement(Cities, Users, UserData, ScopedUsers, ScopedUserData)
    }
}

object OrgMemberships : IntIdTable() {
    val orgId = reference("org", Orgs.uid)
}

class OrgMembership(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<OrgMembership>(OrgMemberships)

    val orgId by OrgMemberships.orgId
    var org by Org referencedOn OrgMemberships.orgId
}

object Orgs : IntIdTable() {
    val uid = varchar("uid", 36).uniqueIndex().clientDefault { UUID.randomUUID().toString() }
    val name = varchar("name", 256)
}

class Org(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Org>(Orgs)

    var uid by Orgs.uid
    var name by Orgs.name
}

internal fun Iterable<ResultRow>.toCityNameList(): List<String> = map { it[DMLTestsData.Cities.name] }

package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import java.util.*

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
        override val primaryKey = PrimaryKey(id)
    }

    object UserData : Table() {
        val user_id: Column<String> = reference("user_id", Users.id)
        val comment: Column<String> = varchar("comment", 30)
        val value: Column<Int> = integer("value")
    }
}

fun DatabaseTestsBase.withCitiesAndUsers(exclude: List<TestDB> = emptyList(), statement: Transaction.(cities: DMLTestsData.Cities, users: DMLTestsData.Users, userData: DMLTestsData.UserData) -> Unit) {
    val Users = DMLTestsData.Users
    val Cities = DMLTestsData.Cities
    val UserData = DMLTestsData.UserData

    withTables(exclude, Cities, Users, UserData) {
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
        }

        Users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[cityId] = munichId
        }

        Users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
        }

        Users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[cityId] = null
        }

        Users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[cityId] = null
        }

        UserData.insert {
            it[user_id] = "smth"
            it[comment] = "Something is here"
            it[value] = 10
        }

        UserData.insert {
            it[user_id] = "smth"
            it[comment] = "Comment #2"
            it[value] = 20
        }

        UserData.insert {
            it[user_id] = "eugene"
            it[comment] = "Comment for Eugene"
            it[value] = 20
        }

        UserData.insert {
            it[user_id] = "sergey"
            it[comment] = "Comment for Sergey"
            it[value] = 30
        }

        statement(Cities, Users, UserData)
    }
}

fun DatabaseTestsBase.withCitiesAndUsersInSchema(exclude: List<TestDB> = emptyList(), schema: Schema, statement: Transaction.(cities: DMLTestsData.Cities, users: DMLTestsData.Users, userData: DMLTestsData.UserData) -> Unit) {
    val Cities = DMLTestsData.Cities.withSchema(schema)
    val Users = DMLTestsData.Users.withSchema(schema, DMLTestsData.Users.cityId to Cities)
    val UserData = DMLTestsData.UserData

    withSchemas(exclude, schema) {
        try {
            SchemaUtils.create(Cities, Users, UserData)
            val saintPetersburgId = Cities.insert {
                it[DMLTestsData.Cities.name] = "St. Petersburg"
            } get DMLTestsData.Cities.id

            val munichId = Cities.insert {
                it[DMLTestsData.Cities.name] = "Munich"
            } get DMLTestsData.Cities.id

            Cities.insert {
                it[DMLTestsData.Cities.name] = "Prague"
            }

            Users.insert {
                it[DMLTestsData.Users.id] = "andrey"
                it[DMLTestsData.Users.name] = "Andrey"
                it[DMLTestsData.Users.cityId] = saintPetersburgId
            }

            Users.insert {
                it[DMLTestsData.Users.id] = "sergey"
                it[DMLTestsData.Users.name] = "Sergey"
                it[DMLTestsData.Users.cityId] = munichId
            }

            Users.insert {
                it[DMLTestsData.Users.id] = "eugene"
                it[DMLTestsData.Users.name] = "Eugene"
                it[DMLTestsData.Users.cityId] = munichId
            }

            Users.insert {
                it[DMLTestsData.Users.id] = "alex"
                it[DMLTestsData.Users.name] = "Alex"
                it[DMLTestsData.Users.cityId] = null
            }

            Users.insert {
                it[DMLTestsData.Users.id] = "smth"
                it[DMLTestsData.Users.name] = "Something"
                it[DMLTestsData.Users.cityId] = null
            }

            UserData.insert {
                it[user_id] = "smth"
                it[comment] = "Something is here"
                it[value] = 10
            }

            UserData.insert {
                it[user_id] = "smth"
                it[comment] = "Comment #2"
                it[value] = 20
            }

            UserData.insert {
                it[user_id] = "eugene"
                it[comment] = "Comment for Eugene"
                it[value] = 20
            }

            UserData.insert {
                it[user_id] = "sergey"
                it[comment] = "Comment for Sergey"
                it[value] = 30
            }

            UserData.insert {
                it[user_id] = "hichem"
                it[comment] = "Comment for Hichem"
                it[value] = 40
            }

            statement(DMLTestsData.Cities, DMLTestsData.Users, UserData)
        } finally {
            SchemaUtils.drop(Cities, Users, UserData)
        }
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

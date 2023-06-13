package org.jetbrains.exposed.sql.tests.shared.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class JsonColumnTypeTests : DatabaseTestsBase() {
    @Test
    fun testInsertAndSelect() {
        withDb {
            addLogger(StdOutSqlLogger)
            println(currentDialectTest.name)
            try {
                SchemaUtils.create(JsonTable)
                val user1 = User("Admin", null)
                val instance1 = DataHolder(user1, 10, true, null)
                val id1 = JsonTable.insertAndGetId {
                    it[jsonColumn] = instance1
                }

                val result = JsonTable.select { JsonTable.id eq id1 }.singleOrNull()
                assertEquals(instance1, result?.get(JsonTable.jsonColumn))
            } finally {
                SchemaUtils.drop(JsonTable)
            }
        }
    }

    @Test
    fun testUpdate() {
        withDb {
            addLogger(StdOutSqlLogger)
            println(currentDialectTest.name)
            try {
                SchemaUtils.create(JsonTable)
                val user1 = User("Admin", null)
                val instance1 = DataHolder(user1, 10, true, null)
                JsonTable.insert {
                    it[jsonColumn] = instance1
                }

                assertEquals(instance1, JsonTable.selectAll().single()[JsonTable.jsonColumn])

                val instance2 = instance1.copy(active = false)
                JsonTable.update {
                    it[jsonColumn] = instance2
                }

                assertEquals(instance2, JsonTable.selectAll().single()[JsonTable.jsonColumn])
            } finally {
                SchemaUtils.drop(JsonTable)
            }
        }
    }

    @Test
    fun testSelectWithSlice() {
        withDb {
            addLogger(StdOutSqlLogger)
            println(currentDialectTest.name)
            try {
                SchemaUtils.create(JsonTable)
                val user1 = User("Admin", null)
                val instance1 = DataHolder(user1, 10, true, null)
                val id1 = JsonTable.insertAndGetId {
                    it[jsonColumn] = instance1
                }

                //val isActive = JsonTable.jsonColumn.jsonColumnPath<Boolean>("active").alias("is_active")
                //val result = JsonTable.slice(isActive).selectAll().singleOrNull()
                //assertEquals(instance1.active, result?.get(isActive))
            } finally {
                SchemaUtils.drop(JsonTable)
            }
        }
    }

    // test containment
    @Test
    fun testSelectWhereJsonContains() {
        withDb {
            addLogger(StdOutSqlLogger)
            println(currentDialectTest.name)
            try {
                SchemaUtils.create(JsonTable)
                val user1 = User("Admin", null)
                val instance1 = DataHolder(user1, 10, true, null)
                val id1 = JsonTable.insertAndGetId {
                    it[jsonColumn] = instance1
                }
                JsonTable.jsonColumn

                //val result = JsonBTable.select { JsonBTable.jsonBColumn regexp "\"active\":true" }.singleOrNull()
                //assertEquals(id1, result?.get(JsonBTable.id))
            } finally {
                SchemaUtils.drop(JsonTable)
            }
        }
    }

    private object JsonTable : IntIdTable("json_table") {
        val jsonColumn = json("json_column", Json, DataHolder.serializer())
    }

    @Serializable
    private data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)
    @Serializable
    private data class User(val name: String, val team: String?)
}

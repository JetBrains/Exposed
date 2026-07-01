package org.jetbrains.exposed.dao.r2dbc.tests.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb

object JsonTestsData {
    object JsonTable : IntIdTable("j_table") {
        val jsonColumn = json<DataHolder>("j_column", Json.Default)
    }

    object JsonBTable : IntIdTable("j_b_table") {
        val jsonBColumn = jsonb<DataHolder>("j_b_column", Json.Default)
    }

    class JsonEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<JsonEntity>(JsonTable)

        var jsonColumn by JsonTable.jsonColumn
    }

    class JsonBEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<JsonBEntity>(JsonBTable)

        var jsonBColumn by JsonBTable.jsonBColumn
    }
}

@Serializable
data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)

@Serializable
data class User(val name: String, val team: String?)

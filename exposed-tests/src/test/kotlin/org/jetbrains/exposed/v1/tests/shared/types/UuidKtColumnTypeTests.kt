package org.jetbrains.exposed.v1.tests.shared.types

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UuidKtColumnTypeTests : DatabaseTestsBase() {
    @Test
    fun insertReadUuid() {
        val tester = object : Table("test_uuid") {
            val id = uuidKt("id")
        }
        withTables(tester, configure = { sqlLogger = StdOutSqlLogger }) {
            val uuid = Uuid.parse("c128770b-e802-40ba-a85a-58592c80ba58")
            tester.insert {
                it[id] = uuid
            }
            val dbUuid = tester.selectAll().first()[tester.id]
            assertEquals(uuid, dbUuid)
        }
    }
}

package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.types

import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class UUIDColumnTypeTests : R2dbcDatabaseTestsBase() {
    @Test
    fun insertReadUUID() {
        val tester = object : Table("test_uuid") {
            val id = uuid("id")
        }
        withTables(tester, configure = { sqlLogger = StdOutSqlLogger }) {
            val uuid = UUID.fromString("c128770b-e802-40ba-a85a-58592c80ba58")
            tester.insert {
                it[id] = uuid
            }
            val dbUuid = tester.selectAll().first()[tester.id]
            assertEquals(uuid, dbUuid)
        }
    }

    @Test
    fun mariadbOwnUUIDType() {
        val tester = object : Table("test_uuid") {
            val id = uuid("id")
        }
        withDb(excludeSettings = TestDB.ALL - TestDB.ALL_MARIADB, configure = { sqlLogger = StdOutSqlLogger }) {
            try {
                // From the version 10.7 MariaDB has own 'UUID' column type, that does not work with the current column type.
                // Even if we generate on DDL type 'BINARY(16)' we could support native UUID for IO operations.
                exec("CREATE TABLE test_uuid (id UUID NOT NULL)")

                val uuid = UUID.fromString("c128770b-e802-40ba-a85a-58592c80ba58")
                tester.insert {
                    it[id] = uuid
                }
                val dbUuid = tester.selectAll().first()[tester.id]
                assertEquals(uuid, dbUuid)
            } finally {
                exec("DROP TABLE test_uuid")
            }
        }
    }
}

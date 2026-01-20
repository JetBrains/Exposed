package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.types

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import java.util.UUID as JavaUUID

class JavaUUIDColumnTypeTests : R2dbcDatabaseTestsBase() {
    @Test
    fun insertReadUUID() {
        val tester = object : Table("test_uuid") {
            val id = javaUUID("id")
        }
        withTables(tester, configure = { sqlLogger = StdOutSqlLogger }) {
            val uuid = JavaUUID.fromString("c128770b-e802-40ba-a85a-58592c80ba58")
            tester.insert {
                it[id] = uuid
            }
            val dbUuid = tester.selectAll().first()[tester.id]
            assertEquals(uuid, dbUuid)
        }
    }

    @Test
    fun testUUIDColumnType() {
        val node = object : IntIdTable("node") {
            val uuid = javaUUID("java_uuid")
        }

        withTables(node) {
            val key: JavaUUID = JavaUUID.randomUUID()
            val id = node.insertAndGetId { it[uuid] = key }
            assertNotNull(id)
            val uidById = node.selectAll().where { node.id eq id }.singleOrNull()?.get(node.uuid)
            assertEquals(key, uidById)
            val uidByKey = node.selectAll().where { node.uuid eq key }.singleOrNull()?.get(node.uuid)
            assertEquals(key, uidByKey)
        }
    }

    @Test
    fun mariadbOwnUUIDType() {
        val tester = object : Table("test_java_uuid") {
            val id = javaUUID("id")
        }
        withDb(excludeSettings = TestDB.ALL - TestDB.MARIADB, configure = { sqlLogger = StdOutSqlLogger }) {
            try {
                // From the version 10.7 MariaDB has own 'UUID' column type, that does not work with the current column type.
                // Even if we generate on DDL type 'BINARY(16)' we could support native UUID for IO operations.
                exec("CREATE TABLE test_java_uuid (id UUID NOT NULL)")

                val uuid = JavaUUID.fromString("c128770b-e802-40ba-a85a-58592c80ba58")
                tester.insert {
                    it[id] = uuid
                }
                val dbUuid = tester.selectAll().first()[tester.id]
                assertEquals(uuid, dbUuid)
            } finally {
                exec("DROP TABLE IF EXISTS test_java_uuid")
            }
        }
    }
}

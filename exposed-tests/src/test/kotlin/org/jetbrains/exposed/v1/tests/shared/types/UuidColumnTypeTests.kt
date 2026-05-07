package org.jetbrains.exposed.v1.tests.shared.types

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.insertAndWait
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertFalse
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.versionNumber
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class UuidColumnTypeTests : DatabaseTestsBase() {
    @Test
    fun insertReadUuid() {
        val tester = object : Table("test_uuid") {
            val id = uuid("id")
        }
        withTables(tester, configure = { sqlLogger = StdOutSqlLogger }) {
            val uuid = Uuid.parseHexDash("c128770b-e802-40ba-a85a-58592c80ba58")
            tester.insert {
                it[id] = uuid
            }
            val dbUuid = tester.selectAll().first()[tester.id]
            assertEquals(uuid, dbUuid)
        }
    }

    @Test
    fun testUuidColumnType() {
        val node = object : IntIdTable("node") {
            val uuid = uuid("uuid")
        }

        withTables(node) {
            val key: Uuid = Uuid.random()
            val id = node.insertAndGetId { it[uuid] = key }
            assertNotNull(id)
            val uidById = node.selectAll().where { node.id eq id }.singleOrNull()?.get(node.uuid)
            assertEquals(key, uidById)
            val uidByKey = node.selectAll().where { node.uuid eq key }.singleOrNull()?.get(node.uuid)
            assertEquals(key, uidByKey)
        }
    }

    @Test
    fun testNullableUuidColumnTypeExplicitInsert() {
        val node = object : IntIdTable("node") {
            val uuid = uuid("uuid").nullable()
        }

        withTables(node) {
            val id = node.insertAndGetId { it[uuid] = null }
            assertNotNull(id)
            val uidById = node.selectAll().where { node.id eq id }.singleOrNull()?.get(node.uuid)
            assertEquals(null, uidById)
            val uidByKey = node.selectAll().where { node.uuid.isNull() }.singleOrNull()?.get(node.uuid)
            assertEquals(null, uidByKey)
        }
    }

    @Test
    fun mariadbOwnUuidType() {
        val tester = object : Table("test_uuid") {
            val id = uuid("id")
        }
        withDb(excludeSettings = TestDB.ALL - TestDB.MARIADB, configure = { sqlLogger = StdOutSqlLogger }) {
            try {
                // From the version 10.7 MariaDB has own 'UUID' column type, that does not work with the current column type.
                // Even if we generate on DDL type 'BINARY(16)' we could support native UUID for IO operations.
                exec("CREATE TABLE test_uuid (id UUID NOT NULL)")

                val uuid = Uuid.parseHexDash("c128770b-e802-40ba-a85a-58592c80ba58")
                tester.insert {
                    it[id] = uuid
                }
                val dbUuid = tester.selectAll().first()[tester.id]
                assertEquals(uuid, dbUuid)
            } finally {
                exec("DROP TABLE IF EXISTS test_uuid")
            }
        }
    }

    @Test
    fun testAutoGenerateUuidVersions() {
        val tester = object : Table("tester_uuid") {
            val idV4 = uuid("id_v4").autoGenerate()
            val idV7 = uuid("id_v7").autoGenerate(UuidVersion.V7)
        }

        withTables(tester) {
            tester.insertAndWait(100L)
            val (firstDbUuidV4, firstDbUuidV7) = tester.selectAll().map { it[tester.idV4] to it[tester.idV7] }.single()
            assertFalse(firstDbUuidV4 == firstDbUuidV7)
            assertEquals(4, firstDbUuidV4.versionNumber())
            assertEquals(7, firstDbUuidV7.versionNumber())

            val secondClientUuidV4 = tester.insert { } get tester.idV4
            val (secondDbUuidV4, secondDbUuidV7) = tester.selectAll()
                .where { tester.idV4 eq secondClientUuidV4 }
                .map { it[tester.idV4] to it[tester.idV7] }
                .single()
            assertTrue(secondClientUuidV4 == secondDbUuidV4)
            assertFalse(secondDbUuidV4 == secondDbUuidV7)
            // time-based Uuids are strictly ordered
            assertTrue(firstDbUuidV7 < secondDbUuidV7)
        }
    }
}

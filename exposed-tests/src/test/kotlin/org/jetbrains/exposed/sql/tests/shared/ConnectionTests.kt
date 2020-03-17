package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.vendors.ColumnMetadata
import org.junit.Test
import java.sql.Types

class ConnectionTests : DatabaseTestsBase() {

    object People : LongIdTable() {
        val name = varchar("name", 80).nullable()
    }

    @Test
    fun testGettingColumnMetadata() {
        withDb (TestDB.H2){
            SchemaUtils.create(People)

            val columnMetadata = connection.metadata {
                requireNotNull(columns(People)[People])
            }.toSet()
            val expected = setOf(
                    ColumnMetadata("ID", Types.BIGINT, false, 19),
                    ColumnMetadata("NAME", Types.VARCHAR, true, 80)
            )
            assertEquals(expected, columnMetadata)
        }
    }

    // GitHub issue #838
    @Test
    @Suppress("unused")
    fun testTableConstraints() {
        val parent = object : LongIdTable("parent") {
            val scale = integer("scale").uniqueIndex()
        }
        val child = object : LongIdTable("child") {
            val scale = reference("scale", parent.scale)
        }
        withTables(listOf(TestDB.MYSQL), child) {
            val constraints = connection.metadata {
                tableConstraints(listOf(child))
            }
            assertEquals(2, constraints.keys.size)
        }
    }
}

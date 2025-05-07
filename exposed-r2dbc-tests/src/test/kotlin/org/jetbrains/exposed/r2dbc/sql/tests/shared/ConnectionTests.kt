package org.jetbrains.exposed.r2dbc.sql.tests.shared

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.sql.vendors.ColumnMetadata
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.junit.Test
import java.sql.Types

class ConnectionTests : R2dbcDatabaseTestsBase() {

    object People : LongIdTable() {
        val firstName = varchar("firstname", 80).nullable()
        val lastName = varchar("lastname", 42).default("Doe")
        val age = integer("age").default(18)
    }

    @Test
    fun testGettingColumnMetadata() {
        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2, People) {
            val columnMetadata = connection.metadata {
                requireNotNull(columns(People)[People])
            }.toSet()
            val expected = when ((db.dialect as H2Dialect).isSecondVersion) {
                false -> setOf(
                    ColumnMetadata("ID", Types.BIGINT, false, 19, null, true, null),
                    ColumnMetadata("FIRSTNAME", Types.VARCHAR, true, 80, null, false, null),
                    ColumnMetadata("LASTNAME", Types.VARCHAR, false, 42, null, false, "Doe"),
                    ColumnMetadata("AGE", Types.INTEGER, false, 10, null, false, "18"),
                )
                true -> setOf(
                    ColumnMetadata("ID", Types.BIGINT, false, 64, null, true, null),
                    ColumnMetadata("FIRSTNAME", Types.VARCHAR, true, 80, null, false, null),
                    ColumnMetadata("LASTNAME", Types.VARCHAR, false, 42, null, false, "Doe"),
                    ColumnMetadata("AGE", Types.INTEGER, false, 32, null, false, "18"),
                )
            }
            assertEquals(expected, columnMetadata)
        }
    }

    @Test
    fun testTableConstraintsWithFKColumnsThatNeedQuoting() {
        val parent = object : LongIdTable("parent") {
            val scale = integer("scale").uniqueIndex()
        }
        val child = object : LongIdTable("child") {
            val scale = reference("scale", parent.scale)
        }

        withTables(child, parent) { testDb ->
            val constraints = connection.metadata {
                tableConstraints(listOf(child))
            }
            // tableConstraints() returns entries for all tables involved in the FK (parent + child)
            assertEquals(2, constraints.keys.size)

            // EXPOSED-711 https://youtrack.jetbrains.com/issue/EXPOSED-711/Oracle-tableConstraints-columnContraints-dont-return-foreign-keys
            // but only child entry has a non-empty list of FKs
            if (testDb != TestDB.ORACLE) {
                assertEquals(
                    1,
                    constraints.values.count { fks ->
                        fks.any { it.fkName == child.scale.foreignKey?.fkName }
                    }
                )
            }
        }
    }
}

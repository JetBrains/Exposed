package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.TestDB
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
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_H2_V2, People) {
            val columnMetadata = connection.metadata {
                requireNotNull(columns(People)[People])
            }.toSet()

            val h2Dialect = (db.dialect as H2Dialect)
            val idType = "BIGINT"
            val firstNameType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "VARCHAR2(80)" else "VARCHAR(80)"
            val lastNameType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "VARCHAR2(42)" else "VARCHAR(42)"
            val ageType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "INTEGER" else "INT"

            val expected = setOf(
                ColumnMetadata(People.id.nameInDatabaseCase(), Types.BIGINT, idType, false, 64, null, h2Dialect.h2Mode != H2Dialect.H2CompatibilityMode.Oracle, null),
                ColumnMetadata(People.firstName.nameInDatabaseCase(), Types.VARCHAR, firstNameType, true, 80, null, false, null),
                ColumnMetadata(People.lastName.nameInDatabaseCase(), Types.VARCHAR, lastNameType, false, 42, null, false, "Doe"),
                ColumnMetadata(People.age.nameInDatabaseCase(), Types.INTEGER, ageType, false, 32, null, false, "18"),
            )

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

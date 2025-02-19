package org.jetbrains.exposed.sql.tests.shared.ddl

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.r2dbc.sql.exists
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test

class SchemaTests : R2dbcDatabaseTestsBase() {
    @Test
    fun `create and set schema in mysql`() = runTest {
        withDb(TestDB.ALL_MYSQL_MARIADB) {
            val schema = Schema("MYSCHEMA")
            try {
                SchemaUtils.createSchema(schema)
                SchemaUtils.setSchema(schema)

                val catalogName = connection.getCatalog()

                assertEquals(catalogName, schema.identifier)
            } finally {
                SchemaUtils.dropSchema(schema)
            }
        }
    }

    @Test
    fun `create and set schema tests`() = runTest {
        withDb(excludeSettings = TestDB.ALL_MYSQL + TestDB.ALL_MARIADB) {
            if (currentDialect.supportsCreateSchema) {
                val schema = when (currentDialect) {
                    is SQLServerDialect -> {
                        exec("GRANT CREATE SCHEMA TO guest")
                        exec("SETUSER 'guest'")
                        Schema("MYSCHEMA", "guest")
                    }
                    else -> {
                        prepareSchemaForTest("MYSCHEMA")
                    }
                }

                try {
                    SchemaUtils.createSchema(schema)
                    SchemaUtils.setSchema(schema)
                    assertEquals(
                        TransactionManager.current().db.identifierManager.inProperCase(schema.identifier),
                        connection.getSchema()
                    )
                } finally {
                    SchemaUtils.dropSchema(schema)
                }
            }
        }
    }

    @Test
    fun testDropSchemaWithCascade() = runTest {
        withDb {
            if (currentDialect.supportsCreateSchema) {
                val schema = Schema("TEST_SCHEMA")
                SchemaUtils.createSchema(schema)
                assertTrue(schema.exists())

                SchemaUtils.dropSchema(schema, cascade = true)
                assertFalse(schema.exists())
            }
        }
    }

    @Test
    fun `table references table with same name in other database in mysql`() = runTest {
        withDb(TestDB.ALL_MYSQL_MARIADB) {
            val schema = Schema("MYSCHEMA")
            try {
                SchemaUtils.createSchema(schema)

                val firstCatalogName = connection.getCatalog()

                exec("DROP TABLE IF EXISTS test")
                exec("CREATE TABLE test(id INT PRIMARY KEY)")
                SchemaUtils.setSchema(schema)
                exec("DROP TABLE IF EXISTS test")
                exec("CREATE TABLE test(id INT REFERENCES $firstCatalogName.test(id))")

                val catalogName = connection.getCatalog()

                assertEquals(catalogName, schema.identifier)
            } finally {
                SchemaUtils.dropSchema(schema)
            }
        }
    }

    @Test
    fun `schemas exists tests`() = runTest {
        val schema = Schema("exposedschema")

        withDb {
            try {
                /** Assert that schema initially doesn't exist */
                assertFalse(schema.exists())

                SchemaUtils.createSchema(schema)
                /** Assert that schema exists after creation */
                assertTrue(schema.exists())

                SchemaUtils.dropSchema(schema)
                /** Assert that schema doesn't exist after dropping */
                assertFalse(schema.exists())
            } finally {
                SchemaUtils.dropSchema(schema)
            }
        }
    }

    @Test
    fun `create existing schema and drop nonexistent schemas`() = runTest {
        val schema1 = Schema("redundant")
        val schema2 = Schema("redundant")
        val schemasTryingToCreate = listOf(schema1, schema1, schema2)

        withSchemas(schema1, schema1, schema2) {
            val toCreate = schemasTryingToCreate.filterNot { it.exists() }
            /** schema1 and schema2 have been created, so there is no remaining schema to be created */
            assertTrue(toCreate.isEmpty())

            /** schema1 and schema2 variables have the same schema name */
            SchemaUtils.dropSchema(schema1)
            assertFalse(schema2.exists())
        }
    }
}

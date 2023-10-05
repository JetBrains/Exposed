package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Assume
import org.junit.Test

class SchemaTests : DatabaseTestsBase() {
    @Test
    fun `create and set schema in mysql`() {
        withDb(listOf(TestDB.MYSQL, TestDB.MARIADB)) {
            val schema = Schema("MYSCHEMA")
            try {
                SchemaUtils.createSchema(schema)
                SchemaUtils.setSchema(schema)

                val catalogName = connection.catalog

                assertEquals(catalogName, schema.identifier)
            } finally {
                SchemaUtils.dropSchema(schema)
            }
        }
    }

    @Test
    fun `create and set schema tests`() {
        withDb(excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB)) {
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
                    assertEquals(TransactionManager.current().db.identifierManager.inProperCase(schema.identifier), connection.schema)
                } finally {
                    SchemaUtils.dropSchema(schema)
                }
            }
        }
    }

    @Test
    fun testDropSchemaWithCascade() {
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
    fun `table references table with same name in other database in mysql`() {
        withDb(listOf(TestDB.MYSQL, TestDB.MARIADB)) {
            val schema = Schema("MYSCHEMA")
            try {
                SchemaUtils.createSchema(schema)

                val firstCatalogName = connection.catalog

                exec("DROP TABLE IF EXISTS test")
                exec("CREATE TABLE test(id INT PRIMARY KEY)")
                SchemaUtils.setSchema(schema)
                exec("DROP TABLE IF EXISTS test")
                exec("CREATE TABLE test(id INT REFERENCES $firstCatalogName.test(id))")

                val catalogName = connection.catalog

                assertEquals(catalogName, schema.identifier)
            } finally {
                SchemaUtils.dropSchema(schema)
            }
        }
    }

    @Test
    fun `schemas exists tests`() {
        val schema = Schema("exposedschema")

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
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
    fun `create existing schema and drop nonexistent schemas`() {
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

    @Test
    fun `test default schema`() {
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledDialects())
        val schema = Schema("schema")
        TestDB.H2.connect()

        transaction {
            connection.metadata {
                assertEquals("PUBLIC", tableNamesByCurrentSchema(null).schemaName)
            }
        }

        transaction {
            SchemaUtils.createSchema(schema)
        }

        val db = TestDB.H2.connect {
            defaultSchema = schema
        }

        transaction(db) {
            connection.metadata {
                val currentScheme = db.identifierManager.cutIfNecessaryAndQuote(
                    tableNamesByCurrentSchema(null).schemaName
                )
                assertEquals(schema.identifier, currentScheme)
            }
            // Nested transaction
            transaction(db) {
                connection.metadata {
                    val currentScheme = db.identifierManager.cutIfNecessaryAndQuote(
                        tableNamesByCurrentSchema(null).schemaName
                    )
                    assertEquals(schema.identifier, currentScheme)
                }
            }
        }
    }
}

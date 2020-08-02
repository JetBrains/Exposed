package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
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
                    is OracleDialect -> {
                        Schema("MYSCHEMA", password = "pwd4myschema", defaultTablespace = "tbs_perm_01", quota = "20M", on = "tbs_perm_01")
                    }
                    else -> {
                        Schema("MYSCHEMA")
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
    fun `table references table with same name in other database in mysql`() {
        withDb(listOf(TestDB.MYSQL, TestDB.MARIADB)) {
            val schema = Schema("MYSCHEMA")
            try {
                SchemaUtils.createSchema(schema)

                val firstCatalogName = connection.catalog

                exec("CREATE TABLE test(id INT PRIMARY KEY)")
                SchemaUtils.setSchema(schema)
                exec("CREATE TABLE test(id INT REFERENCES ${firstCatalogName}.test(id))")

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

        withSchemas(excludeSettings = listOf(TestDB.SQLITE), schemas = *arrayOf(schema1, schema1, schema2)) {
            val toCreate = schemasTryingToCreate.filterNot { it.exists() }
            /** schema1 and schema2 have been created, so there is no remaining schema to be created */
            assertTrue(toCreate.isEmpty())

            /** schema1 and schema2 variables have the same schema name */
            SchemaUtils.dropSchema(schema1)
            assertFalse(schema2.exists())
        }
    }
}
package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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
        withDb(TestDB.ALL_MYSQL_MARIADB) {
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
        withDb(TestDB.ALL_MYSQL_MARIADB) {
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
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        val schema = Schema("schema")
        TestDB.H2_V2.connect()

        transaction {
            connection.metadata {
                assertEquals("PUBLIC", tableNamesByCurrentSchema(null).schemaName)
            }
        }

        transaction {
            SchemaUtils.createSchema(schema)
        }

        val db = TestDB.H2_V2.connect {
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

        transaction {
            SchemaUtils.dropSchema(schema)
        }
    }

    @Test
    fun testCheckConstraintsNamedWithoutSchemaPrefix() {
        val schemaName = "my_schema"
        val tester = object : Table("$schemaName.test_table") {
            val amount1 = ushort("amount1") // implicit column check constraint
            val amount2 = integer("amount2").check { it lessEq 100 } // explicit column check constraint
            init {
                check { (amount1 less 100.toUShort()) and (amount2 greater 50) } // explicit table check constraint
            }
        }

        // SQLite does not recognize creation of schema other than the attached database.
        // Check constraints only introduced in MySQL v8+.
        withDb(excludeSettings = listOf(TestDB.SQLITE, TestDB.MYSQL_V5)) { testDb ->
            val schema = prepareSchemaForTest(schemaName)
            try {
                SchemaUtils.createSchema(schema)
                SchemaUtils.create(tester)

                tester.insert {
                    it[amount1] = 99u
                    it[amount2] = 56
                }

                assertEquals(1L, tester.selectAll().count())

                assertFailAndRollback("Column check constraints") {
                    tester.insert {
                        it[amount1] = 99999.toUShort()
                        it[amount2] = Int.MAX_VALUE + 1
                    }
                }

                assertFailAndRollback("Table check constraints") {
                    tester.insert {
                        it[amount1] = 101u
                        it[amount2] = 49
                    }
                }
            } finally {
                if (testDb == TestDB.SQLSERVER) {
                    SchemaUtils.drop(tester)
                    SchemaUtils.dropSchema(schema)
                } else {
                    SchemaUtils.dropSchema(schema, cascade = true)
                }
            }
        }
    }
}

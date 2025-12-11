package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

class SchemaTests : R2dbcDatabaseTestsBase() {
    @Test
    fun `create and set schema in mysql`() {
        withDb(TestDB.ALL_MYSQL_MARIADB) {
            val schema = Schema("MYSCHEMA")
            val manualSchema = Schema("MANUAL_SCHEMA")
            try {
                SchemaUtils.createSchema(schema)
                SchemaUtils.setSchema(schema)

                val catalogName = connection().getCatalog()

                assertEquals(catalogName, schema.identifier)

                // set schema directly through connection
                SchemaUtils.createSchema(manualSchema)
                connection().setCatalog(manualSchema.identifier)
                assertEquals(manualSchema.identifier, connection().getCatalog())
            } finally {
                SchemaUtils.dropSchema(schema, manualSchema)
            }
        }
    }

    @Test
    fun `create and set schema tests`() {
        withDb(excludeSettings = TestDB.ALL_MYSQL + TestDB.MARIADB) {
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
                        connection().getSchema()
                    )
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

                val firstCatalogName = connection().getCatalog()

                exec("DROP TABLE IF EXISTS test")
                exec("CREATE TABLE test(id INT PRIMARY KEY)")
                SchemaUtils.setSchema(schema)
                exec("DROP TABLE IF EXISTS test")
                exec("CREATE TABLE test(id INT REFERENCES $firstCatalogName.test(id))")

                val catalogName = connection().getCatalog()

                assertEquals(catalogName, schema.identifier)
            } finally {
                SchemaUtils.dropSchema(schema)
            }
        }
    }

    @Test
    fun `schemas exists tests`() {
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
    fun `test default schema`() = runTest {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        val schema = Schema("schema")
        TestDB.H2_V2.connect()

        suspendTransaction {
            connection().metadata {
                assertEquals("PUBLIC", tableNamesByCurrentSchema(null).schemaName)
            }
        }

        suspendTransaction {
            SchemaUtils.createSchema(schema)
        }

        val db = TestDB.H2_V2.connect {
            defaultSchema = schema
        }

        suspendTransaction(db) {
            connection().metadata {
                val currentScheme = db.identifierManager.cutIfNecessaryAndQuote(
                    tableNamesByCurrentSchema(null).schemaName
                )
                assertEquals(schema.identifier, currentScheme)
            }
            // Nested transaction
            suspendTransaction(db) {
                connection().metadata {
                    val currentScheme = db.identifierManager.cutIfNecessaryAndQuote(
                        tableNamesByCurrentSchema(null).schemaName
                    )
                    assertEquals(schema.identifier, currentScheme)
                }
            }
        }

        suspendTransaction {
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

        // Check constraints only introduced in MySQL v8+.
        withDb(excludeSettings = listOf(TestDB.MYSQL_V5)) { testDb ->
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

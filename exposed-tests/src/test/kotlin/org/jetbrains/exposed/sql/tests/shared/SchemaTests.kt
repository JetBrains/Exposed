package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.PostgreSQLNGDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test

class SchemaTests : DatabaseTestsBase() {
    @Test
    fun `create and set schema in mysql`() {
        withDb(TestDB.MYSQL) {
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
        // MARIADB also uses catalogs in data manipulation
        withDb(excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB)) {
            currentDialect
            if (currentDialect.supportsCreateSchema) {
                val schema = if (currentDialect is SQLServerDialect) {
                    exec("GRANT CREATE SCHEMA TO guest")
                    exec("SETUSER 'guest'")
                    Schema("MYSCHEMA", "guest")
                } else {
                    Schema("MYSCHEMA")
                }

                try {
                    SchemaUtils.createSchema(schema)
                    SchemaUtils.setSchema(schema)

                    val schemaName = if(currentDialect is PostgreSQLNGDialect) {
                        /** connection.schema in Pstgresql-ng always return null in current pgjdbc-ng version (0.8.3).
                         * This is fixed in pgjdbc-ng repo but not yet released. So here we retrieve the current
                         * schema using sql query rather than connection.schema */
                         exec("SELECT current_schema()") { rs ->
                            if (rs.next()) { rs.getString(1) } else { "" }
                        }
                    } else {
                        connection.schema
                    }

                    assertEquals(TransactionManager.current().db.identifierManager.inProperCase(schema.identifier), schemaName)
                } finally {
                    SchemaUtils.dropSchema(schema)
                }
            }
        }
    }

    @Test
    fun `table references table with same name in other database in mysql`() {
        withDb(TestDB.MYSQL) {
            val schema = Schema("MYSCHEMA")
            try {
                SchemaUtils.createSchema(schema)

                val firstCatalogName = connection.catalog

                exec("CREATE TABLE test(id INT)")
                SchemaUtils.setSchema(schema)
                exec("CREATE TABLE test(id INT REFERENCES ${firstCatalogName}.test(id))")

                val catalogName = connection.catalog

                assertEquals(catalogName, schema.identifier)
            } finally {
                SchemaUtils.dropSchema(schema)
            }
        }
    }
}
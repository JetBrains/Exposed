package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test

class SchemaTests : DatabaseTestsBase() {
    @Test
    fun schemaTests()  {
        withDb(TestDB.MYSQL) {
            val schema = Schema("MYSCHEMA")
            try {
                SchemaUtils.createSchema(schema)
                SchemaUtils.setSchema(schema)

                val catalogName = connection.catalog

                assertEquals(catalogName, schema.identifier)
            }
            finally {
                SchemaUtils.dropSchema(schema)
            }
        }

        // MARIADB also uses catalogs in data manipulation
        withDb (excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB)) {
            if(currentDialect.supportsCreateSchema) {
                val schema = if(currentDialect is SQLServerDialect) {
                    exec("GRANT CREATE SCHEMA TO guest")
                    exec("SETUSER 'guest'")
                    Schema("MYSCHEMA", "guest")
                } else {
                    Schema("MYSCHEMA")
                }

                try {
                    SchemaUtils.createSchema(schema)
                    SchemaUtils.setSchema(schema)

                    val schemaName = connection.schema

                    assertEquals(TransactionManager.current().db.identifierManager.inProperCase(schema.identifier), schemaName)
                }
                finally {
                    SchemaUtils.dropSchema(schema)
                }
            }
        }

        // Test references between databases in MySQL
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
            }
            finally {
                SchemaUtils.dropSchema(schema)
            }
        }
    }
}
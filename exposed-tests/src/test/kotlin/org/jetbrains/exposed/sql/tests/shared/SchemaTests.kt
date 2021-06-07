package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.withSchema
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
    fun `different tables from different schemas Test`() {
        val schema1 = Schema("schema1")
        val schema2 = Schema("schema2")

        withSchemas(schema2, schema1) {
            /** create tables in schemas */
            Author.columns.first().table.tableName
            val author = Author.withSchema(schema1)
            author.tableName
            Author.tableName

            val book = Book.withSchema(schema2, Book.authorId to author)
            SchemaUtils.create(author)
            SchemaUtils.create(book)

            /** insert in Author table from schema1  */
            author.insert {
                it[Author.name] = "author-name"
            }

            /** test inner-joins from different schemas  */
            (author innerJoin book).slice(Book.id, Author.id).selectAll().forEach {
                println("${it[Author.id]} wrote ${it[Book.id]}")
            }

            /** test cross-joins from different schemas  */
            (author crossJoin book).slice(Book.id, Author.id).selectAll().forEach {
                println("${it[Author.id]} wrote ${it[Book.id]}")
            }

            /** test right-joins from different schemas  */
            (author rightJoin book).slice(Book.id, Author.id).selectAll().forEach {
                println("${it[Author.id]} wrote ${it[Book.id]}")
            }

            /** test left-joins from different schemas  */
            (book leftJoin author).slice(Book.id, Author.id).selectAll().forEach {
                println("${it[Author.id]} wrote ${it[Book.id]}")
            }
        }
    }

    @Test
    fun `same table from different schemas Test`() {

        val schema1 = Schema("schema1")
        val schema2 = Schema("schema2")
        withSchemas(schema1, schema2) {
            /** create the same table in different schemas */
            val authorSchema1 = Author.withSchema(schema1)
            val authorSchema2 = Author.withSchema(schema2)
            SchemaUtils.create(authorSchema1)
            SchemaUtils.create(authorSchema2)

            /** insert in Actor table from schema1. */
            authorSchema1.insert {
                it[Author.name] = "author-schema1"
            }

            /** insert in Actor table from schema2. */
            authorSchema2.insert {
                it[Author.name] = "author-schema2"
            }

            /** check that author table contains only one row with the inserted data  */
            val insertedInSchema1 = Author.withSchema(schema1).slice(Author.name).selectAll().single()[Author.name]
            val insertedInSchema2 = Author.withSchema(schema2).slice(Author.name).selectAll().single()[Author.name]

            assertEquals("author-schema1", insertedInSchema1)
            assertEquals("author-schema2", insertedInSchema2)
        }
    }

    @Test
    fun `Create Missing Tables And Columns in schemas Test`() {
        val schema1 = Schema("schema1")

        withSchemas(excludeSettings = listOf(TestDB.MYSQL), schema1) {
            /** Create missing tables And columns with schema */
            val authorSchema1 = Author.withSchema(schema1)
            SchemaUtils.createMissingTablesAndColumns(authorSchema1)
        }
    }

    @Test
    fun `Data Manipulation in schemas Test`() {
        val schema1 = Schema("schema1")

        withSchemas(schema1) {
            val authorSchema = Author.withSchema(schema1)

            /** create schema  */
            SchemaUtils.createSchema(schema1)

            /** Create table with schema */
            SchemaUtils.create(authorSchema)

            /** insert into table with schema. */
            authorSchema.insert { it[Author.name] = "author1" }

            val insertedInSchema1 = authorSchema.slice(Author.name).selectAll().single()[Author.name]
            assertEquals("author1", insertedInSchema1)

            /** update table with schema. */
            authorSchema.update({ Author.name eq "author1" }) {
                it[Author.name] = "hichem"
            }

            val updatedInSchema1 = authorSchema.slice(Author.name).selectAll().single()[Author.name]
            assertEquals("hichem", updatedInSchema1)

            /** delete from table with schema. */
            authorSchema.deleteWhere {
                Author.name eq "hichem"
            }

            val deletedInSchema1 = authorSchema.slice(Author.name).selectAll().singleOrNull()?.get(Author.name)
            assertEquals(null, deletedInSchema1)

            authorSchema.insert { it[Author.name] = "hichem" }
            /** Different methods of select could be used */
            val inserted = authorSchema.select { Author.name eq "hichem" }.single() [Author.name]
            val inserted2 = authorSchema.slice(Author.name).select { Author.name eq "hichem" }.single() [Author.name]
            val inserted3 = Author.withSchema(schema1).slice(Author.name).select { Author.name eq "hichem" }.single() [Author.name]

            listOf(inserted, inserted2, inserted3).forEach { assertEquals("hichem", it) }
        }
    }

    @Test
    fun `Table name contains schema Test`() {
        val fooTable = object : IntIdTable("A.Foo") {}
        val newSchema = Schema("NewSchema")
        withDb {
            val foo = fooTable.withSchema(newSchema)
            kotlin.test.assertEquals(foo.tableName, "NewSchema.Foo")
        }
    }

    object Author : IntIdTable("author") {
        val name = varchar("name", 20)
    }

    object Book : Table("book") {
        val id = integer("id")
        val authorId = reference("authorId", Author).nullable()

        override val primaryKey = PrimaryKey(id)
    }
}

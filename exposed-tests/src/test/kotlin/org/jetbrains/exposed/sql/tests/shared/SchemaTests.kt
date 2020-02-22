package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.junit.Test

object Author : IntIdTable("authork") {
    val name = varchar("name", 20)
}

object Book : Table("bookk") {
    val id = integer("id")
    val authorId = reference("cityId", Author).nullable()

    override val primaryKey = PrimaryKey(id)
}

class SchemaTests : DatabaseTestsBase() {

    @Test
    fun `different tables from different schemas Test`() {

        withDb {
            val schema1 = "schema1"
            val schema2 = "schema2"

            try {
                /** Testing only dialects that supports using a schema name or catalog name in a data manipulation statement. */
                if (connection.metadata { supportsSchemasInDataManipulation } || connection.metadata { supportsCatalogsInDataManipulation }) {

                    /** create two schemas  */
                    SchemaUtils.createSchema(Schema("schema1"))
                    SchemaUtils.createSchema(Schema("schema2"))

                    /** create tables in schemas */
                    val author = Author.withSchema(schema1)
                    val book = Book.withSchema(schema2, author)
                    SchemaUtils.create(author)
                    SchemaUtils.create(book)

                    /** insert in Actor table from schema1  */
                    Author.insertInSchema(schema1) {
                        it[name] = "author-name"
                    }

                    /** You can use  author.insert {} directly or Author.withSchema(schema1).insert{} */
                    author.insert {
                        it[Author.name] = "author2-name"
                    }

                    /** test inner-joins from different schemas  */
                    (Author.withSchema(schema1) innerJoin Book.withSchema(schema2)).slice(Book.id, Author.id).selectAll().forEach {
                        println("${it[Author.id]} wrote ${it[Book.id]}")
                    }

                    /** test cross-joins from different schemas  */
                    (Author.withSchema(schema1) crossJoin Book.withSchema(schema2)).slice(Book.id, Author.id).selectAll().forEach {
                        println("${it[Author.id]} wrote ${it[Book.id]}")
                    }

                    /** test right-joins from different schemas  */
                    (Author.withSchema(schema1) rightJoin Book.withSchema(schema2)).slice(Book.id, Author.id).selectAll().forEach {
                        println("${it[Author.id]} wrote ${it[Book.id]}")
                    }

                    /** test left-joins from different schemas  */
                    (Book.withSchema(schema2) leftJoin Author.withSchema(schema1)).slice(Book.id, Author.id).selectAll().forEach {
                        println("${it[Author.id]} wrote ${it[Book.id]}")
                    }

                    /** you can use author and book objects directly  */
                    (author innerJoin book).slice(Book.id, Author.id).selectAll().forEach {
                        println("${it[Author.id]} wrote ${it[Book.id]}")
                    }
                }
            }
            finally {
                /** drop tables in schemas */
                SchemaUtils.drop(Book.withSchema(schema2))
                SchemaUtils.drop(Author.withSchema(schema1))
            }
        }
    }

    @Test
    fun `same table from different schemas Test`() {

        withDb {
            val schema1 = "schema1"
            val schema2 = "schema2"

            try {
                /** Testing only dialects that supports using a schema name or catalog name in a data manipulation statement. */
                if (connection.metadata { supportsSchemasInDataManipulation } || connection.metadata { supportsCatalogsInDataManipulation }) {

                    /** create two schemas  */
                    SchemaUtils.createSchema(Schema("schema1"))
                    SchemaUtils.createSchema(Schema("schema2"))

                    /** create the same table in different schemas */
                    val authorSchema1 = Author.withSchema(schema1)
                    val authorSchema2 = Author.withSchema(schema2)
                    SchemaUtils.create(authorSchema1)
                    SchemaUtils.create(authorSchema2)

                    /** insert in Actor table from schema1. */
                    Author.insertInSchema(schema1) {
                        it[name] = "author-schema1"
                    }

                    /** insert in Actor table from schema2. */
                    Author.insertInSchema(schema2) {
                        it[name] = "author-schema2"
                    }

                    /** check that author table contains only one row with the inserted data  */
                    val insertedInSchema1 = Author.withSchema(schema1).slice(Author.name).selectAll().single()[Author.name]
                    val insertedInSchema2 = Author.withSchema(schema2).slice(Author.name).selectAll().single()[Author.name]

                    assertEquals("author-schema1", insertedInSchema1)
                    assertEquals("author-schema2", insertedInSchema2)
                }
            }
            finally {
                /** drop tables in schemas */
                SchemaUtils.drop(Author.withSchema(schema1))
                SchemaUtils.drop(Author.withSchema(schema2))
            }
        }
    }

    @Test
    fun `Create Missing Tables And Columns in schemas Test`() {

        withDb(excludeSettings = (listOf(TestDB.MYSQL))) {
            val schema1 = "schema1"

            try {
                /** Testing only dialects that supports using a schema name or catalog name in a data manipulation statement. */
                if (connection.metadata { supportsSchemasInDataManipulation } || connection.metadata { supportsCatalogsInDataManipulation }) {

                    /** create two schemas  */
                    SchemaUtils.createSchema(Schema("schema1"))

                    /** Create missing tables And columns with schema */
                    val authorSchema1 = Author.withSchema(schema1)
                    SchemaUtils.createMissingTablesAndColumns(authorSchema1)
                }
            }
            finally {
                /** drop table in schema */
                SchemaUtils.drop(Author.withSchema(schema1))
            }
        }
    }

    @Test
    fun `Data Manipulation in schemas Test`() {

        withDb {
            val schema1 = "schema1"
            val authorSchema = Author.withSchema(schema1)

            try {
                /** Testing only dialects that supports using a schema name or catalog name in a data manipulation statement. */
                if (connection.metadata { supportsSchemasInDataManipulation } || connection.metadata { supportsCatalogsInDataManipulation }) {

                    /** create schema  */
                    SchemaUtils.createSchema(Schema("schema1"))

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

                    /** Different methods of select could be used */
                    authorSchema.insert { it[Author.name] = "hichem" }
                    val inserted = authorSchema.select { Author.name eq "hichem" }.single()[Author.name]
                    val inserted2 = authorSchema.slice(Author.name).select { Author.name eq "hichem" }.single()[Author.name]
                    val inserted3 = Author.withSchema(schema1).slice(Author.name).select { Author.name eq "hichem" }.single()[Author.name]

                    listOf(inserted, inserted2, inserted3).map { assertEquals("hichem", it) }

                }
            } finally {
                /** drop table in schema */
                SchemaUtils.drop(authorSchema)
            }
        }
    }
}
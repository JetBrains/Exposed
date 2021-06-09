package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.CustomLongFunction
import org.jetbrains.exposed.sql.CustomOperator
import org.jetbrains.exposed.sql.CustomStringFunction
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Sum
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.function
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.intParam
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.stringParam
import org.jetbrains.exposed.sql.substring
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.sql.tests.shared.dml.withCitiesAndUsersInSchema
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upperCase
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.withSchema
import org.junit.Test
import kotlin.test.assertNotNull

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

    @Test
    fun testCalc01() {
        withCitiesAndUsersInSchema(schema = schema1) { cities, _, _, citiesInSchema, _, _ ->
            val r = citiesInSchema.slice(cities.id.sum()).selectAll().toList()
            assertEquals(1, r.size)
            assertEquals(6, r[0][cities.id.sum()])
        }
    }

    @Test
    fun testCalc02() {
        withCitiesAndUsersInSchema(schema = schema1) { cities, users, userData, citiesInSchema, usersInSchema, userDataInSchema ->
            val sum = Expression.build {
                Sum(cities.id + userData.value, IntegerColumnType())
            }
            val r = (usersInSchema innerJoin userDataInSchema innerJoin citiesInSchema).slice(users.id, sum)
                .selectAll().groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(22, r[0][sum])
            assertEquals("sergey", r[1][users.id])
            assertEquals(32, r[1][sum])
        }
    }

    @Test
    fun testCalc03() {
        withCitiesAndUsersInSchema(schema = schema1) { cities, users, userData, citiesInSchema, usersInSchema, userDataInSchema ->
            val sum = Expression.build { Sum(cities.id * 100 + userData.value / 10, IntegerColumnType()) }
            val mod1 = Expression.build { sum % 100 }
            val mod2 = Expression.build { sum mod 100 }
            val r = (usersInSchema innerJoin userDataInSchema innerJoin citiesInSchema).slice(users.id, sum, mod1, mod1)
                .selectAll().groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(202, r[0][sum])
            assertEquals(2, r[0][mod1])
            assertEquals(2, r[0][mod2])
            assertEquals("sergey", r[1][users.id])
            assertEquals(203, r[1][sum])
            assertEquals(3, r[1][mod1])
            assertEquals(3, r[1][mod2])
        }
    }

    @Test
    fun testSubstring01() {
        withCitiesAndUsersInSchema(schema = schema1) { _, users, _, _, usersInSchema, _ ->
            val substring = users.name.substring(1, 2)
            val r = usersInSchema.slice(users.id, substring)
                .selectAll().orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals("Al", r[0][substring])
            assertEquals("An", r[1][substring])
            assertEquals("Eu", r[2][substring])
            assertEquals("Se", r[3][substring])
            assertEquals("So", r[4][substring])
        }
    }

    @Test
    fun testLengthWithCount01() {
        class LengthFunction<T : ExpressionWithColumnType<String>>(val exp: T) : Function<Int>(IntegerColumnType()) {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
                if (currentDialectTest is SQLServerDialect) append("LEN(", exp, ')')
                else append("LENGTH(", exp, ')')
            }
        }
        withCitiesAndUsersInSchema(schema = schema1) { cities, _, _, citiesInSchema, _, _ ->
            val sumOfLength = LengthFunction(cities.name).sum()
            val expectedValue = citiesInSchema.selectAll().sumBy { it[cities.name].length }

            val results = citiesInSchema.slice(sumOfLength).selectAll().toList()
            assertEquals(1, results.size)
            assertEquals(expectedValue, results.single()[sumOfLength])
        }
    }

    @Test
    fun testSelectCase01() {
        withCitiesAndUsersInSchema(schema = schema1) { _, users, _, _, usersInSchema, _ ->
            val field = Expression.build { case().When(users.id eq "alex", stringLiteral("11")).Else(stringLiteral("22")) }
            val r = usersInSchema.slice(users.id, field).selectAll().orderBy(users.id).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("11", r[0][field])
            assertEquals("alex", r[0][users.id])
            assertEquals("22", r[1][field])
            assertEquals("andrey", r[1][users.id])
        }
    }

    @Test
    fun testStringFunctions() {
        withCitiesAndUsersInSchema(schema = schema1) { _, _, _, citiesInSchema, _, _ ->

            val lcase = DMLTestsData.Cities.name.lowerCase()
            assert(citiesInSchema.slice(lcase).selectAll().any { it[lcase] == "prague" })

            val ucase = DMLTestsData.Cities.name.upperCase()
            assert(citiesInSchema.slice(ucase).selectAll().any { it[ucase] == "PRAGUE" })
        }
    }

    @Test
    fun testRandomFunction01() {
        val t = DMLTestsData.Cities.withSchema(schema1)
        withSchemas(schema1) {
            try {
                SchemaUtils.create(t)
                if (t.selectAll().count() == 0L) {
                    t.insert { it[DMLTestsData.Cities.name] = "city-1" }
                }

                val rand = Random()
                val resultRow = t.slice(rand).selectAll().limit(1).single()
                assertNotNull(resultRow[rand])
            } finally {
                SchemaUtils.drop(t)
            }
        }
    }

    @Test fun testRegexp01() {
        withCitiesAndUsersInSchema(listOf(TestDB.SQLITE, TestDB.SQLSERVER), schema = schema1) { _, users, _, _, usersInSchema, _ ->
            assertEquals(2L, usersInSchema.select { users.id regexp "a.+" }.count())
            assertEquals(1L, usersInSchema.select { users.id regexp "an.+" }.count())
            assertEquals(usersInSchema.selectAll().count(), usersInSchema.select { users.id regexp ".*" }.count())
            assertEquals(2L, usersInSchema.select { users.id regexp ".+y" }.count())
        }
    }

    @Test fun testRegexp02() {
        withCitiesAndUsersInSchema(listOf(TestDB.SQLITE, TestDB.SQLSERVER), schema = schema1) { _, users, _, _, usersInSchema, _ ->
            assertEquals(2L, usersInSchema.select { users.id.regexp(stringLiteral("a.+")) }.count())
            assertEquals(1L, usersInSchema.select { users.id.regexp(stringLiteral("an.+")) }.count())
            assertEquals(usersInSchema.selectAll().count(), usersInSchema.select { users.id.regexp(stringLiteral(".*")) }.count())
            assertEquals(2L, usersInSchema.select { users.id.regexp(stringLiteral(".+y")) }.count())
        }
    }

    @Test fun testConcat01() {
        withCitiesAndUsersInSchema(schema = schema1) { _, _, _, citiesInSchema, _, _ ->
            val concatField = SqlExpressionBuilder.concat(stringLiteral("Foo"), stringLiteral("Bar"))
            val result = citiesInSchema.slice(concatField).selectAll().limit(1).single()
            assertEquals("FooBar", result[concatField])

            val concatField2 = SqlExpressionBuilder.concat("!", listOf(stringLiteral("Foo"), stringLiteral("Bar")))
            val result2 = citiesInSchema.slice(concatField2).selectAll().limit(1).single()
            assertEquals("Foo!Bar", result2[concatField2])
        }
    }

    @Test fun testConcat02() {
        withCitiesAndUsersInSchema(schema = schema1) { _, users, _, _, usersInSchema, _ ->
            val concatField = SqlExpressionBuilder.concat(users.id, stringLiteral(" - "), users.name)
            val result = usersInSchema.slice(concatField).select { users.id eq "andrey" }.single()
            assertEquals("andrey - Andrey", result[concatField])

            val concatField2 = SqlExpressionBuilder.concat("!", listOf(users.id, users.name))
            val result2 = usersInSchema.slice(concatField2).select { users.id eq "andrey" }.single()
            assertEquals("andrey!Andrey", result2[concatField2])
        }
    }

    @Test fun testConcatWithNumbers() {
        withCitiesAndUsersInSchema(schema = schema1) { _, _, data, _, _, userDataInSchema ->
            val concatField = SqlExpressionBuilder.concat(
                data.user_id,
                stringLiteral(" - "),
                data.comment,
                stringLiteral(" - "),
                data.value
            )
            val result = userDataInSchema.slice(concatField).select { data.user_id eq "sergey" }.single()
            assertEquals("sergey - Comment for Sergey - 30", result[concatField])

            val concatField2 = SqlExpressionBuilder.concat("!", listOf(data.user_id, data.comment, data.value))
            val result2 = userDataInSchema.slice(concatField2).select { data.user_id eq "sergey" }.single()
            assertEquals("sergey!Comment for Sergey!30", result2[concatField2])
        }
    }

    @Test
    fun testCustomStringFunctions01() {
        withCitiesAndUsersInSchema(schema = schema1) { _, _, _, citiesInSchema, _, _ ->
            val customLower = DMLTestsData.Cities.name.function("lower")
            assert(citiesInSchema.slice(customLower).selectAll().any { it[customLower] == "prague" })

            val customUpper = DMLTestsData.Cities.name.function("UPPER")
            assert(citiesInSchema.slice(customUpper).selectAll().any { it[customUpper] == "PRAGUE" })
        }
    }

    @Test
    fun testCustomStringFunctions02() {
        withCitiesAndUsersInSchema(schema = schema1) { cities, _, _, citiesInSchema, _, _ ->
            val replace = CustomStringFunction("REPLACE", cities.name, stringParam("gue"), stringParam("foo"))
            val result = citiesInSchema.slice(replace).select { cities.name eq "Prague" }.singleOrNull()
            assertEquals("Prafoo", result?.get(replace))
        }
    }

    @Test
    fun testCustomIntegerFunctions01() {
        withCitiesAndUsersInSchema(schema = schema1) { _, _, _, citiesInSchema, _, _ ->
            val ids = citiesInSchema.selectAll().map { it[DMLTestsData.Cities.id] }.toList()
            assertEqualCollections(listOf(1, 2, 3), ids)

            val sqrt = DMLTestsData.Cities.id.function("SQRT")
            val sqrtIds = citiesInSchema.slice(sqrt).selectAll().map { it[sqrt] }.toList()
            assertEqualCollections(listOf(1, 1, 1), sqrtIds)
        }
    }

    @Test
    fun testCustomIntegerFunctions02() {
        withCitiesAndUsersInSchema(schema = schema1) { cities, _, _, citiesInSchema, _, _ ->
            val power = CustomLongFunction("POWER", cities.id, intParam(2))
            val ids = citiesInSchema.slice(power).selectAll().map { it[power] }
            assertEqualCollections(listOf(1L, 4L, 9L), ids)
        }
    }

    @Test
    fun testCustomOperator() {
        // implement a + operator using CustomOperator
        infix fun Expression<*>.plus(operand: Int) =
            CustomOperator<Int>("+", IntegerColumnType(), this, intParam(operand))

        withCitiesAndUsersInSchema(schema = schema1) { _, _, userData, citiesInSchema, usersInSchema, userDataInSchema ->
            userDataInSchema
                .select { (userData.value plus 15).eq(35) }
                .forEach {
                    assertEquals(it[userData.value], 20)
                }
        }
    }

    @Test
    fun testCoalesceFunction() {
        withCitiesAndUsersInSchema(schema = schema1) { _, users, _, citiesInSchema, usersInSchema, userDataInSchema ->
            val coalesceExp1 = Coalesce(users.cityId, intLiteral(1000))

            usersInSchema.slice(users.cityId, coalesceExp1).selectAll().forEach {
                val cityId = it[users.cityId]
                if (cityId != null)
                    assertEquals(cityId, it[coalesceExp1])
                else
                    assertEquals(1000, it[coalesceExp1])
            }
        }
    }

    val schema1 = Schema("schema1")

    object Author : IntIdTable("author") {
        val name = varchar("name", 20)
    }

    object Book : Table("book") {
        val id = integer("id")
        val authorId = reference("authorId", Author).nullable()

        override val primaryKey = PrimaryKey(id)
    }
}

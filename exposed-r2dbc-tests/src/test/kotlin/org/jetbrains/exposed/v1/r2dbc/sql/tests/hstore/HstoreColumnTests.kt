package org.jetbrains.exposed.v1.r2dbc.sql.tests.hstore

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.postgresql.hstore.concat
import org.jetbrains.exposed.v1.postgresql.hstore.contains
import org.jetbrains.exposed.v1.postgresql.hstore.delete
import org.jetbrains.exposed.v1.postgresql.hstore.exists
import org.jetbrains.exposed.v1.postgresql.hstore.existsAll
import org.jetbrains.exposed.v1.postgresql.hstore.existsAny
import org.jetbrains.exposed.v1.postgresql.hstore.get
import org.jetbrains.exposed.v1.postgresql.hstore.hstore
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

object HstoreTable : IntIdTable("hstore_table") {
    val data = hstore("hstore_column")
    val nullableData = hstore("nullable_hstore_column").nullable()
}

class HstoreColumnTests : R2dbcDatabaseTestsBase() {
    private fun withHstoreTable(statement: suspend R2dbcTransaction.(HstoreTable) -> Unit) {
        withDb(db = listOf(TestDB.POSTGRESQL)) {
            exec("CREATE EXTENSION IF NOT EXISTS hstore")
            SchemaUtils.create(HstoreTable)
            try {
                statement(HstoreTable)
                commit()
            } finally {
                SchemaUtils.drop(HstoreTable)
                commit()
            }
        }
    }

    @Test
    fun testHstoreRoundTrip() {
        withHstoreTable { table ->
            val map = mapOf("a" to "1", "b" to "2", "c" to null)
            table.insert {
                it[data] = map
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(map, result)
        }
    }

    @Test
    fun testHstoreNullValueInMap() {
        withHstoreTable { table ->
            val map = mapOf("a" to null, "b" to null)
            table.insert {
                it[data] = map
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(map, result)
            assertNull(result.getValue("a"))
        }
    }

    @Test
    fun testHstoreNullableColumn() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1")
                it[nullableData] = null
            }

            val result = table.selectAll().single()
            assertNull(result[table.nullableData])
        }
    }

    @Test
    fun testHstoreNullableColumnNonNullValue() {
        withHstoreTable { table ->
            val map = mapOf("a" to "1", "b" to null)
            table.insert {
                it[data] = mapOf("a" to "1")
                it[nullableData] = map
            }

            val result = table.selectAll().single()
            assertEquals(map, result[table.nullableData])
        }
    }

    @Test
    fun testHstoreEscaping() {
        withHstoreTable { table ->
            val map = mapOf("key with \"quotes\"" to "value with \\backslash\\ and \"quotes\"")
            table.insert {
                it[data] = map
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(map, result)
        }
    }

    @Test
    fun testHstoreEmptyMap() {
        withHstoreTable { table ->
            table.insert {
                it[data] = emptyMap()
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(emptyMap(), result)
        }
    }

    @Test
    fun testHstoreValueEndingInBackslash() {
        withHstoreTable { table ->
            val map = mapOf("url" to "http://google.com\\")
            table.insert {
                it[data] = map
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(map, result)
        }
    }

    @Test
    fun testHstoreDecodeRawLiteral() {
        // Plants the value via a raw hstore literal, bypassing our own encoder entirely, so this
        // only exercises decodeHstore(). A round-trip test alone can't catch a symmetric encode/decode bug.
        withHstoreTable { table ->
            exec("""INSERT INTO hstore_table (hstore_column) VALUES ('a=>NULL, b=>"1"'::hstore)""")

            val result = table.selectAll().single()[table.data]
            assertEquals(mapOf("a" to null, "b" to "1"), result)
        }
    }

    @Test
    fun testHstoreStringNullValue() {
        // The string "NULL" (quoted) must round-trip as the string "NULL", not as an actual null value.
        // Only a bare, unquoted NULL token means "no value" in hstore's text format.
        withHstoreTable { table ->
            val map = mapOf("a" to "NULL", "b" to "null", "c" to "Null", "d" to null)
            table.insert {
                it[data] = map
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(map, result)
            assertEquals("NULL", result.getValue("a"))
        }
    }

    @Test
    fun testHstoreDecodeRawLiteralStringNull() {
        // Same distinction as testHstoreStringNullValue, but decode-only: the quoted "NULL" literal is
        // planted directly via raw SQL, bypassing our own encoder.
        withHstoreTable { table ->
            exec("""INSERT INTO hstore_table (hstore_column) VALUES ('a=>"NULL", b=>NULL'::hstore)""")

            val result = table.selectAll().single()[table.data]
            assertEquals(mapOf("a" to "NULL", "b" to null), result)
        }
    }

    @Test
    fun testHstoreCommaAndNewlineInValue() {
        withHstoreTable { table ->
            val map = mapOf("multiline" to "line1,line2\nline3\rline4")
            table.insert {
                it[data] = map
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(map, result)
        }
    }

    @Test
    fun testHstoreSingleQuoteValue() {
        withHstoreTable { table ->
            val map = mapOf("it's a key" to "it's a value")
            table.insert {
                it[data] = map
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(map, result)
        }
    }

    @Test
    fun testHstoreUpdate() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1")
            }

            table.update {
                it[data] = mapOf("a" to "2", "b" to "3")
            }

            val result = table.selectAll().single()[table.data]
            assertEquals(mapOf("a" to "2", "b" to "3"), result)
        }
    }

    @Test
    fun testHstoreGet() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2")
            }

            val getA = table.data.get("a")
            val value = table.select(getA).single()[getA]
            assertEquals("1", value)
        }
    }

    @Test
    fun testHstoreContains() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2")
            }

            val count = table.selectAll().where { table.data.contains(mapOf("a" to "1")) }.count()
            assertEquals(1L, count)

            val noMatch = table.selectAll().where { table.data.contains(mapOf("a" to "999")) }.count()
            assertEquals(0L, noMatch)
        }
    }

    @Test
    fun testHstoreExists() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2")
            }

            val count = table.selectAll().where { table.data.exists("a") }.count()
            assertEquals(1L, count)

            val noMatch = table.selectAll().where { table.data.exists("nope") }.count()
            assertEquals(0L, noMatch)
        }
    }

    @Test
    fun testHstoreExistsAll() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2")
            }

            val allPresent = table.selectAll().where { table.data.existsAll(listOf("a", "b")) }.count()
            assertEquals(1L, allPresent)

            val partialMatch = table.selectAll().where { table.data.existsAll(listOf("a", "nope")) }.count()
            assertEquals(0L, partialMatch)
        }
    }

    @Test
    fun testHstoreExistsAny() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2")
            }

            val anyPresent = table.selectAll().where { table.data.existsAny(listOf("nope", "b")) }.count()
            assertEquals(1L, anyPresent)

            val noMatch = table.selectAll().where { table.data.existsAny(listOf("nope", "neither")) }.count()
            assertEquals(0L, noMatch)
        }
    }

    @Test
    fun testHstoreDeleteKey() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2")
            }

            val deleted = table.data.delete("a")
            val result = table.select(deleted).single()[deleted]
            assertEquals(mapOf("b" to "2"), result)
        }
    }

    @Test
    fun testHstoreDeleteKeys() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2", "c" to "3")
            }

            val deleted = table.data.delete(listOf("a", "c"))
            val result = table.select(deleted).single()[deleted]
            assertEquals(mapOf("b" to "2"), result)
        }
    }

    @Test
    fun testHstoreDeleteMatching() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2")
            }

            // only removes keys whose value also matches; "b" to "999" does not match, so "b" survives
            val deleted = table.data.delete(mapOf("a" to "1", "b" to "999"))
            val result = table.select(deleted).single()[deleted]
            assertEquals(mapOf("b" to "2"), result)
        }
    }

    @Test
    fun testHstoreConcat() {
        withHstoreTable { table ->
            table.insert {
                it[data] = mapOf("a" to "1", "b" to "2")
            }

            val concatenated = table.data.concat(mapOf("b" to "20", "c" to "3"))
            val result = table.select(concatenated).single()[concatenated]
            assertEquals(mapOf("a" to "1", "b" to "20", "c" to "3"), result)
        }
    }
}

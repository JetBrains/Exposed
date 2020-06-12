package org.jetbrains.exposed.sql.tests.h2

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class H2Tests : DatabaseTestsBase() {

    @Test
    fun insertInH2() {
        withDb(listOf(TestDB.H2_MYSQL, TestDB.H2)) {

            SchemaUtils.drop(Testing)
            SchemaUtils.create(Testing)
            Testing.insert {
                it[Testing.id] = 1
                it[Testing.string] = "one"
            }

            assertEquals("one", Testing.select { Testing.id.eq(1) }.single()[Testing.string])

        }
    }

    @Test
    fun replaceAsInsertInH2() {
        withDb(listOf(TestDB.H2_MYSQL, TestDB.H2)) {

            SchemaUtils.drop(Testing)
            SchemaUtils.create(Testing)
            Testing.replace {
                it[Testing.id] = 1
                it[Testing.string] = "one"
            }

            assertEquals("one", Testing.select { Testing.id.eq(1) }.single()[Testing.string])

        }
    }

    @Test
    fun replaceAsUpdateInH2() {
        withDb(listOf(TestDB.H2_MYSQL, TestDB.H2)) {

            SchemaUtils.drop(Testing)
            SchemaUtils.create(Testing)
            Testing.insert {
                it[Testing.id] = 1
                it[Testing.string] = "one"
            }

            Testing.replace {
                it[Testing.id] = 1
                it[Testing.string] = "two"
            }

            assertEquals("two", Testing.select { Testing.id.eq(1) }.single()[Testing.string])
        }
    }

    @Test
    fun emptyReplace() {
        withDb(listOf(TestDB.H2_MYSQL, TestDB.H2)) {

            SchemaUtils.drop(Testing)
            SchemaUtils.create(Testing)

            Testing.replace {}
        }
    }

    object Testing : Table("H2_TESTING") {
        val id = integer("id").autoIncrement() // Column<Int>
        val string = varchar("string", 128)

        override val primaryKey = PrimaryKey(id)
    }

    object RefTable : Table() {
        val id = integer("id").autoIncrement() // Column<Int>
        val ref = reference("test", Testing.id)

        override val primaryKey = PrimaryKey(id)
    }
}
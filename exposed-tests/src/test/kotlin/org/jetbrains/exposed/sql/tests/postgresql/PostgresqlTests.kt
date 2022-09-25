package org.jetbrains.exposed.sql.tests.postgresql

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.RepeatableTestRule
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class PostgresqlTests : DatabaseTestsBase() {
    @get:Rule
    val repeatRule = RepeatableTestRule()

    private val table = object : IntIdTable() {
        val name = varchar("name", 50)
    }

    @Test
    fun `test for update options syntax`() {
        val id = 1

        fun Query.city() = map { it[table.name] }.single()

        fun select(option: ForUpdateOption): String {
            return table.select { table.id eq id }.forUpdate(option).city()
        }

        withDb(TestDB.POSTGRESQL) {
            withTable {
                val name = "name"
                table.insert {
                    it[table.id] = id
                    it[table.name] = name
                }
                commit()

                val defaultForUpdateRes = table.select { table.id eq id }.city()
                val forUpdateRes = select(ForUpdateOption.ForUpdate)
                val forShareRes = select(ForUpdateOption.PostgreSQL.ForShare)
                val forKeyShareRes = select(ForUpdateOption.PostgreSQL.ForKeyShare)
                val forNoKeyUpdateRes = select(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
                val notForUpdateRes = table.select { table.id eq id }.notForUpdate().city()

                assertEquals(name, defaultForUpdateRes)
                assertEquals(name, forUpdateRes)
                assertEquals(name, forShareRes)
                assertEquals(name, forKeyShareRes)
                assertEquals(name, forNoKeyUpdateRes)
                assertEquals(name, notForUpdateRes)
            }
        }
    }

    private fun Transaction.withTable(statement: Transaction.() -> Unit) {
        SchemaUtils.create(table)
        try {
            statement()
            commit() // Need commit to persist data before drop tables
        } finally {
            SchemaUtils.drop(table)
            commit()
        }
    }
}

package org.jetbrains.exposed.sql.tests.sqlite

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.Category
import org.jetbrains.exposed.sql.tests.shared.DEFAULT_CATEGORY_ID
import org.jetbrains.exposed.sql.tests.shared.Item
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test

class ForeignKeyConstraintTests : DatabaseTestsBase() {

    @Test
    fun `test ON DELETE SET DEFAULT for databases that support it without SQLite`() {
        withDb(excludeSettings = listOf(TestDB.MARIADB, TestDB.MYSQL, TestDB.SQLITE, TestDB.ORACLE)) {
            testOnDeleteSetDefault()
        }
    }

    @Test
    fun `test ON DELETE SET DEFAULT for SQLite`() {
        Assume.assumeTrue(TestDB.SQLITE in TestDB.enabledDialects())

        transaction(Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared&foreign_keys=on", user = "root", driver = "org.sqlite.JDBC")) {
            testOnDeleteSetDefault()
        }
    }

    private fun Transaction.testOnDeleteSetDefault() {
        SchemaUtils.drop(Category, Item)
        SchemaUtils.create(Category, Item)

        Category.insert {
            it[id] = DEFAULT_CATEGORY_ID
            it[name] = "Default"
        }

        val saladsId = 1
        Category.insert {
            it[id] = saladsId
            it[name] = "Salads"
        }

        val tabboulehId = 0
        Item.insert {
            it[id] = tabboulehId
            it[name] = "Tabbouleh"
            it[categoryId] = saladsId
        }

        assertEquals(
            saladsId,
            Item.select { Item.id eq tabboulehId }.single().also {
                println("SELECT result = $it")
            }[Item.categoryId]
        )

        Category.deleteWhere { id eq saladsId }

        assertEquals(
            DEFAULT_CATEGORY_ID,
            Item.select { Item.id eq tabboulehId }.single()[Item.categoryId]
        )
    }

    @Test
    fun `test ON DELETE RESTRICT for databases that support it without SQLite`() {
        withDb(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER)) {
            testOnDeleteRestrict()
        }
    }

    @Test
    fun `test ON DELETE RESTRICT for SQLite`() {
        Assume.assumeTrue(TestDB.SQLITE in TestDB.enabledDialects())

        transaction(Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared&foreign_keys=on", user = "root", driver = "org.sqlite.JDBC")) {
            testOnDeleteRestrict()
        }
    }

    private fun testOnDeleteRestrict() {
        val country = object : Table("Country") {
            val id = integer("id")
            val name = varchar(name = "name", length = 20)

            override val primaryKey = PrimaryKey(id)
        }

        val city = object : Table("City") {
            val id = integer("id")
            val name = varchar(name = "name", length = 20)
            val countryId = integer("countryId")
                .references(
                    country.id,
                    onDelete = ReferenceOption.RESTRICT
                )

            override val primaryKey = PrimaryKey(id)
        }

        SchemaUtils.drop(country, city)
        SchemaUtils.create(country, city)

        val lebanonId = 0
        country.insert {
            it[id] = lebanonId
            it[name] = "Lebanon"
        }

        val beirutId = 0
        city.insert {
            it[id] = beirutId
            it[name] = "Beirut"
            it[countryId] = 0
        }

        expectException<ExposedSQLException> {
            country.deleteWhere { id eq lebanonId }
        }
    }

    @Test
    fun `test ON UPDATE RESTRICT for databases that support it without SQLite`() {
        withDb(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) {
            testOnUpdateRestrict()
        }
    }

    @Test
    fun `test ON UPDATE RESTRICT for SQLite`() {
        Assume.assumeTrue(TestDB.SQLITE in TestDB.enabledDialects())

        transaction(Database.connect("jdbc:sqlite:file:test?mode=memory&cache=shared&foreign_keys=on", user = "root", driver = "org.sqlite.JDBC")) {
            testOnUpdateRestrict()
        }
    }

    private fun testOnUpdateRestrict() {
        val country = object : Table("Country") {
            val id = integer("id")
            val name = varchar(name = "name", length = 20)

            override val primaryKey = PrimaryKey(id)
        }

        val city = object : Table("City") {
            val id = integer("id")
            val name = varchar(name = "name", length = 20)
            val countryId = integer("countryId")
                .references(
                    country.id,
                    onUpdate = ReferenceOption.RESTRICT
                )

            override val primaryKey = PrimaryKey(id)
        }

        SchemaUtils.drop(country, city)
        SchemaUtils.create(country, city)

        val lebanonId = 0
        country.insert {
            it[id] = lebanonId
            it[name] = "Lebanon"
        }

        val beirutId = 0
        city.insert {
            it[id] = beirutId
            it[name] = "Beirut"
            it[countryId] = 0
        }

        expectException<ExposedSQLException> {
            country.update({ country.id eq lebanonId }) {
                it[id] = 1
            }
        }
    }
}

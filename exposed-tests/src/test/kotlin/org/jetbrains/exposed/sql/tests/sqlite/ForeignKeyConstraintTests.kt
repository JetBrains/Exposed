package org.jetbrains.exposed.sql.tests.sqlite

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.Category
import org.jetbrains.exposed.sql.tests.shared.DEFAULT_CATEGORY_ID
import org.jetbrains.exposed.sql.tests.shared.Item
import org.jetbrains.exposed.sql.tests.shared.assertEquals
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
}

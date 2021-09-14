@file: Suppress("MatchingDeclarationName", "Filename")
package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class `Table id not in Record Test issue 1341` : DatabaseTestsBase() {

    object NamesTable : IdTable<Int>("names_table") {
        val first = varchar("first", 50)

        val second = varchar("second", 50)

        override val id = integer("id").autoIncrement().entityId()

        override val primaryKey = PrimaryKey(id)
    }

    object AccountsTable : IdTable<Int>("accounts_table") {
        val name = reference("name", NamesTable)
        override val id: Column<EntityID<Int>> = integer("id").autoIncrement().entityId()
        override val primaryKey = PrimaryKey(id)
    }

    class Names(id: EntityID<Int>) : IntEntity(id) {
        var first: String by NamesTable.first
        var second: String by NamesTable.second
        companion object : IntEntityClass<Names>(NamesTable)
    }

    class Accounts(id: EntityID<Int>) : IntEntity(id) {
        var name: Names by Names referencedOn AccountsTable.name

        companion object : EntityClass<Int, Accounts>(AccountsTable) {
            fun new(accountName: Pair<String, String>): Accounts = new {
                this.name = Names.new {
                    first = accountName.first
                    second = accountName.second
                }
            }
        }
    }

    @Test
    fun testRegression() {
        withTables(NamesTable, AccountsTable) {
            val account = Accounts.new("first" to "second")
            assertEquals("first", account.name.first)
            assertEquals("second", account.name.second)
        }
    }
}

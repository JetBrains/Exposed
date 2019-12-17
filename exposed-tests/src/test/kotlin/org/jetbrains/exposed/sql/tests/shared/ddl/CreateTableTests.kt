package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.junit.Test

import kotlin.test.assertFails

class CreateTableTests : DatabaseTestsBase() {
    // https://github.com/JetBrains/Exposed/issues/709
    @Test
    fun createTableWithDuplicateColumn() {
        val assertionFailureMessage = "Can't create a table with multiple columns having the same name"

        withDb() {
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableWithDuplicatedColumn)
            }
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableDuplicatedColumnRefereToIntIdTable)
            }
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableDuplicatedColumnRefereToTable)
            }
        }
    }

    object TableWithDuplicatedColumn : Table("myTable") {
        val id1 = integer("id")
        val id2 = integer("id")
    }

    object IDTable : IntIdTable("IntIdTable")

    object TableDuplicatedColumnRefereToIntIdTable : IntIdTable("myTable") {
        val reference = reference("id", IDTable)
    }

    object TableDuplicatedColumnRefereToTable : Table("myTable") {
        val reference = reference("id", TableWithDuplicatedColumn.id1)
    }
}

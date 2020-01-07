package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.DuplicateColumnException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.junit.Test

import kotlin.test.assertFailsWith

class ReplaceColumnTests : DatabaseTestsBase() {
    // https://github.com/JetBrains/Exposed/issues/709
    @Test
    fun replaceColumnToDuplicateColumn() {
        val assertionFailureMessage = "Can't replace a column with another one that has the same name as an existing column"

        withTables(IDTable) {
            assertFailsWith(exceptionClass =    DuplicateColumnException::class,
                            message =           assertionFailureMessage
            ) {
                //Duplicate the id column by replacing the IDTable.code by a column with the name "id"
                val id = Column<Int>(IDTable, IDTable.id.name, IDTable.id.columnType)
                IDTable.replaceColumn(IDTable.code, id)
            }
        }
    }

    object IDTable : IntIdTable("myTable") {
        val code = integer("code")
    }
}

package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.DuplicateColumnException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EntityIDColumnType
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test

class ReplaceColumnTests : DatabaseTestsBase() {
    // https://github.com/JetBrains/Exposed/issues/709
    @Test
    fun replaceColumnToDuplicateColumn() {
        withTables(IDTable) {
            expectException<DuplicateColumnException> {
                // Duplicate the id column by replacing the IDTable.code by a column with the name "id"
                val id = Column<Int>(IDTable, IDTable.id.name, (IDTable.id.columnType as EntityIDColumnType<Int>).idColumn.columnType)
                IDTable.replaceColumn(IDTable.code, id)
            }
        }
    }

    object IDTable : IntIdTable("myTable") {
        val code = integer("code")
    }
}

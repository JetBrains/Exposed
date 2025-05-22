package org.jetbrains.exposed.v1.tests.shared.ddl

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.exceptions.DuplicateColumnException
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.Test

class ReplaceColumnTests : DatabaseTestsBase() {
    // https://github.com/JetBrains/Exposed/issues/709
    @Test
    fun replaceColumnToDuplicateColumn() {
        withTables(IDTable) {
            expectException<DuplicateColumnException> {
                // Duplicate the id column by replacing the IDTable.code by a column with the name "id"
                val id = Column(IDTable, IDTable.id.name, (IDTable.id.columnType as EntityIDColumnType<Int>).idColumn.columnType)
                IDTable.replaceColumn(IDTable.code, id)
            }
        }
    }

    object IDTable : IntIdTable("myTable") {
        val code = integer("code")
    }
}

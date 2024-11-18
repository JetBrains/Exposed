package org.example.examples

import org.example.tables.UsersTable
import org.example.tables.CitiesTable
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.stringParam
import org.jetbrains.exposed.sql.substring

class InsertSelectExamples {
    fun insertSelect() {
        val substring = UsersTable.name.substring(1, 2)
        val insertedRows = CitiesTable.insert(UsersTable.select(substring).orderBy(UsersTable.id).limit(2))
    }

    fun insertSelectWithCol() {
        val userCount = UsersTable.selectAll().count()
        val insertedUsers = UsersTable.insert(
            UsersTable.select(
                stringParam("Foo"),
                Random().castTo<String>(VarCharColumnType()).substring(1, 10)
            ),
            columns = listOf(UsersTable.name, UsersTable.id)
        )
    }
}

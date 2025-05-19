package org.example.examples

import org.example.tables.CitiesTable
import org.example.tables.UsersTable
import org.jetbrains.exposed.v1.core.Random
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.core.substring
import org.jetbrains.exposed.v1.jdbc.*

private const val MAX_VARCHAR_LENGTH = 10

class InsertSelectExamples {
    fun insertSelect() {
        val substring = UsersTable.name.substring(1, 2)
        val insertedRows = CitiesTable.insert(UsersTable.select(substring).orderBy(UsersTable.id).limit(2))
        println(insertedRows)
    }

    fun insertSelectWithCol() {
        val userCount = UsersTable.selectAll().count()
        println(userCount)

        val insertedUsers = UsersTable.insert(
            UsersTable.select(
                stringParam("Foo"),
                Random().castTo<String>(VarCharColumnType()).substring(1, MAX_VARCHAR_LENGTH)
            ),
            columns = listOf(UsersTable.name, UsersTable.id)
        )
        println(insertedUsers)
    }
}

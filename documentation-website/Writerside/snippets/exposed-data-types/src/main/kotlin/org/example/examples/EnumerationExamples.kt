package org.example.examples

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PGobject

enum class Foo { BAR, BAZ }

class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
    init {
        value = enumValue?.name
        type = enumTypeName
    }
}

object EnumTable : Table() {
    val enumColumn: Column<Foo> = customEnumeration(
        name = "enumColumn",
        sql = "FooEnum",
        fromDb = { value -> Foo.valueOf(value as String) },
        toDb = { PGEnum("FooEnum", it) }
    )
}

object NewEnumTable : Table() {
    val newEnumColumn: Column<Foo> = customEnumeration(
        name = "enumColumn",
        sql = "ENUM('BAR', 'BAZ')",
        fromDb = { value -> Foo.valueOf(value as String) },
        toDb = { it.name }
    )
}

object ExistingEnumTable : Table() {
    val existingEnumColumn: Column<Foo> = customEnumeration(
        name = "enumColumn",
        fromDb = { value -> Foo.valueOf(value as String) },
        toDb = { it.name }
    )
}

class EnumerationExamples {
    fun createTableWithExistingEnumColumn() {
        transaction {
            exec("""CREATE TABLE IF NOT EXISTS EXISTINGENUM ("enumColumn" ENUM('BAR', 'BAZ') NOT NULL)""")
        }
    }
    fun insertEnumIntoTableWithExistingEnumColumn() {
        ExistingEnumTable.insert {
            it[existingEnumColumn] = Foo.BAZ
        }
    }
    fun createTableWithEnumColumn() {
        transaction {
            exec("CREATE TYPE FooEnum AS ENUM ('BAR', 'BAZ');")
            SchemaUtils.create(EnumTable)
        }
    }
}

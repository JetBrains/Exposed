package org.example.examples

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.postgresql.util.PGobject

/*
    Important: The code in this file is referenced by line number in `Enumeration-types.topic`.
    If you add, remove, or modify any lines prior to this one, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

enum class Foo { BAR, BAZ }

private const val ENUM_NAME_COLUMN_LENGTH = 10

object BasicEnumTable : Table() {
    val enumOrdinal = enumeration("enumOrdinal", Foo::class)
}

object NamedEnumTable : Table() {
    val enumName = enumerationByName("enumName", ENUM_NAME_COLUMN_LENGTH, Foo::class)
}

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
    fun basicEnumExample() {
        transaction {
            // Create table with enumeration column (stores ordinal values)
            SchemaUtils.create(BasicEnumTable)

            // Insert enum value
            BasicEnumTable.insert {
                it[enumOrdinal] = Foo.BAR
            }
        }
    }

    fun namedEnumExample() {
        transaction {
            // Create table with enumerationByName column (stores enum names)
            SchemaUtils.create(NamedEnumTable)

            // Insert enum value
            NamedEnumTable.insert {
                it[enumName] = Foo.BAZ
            }
        }
    }

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

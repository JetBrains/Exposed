package org.example.examples

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
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
    val enumColumn = customEnumeration("enumColumn", "FooEnum", { value -> Foo.valueOf(value as String) }, { PGEnum("FooEnum", it) })
}

object NewEnumTable : Table() {
    val newEnumColumn = customEnumeration("enumColumn", "ENUM('BAR', 'BAZ')", { value -> Foo.valueOf(value as String) }, { it.name })
}

object ExistingEnumTable : Table() {
    val existingEnumColumn = customEnumeration("enumColumn", { value -> Foo.valueOf(value as String) }, { it.name })
}

class EnumerationExamples {
    fun createTableWithExistingEnumColumn() {
        transaction {
            SchemaUtils.create(MySQLEnumTable)
        }
    }
    fun createTableWithEnumColumn() {
        transaction {
            exec("CREATE TYPE FooEnum AS ENUM ('BAR', 'BAZ');")
            SchemaUtils.create(EnumTable)
        }
    }
}

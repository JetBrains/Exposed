package org.jetbrains.exposed.r2dbc.sql.tests.ddl

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.junit.Test
import org.postgresql.util.PGobject

class EnumerationTests : R2dbcDatabaseTestsBase() {
    // r2dbc-h2 declined feature request for enum codec support: https://github.com/r2dbc/r2dbc-h2/issues/131
    private val supportsCustomEnumerationDB = TestDB.ALL_MYSQL_MARIADB + TestDB.ALL_POSTGRES

    internal enum class Foo {
        Bar, Baz;

        override fun toString(): String = "Foo Enum ToString: $name"
    }

    class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
        init {
            value = enumValue?.name
            type = enumTypeName
        }
    }

    object EnumTable : IntIdTable("EnumTable") {
        internal var enumColumn: Column<Foo> = enumeration("enumColumn")

        internal fun initEnumColumn(sql: String) {
            (columns as MutableList<Column<*>>).remove(enumColumn)
            enumColumn = customEnumeration(
                "enumColumn", sql,
                { value -> Foo.valueOf(value as String) },
                { value ->
                    when (currentDialectTest) {
                        is PostgreSQLDialect -> PGEnum(sql, value)
                        else -> value.name
                    }
                }
            )
        }
    }

    @Test
    fun testCustomEnumerationWithDefaultValue() {
        withDb(supportsCustomEnumerationDB) {
            val sqlType = when (currentDialectTest) {
                is MysqlDialect -> "ENUM('Bar', 'Baz')"
                is PostgreSQLDialect -> "FooEnum2"
                else -> error("Unsupported case")
            }
            try {
                if (currentDialectTest is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS FooEnum2;")
                    exec("CREATE TYPE FooEnum2 AS ENUM ('Bar', 'Baz');")
                }
                EnumTable.initEnumColumn(sqlType)
                with(EnumTable) {
                    enumColumn.default(Foo.Bar)
                }
                SchemaUtils.create(EnumTable)
                // drop shared table object's unique index if created in other test
                if (EnumTable.indices.isNotEmpty()) {
                    exec(EnumTable.indices.first().dropStatement().single())
                }

                EnumTable.insert { }
                val default = EnumTable.selectAll().single()[EnumTable.enumColumn]
                assertEquals(Foo.Bar, default)
            } finally {
                try {
                    SchemaUtils.drop(EnumTable)
                } catch (ignore: Exception) {
                }
            }
        }
    }

    @Test
    fun testCustomEnumerationWithReference() {
        val referenceTable = object : Table("ref_table") {
            var referenceColumn: Column<Foo> = enumeration("ref_column")

            fun initRefColumn() {
                (columns as MutableList<Column<*>>).remove(referenceColumn)
                referenceColumn = reference("ref_column", EnumTable.enumColumn)
            }
        }

        withDb(supportsCustomEnumerationDB) {
            val sqlType = when (currentDialectTest) {
                is MysqlDialect -> "ENUM('Bar', 'Baz')"
                is PostgreSQLDialect -> "RefEnum"
                else -> error("Unsupported case")
            }
            try {
                if (currentDialectTest is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS $sqlType;")
                    exec("CREATE TYPE $sqlType AS ENUM ('Bar', 'Baz');")
                }
                EnumTable.initEnumColumn(sqlType)
                with(EnumTable) {
                    if (indices.isEmpty()) enumColumn.uniqueIndex()
                }
                SchemaUtils.create(EnumTable)

                referenceTable.initRefColumn()
                SchemaUtils.create(referenceTable)

                val fooBar = Foo.Bar
                val id1 = EnumTable.insert {
                    it[enumColumn] = fooBar
                } get EnumTable.enumColumn
                referenceTable.insert {
                    it[referenceColumn] = id1
                }

                assertEquals(fooBar, EnumTable.selectAll().single()[EnumTable.enumColumn])
                assertEquals(fooBar, referenceTable.selectAll().single()[referenceTable.referenceColumn])
            } finally {
                SchemaUtils.drop(referenceTable)
                exec(EnumTable.indices.first().dropStatement().single())
                SchemaUtils.drop(EnumTable)
            }
        }
    }

    @Test
    fun testEnumerationColumnsWithReference() {
        val tester = object : Table("tester") {
            val enumColumn = enumeration<Foo>("enum_column").uniqueIndex()
            val enumNameColumn = enumerationByName<Foo>("enum_name_column", 32).uniqueIndex()
        }
        val referenceTable = object : Table("ref_table") {
            val referenceColumn = reference("ref_column", tester.enumColumn)
            val referenceNameColumn = reference("ref_name_column", tester.enumNameColumn)
        }

        withTables(tester, referenceTable) {
            val fooBar = Foo.Bar
            val fooBaz = Foo.Baz
            val entry = tester.insert {
                it[enumColumn] = fooBar
                it[enumNameColumn] = fooBaz
            }
            referenceTable.insert {
                it[referenceColumn] = entry[tester.enumColumn]
                it[referenceNameColumn] = entry[tester.enumNameColumn]
            }

            assertEquals(fooBar, tester.selectAll().single()[tester.enumColumn])
            assertEquals(fooBar, referenceTable.selectAll().single()[referenceTable.referenceColumn])

            assertEquals(fooBaz, tester.selectAll().single()[tester.enumNameColumn])
            assertEquals(fooBaz, referenceTable.selectAll().single()[referenceTable.referenceNameColumn])
        }
    }
}

package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.DDLTests
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.junit.Test

class EnumerationTests : DatabaseTestsBase() {
    private val supportsCustomEnumerationDB = TestDB.mySqlRelatedDB + listOf(TestDB.H2, TestDB.H2_PSQL, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG)

    object EnumTable : IntIdTable("EnumTable") {
        internal var enumColumn: Column<DDLTests.Foo> = enumeration("enumColumn")

        internal fun initEnumColumn(sql: String) {
            (columns as MutableList<Column<*>>).remove(enumColumn)
            enumColumn = customEnumeration(
                "enumColumn", sql,
                { value ->
                    when {
                        currentDialectTest is H2Dialect && value is Int -> DDLTests.Foo.values()[value]
                        else -> DDLTests.Foo.valueOf(value as String)
                    }
                },
                { value ->
                    when (currentDialectTest) {
                        is PostgreSQLDialect -> DDLTests.PGEnum(sql, value)
                        else -> value.name
                    }
                }
            )
        }
    }

    @Test
    fun testCustomEnumeration01() {
        withDb(supportsCustomEnumerationDB) {
            val sqlType = when (currentDialectTest) {
                is H2Dialect, is MysqlDialect -> "ENUM('Bar', 'Baz')"
                is PostgreSQLDialect -> "FooEnum"
                else -> error("Unsupported case")
            }

            class EnumEntity(id: EntityID<Int>) : IntEntity(id) {
                var enum by EnumTable.enumColumn
            }

            val EnumClass = object : IntEntityClass<EnumEntity>(EnumTable, EnumEntity::class.java) {}

            try {
                if (currentDialectTest is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS FooEnum;")
                    exec("CREATE TYPE FooEnum AS ENUM ('Bar', 'Baz');")
                }
                EnumTable.initEnumColumn(sqlType)
                SchemaUtils.create(EnumTable)
                // drop shared table object's unique index if created in other test
                if (EnumTable.indices.isNotEmpty()) {
                    exec(EnumTable.indices.first().dropStatement().single())
                }
                EnumTable.insert {
                    it[enumColumn] = DDLTests.Foo.Bar
                }
                assertEquals(DDLTests.Foo.Bar, EnumTable.selectAll().single()[EnumTable.enumColumn])

                EnumTable.update {
                    it[enumColumn] = DDLTests.Foo.Baz
                }

                val entity = EnumClass.new {
                    enum = DDLTests.Foo.Baz
                }
                assertEquals(DDLTests.Foo.Baz, entity.enum)
                entity.id.value // flush entity
                assertEquals(DDLTests.Foo.Baz, entity.enum)
                assertEquals(DDLTests.Foo.Baz, EnumClass.reload(entity)!!.enum)
                entity.enum = DDLTests.Foo.Bar
//                flushCache()
                assertEquals(DDLTests.Foo.Bar, EnumClass.reload(entity, true)!!.enum)
            } finally {
                try {
                    SchemaUtils.drop(EnumTable)
                } catch (ignore: Exception) {}
            }
        }
    }

    @Test
    fun testCustomEnumerationWithDefaultValue() {
        withDb(supportsCustomEnumerationDB) {
            val sqlType = when (currentDialectTest) {
                is H2Dialect, is MysqlDialect -> "ENUM('Bar', 'Baz')"
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
                    enumColumn.default(DDLTests.Foo.Bar)
                }
                SchemaUtils.create(EnumTable)
                // drop shared table object's unique index if created in other test
                if (EnumTable.indices.isNotEmpty()) {
                    exec(EnumTable.indices.first().dropStatement().single())
                }

                EnumTable.insert { }
                val default = EnumTable.selectAll().single()[EnumTable.enumColumn]
                assertEquals(DDLTests.Foo.Bar, default)
            } finally {
                try {
                    SchemaUtils.drop(EnumTable)
                } catch (ignore: Exception) {}
            }
        }
    }

    @Test
    fun testCustomEnumerationWithReference() {
        val referenceTable = object : Table("ref_table") {
            var referenceColumn: Column<DDLTests.Foo> = enumeration("ref_column")

            fun initRefColumn() {
                (columns as MutableList<Column<*>>).remove(referenceColumn)
                referenceColumn = reference("ref_column", EnumTable.enumColumn)
            }
        }

        withDb(supportsCustomEnumerationDB) {
            val sqlType = when (currentDialectTest) {
                is H2Dialect, is MysqlDialect -> "ENUM('Bar', 'Baz')"
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

                val fooBar = DDLTests.Foo.Bar
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
            val enumColumn = enumeration<DDLTests.Foo>("enum_column").uniqueIndex()
            val enumNameColumn = enumerationByName<DDLTests.Foo>("enum_name_column", 32).uniqueIndex()
        }
        val referenceTable = object : Table("ref_table") {
            val referenceColumn = reference("ref_column", tester.enumColumn)
            val referenceNameColumn = reference("ref_name_column", tester.enumNameColumn)
        }

        withTables(tester, referenceTable) {
            val fooBar = DDLTests.Foo.Bar
            val fooBaz = DDLTests.Foo.Baz
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

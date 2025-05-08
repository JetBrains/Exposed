package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.codec.EnumCodec
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.sql.insert
import org.jetbrains.exposed.v1.r2dbc.sql.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.sql.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.sql.update
import org.jetbrains.exposed.v1.sql.Column
import org.jetbrains.exposed.v1.sql.Table
import org.jetbrains.exposed.v1.sql.vendors.MysqlDialect
import org.jetbrains.exposed.v1.sql.vendors.PostgreSQLDialect
import org.junit.Test

class EnumerationTests : R2dbcDatabaseTestsBase() {
    // NOTE: UNSUPPORTED r2dbc-h2
    // declined feature request for enum codec support: https://github.com/r2dbc/r2dbc-h2/issues/131
    private val supportsCustomEnumerationDB = TestDB.ALL_MYSQL_MARIADB + TestDB.ALL_POSTGRES

    internal enum class Foo {
        Bar, Baz
    }

    @Suppress("UnusedPrivateProperty")
    private val pgOptionsWithEnumCodec by lazy {
        PostgresqlConnectionConfiguration.builder()
            .host("127.0.0.1")
            .port(3004)
            .username(TestDB.POSTGRESQL.user)
            .password(TestDB.POSTGRESQL.pass)
            .database("postgres")
            .options(mapOf("lc_messages" to "en_US.UTF-8"))
            .codecRegistrar(EnumCodec.builder().withEnum("fooenum", Foo::class.java).build())
            .build()
    }

//    private val pgDB by lazy {
//        R2dbcDatabase.connect(
//            connectioNFactory = PostgresqlConnectionFactory(pgOptionsWithEnumCodec)
//        ) {
//            explicitDialect = PostgreSQLDialect()
//        }
//    }

    object EnumTable : IntIdTable("EnumTable") {
        internal var enumColumn: Column<Foo> = enumeration("enumColumn")

        internal fun initEnumColumn(sql: String) {
            (columns as MutableList<Column<*>>).remove(enumColumn)
            enumColumn = customEnumeration(
                "enumColumn", sql,
                { value -> Foo.valueOf(value as String) },
                { value ->
                    when (currentDialectTest) {
                        is PostgreSQLDialect -> value
                        else -> value.name
                    }
                }
            )
        }
    }

    // NOTE: DAO part of test uncommented
    @Test
    fun testCustomEnumeration01() {
        withDb(supportsCustomEnumerationDB, excludeSettings = TestDB.ALL_POSTGRES) {
            val sqlType = when (currentDialectTest) {
                is MysqlDialect -> "ENUM('Bar', 'Baz')"
                is PostgreSQLDialect -> "fooenum"
                else -> error("Unsupported case")
            }

//            class EnumEntity(id: EntityID<Int>) : IntEntity(id) {
//                var enum by EnumTable.enumColumn
//            }

//            val enumClass = object : IntEntityClass<EnumEntity>(EnumTable, EnumEntity::class.java) {}

            try {
                if (currentDialectTest is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS fooenum;")
                    exec("CREATE TYPE fooenum AS ENUM ('Bar', 'Baz');")
                }
                EnumTable.initEnumColumn(sqlType)
                SchemaUtils.create(EnumTable)
                // drop shared table object's unique index if created in other test
                if (EnumTable.indices.isNotEmpty()) {
                    exec(EnumTable.indices.first().dropStatement().single())
                }
                EnumTable.insert {
                    it[EnumTable.enumColumn] = Foo.Bar
                }
                assertEquals(Foo.Bar, EnumTable.selectAll().single()[EnumTable.enumColumn])

                // low-level bind works fine with PG-specific options, as does higher-level insert().
                // cache issue remains when trying to get resultPublisher from the stored R2dbcResult;
                // need to create an alt R2dbcDatabase.connect(connectionFactory = ?) to handle this.
//                val cx = (connection.connection as Publisher<out Connection>).awaitFirst()
//                val stmt = cx.createStatement("INSERT INTO enumtable (\"enumColumn\") VALUES ($1)")
//                stmt.bind("$1", Foo.Bar)
//                stmt.execute().awaitFirst().map {  }.collect {  }
//
//                EnumTable.selectAll().single()[EnumTable.enumColumn].also { println("Retrieved $it") }

                EnumTable.update {
                    it[enumColumn] = Foo.Baz
                }
                assertEquals(Foo.Baz, EnumTable.selectAll().single()[EnumTable.enumColumn])

//                val entity = enumClass.new {
//                    enum = Foo.Baz
//                }
//                assertEquals(Foo.Baz, entity.enum)
//                entity.id.value // flush entity
//                assertEquals(Foo.Baz, entity.enum)
//                assertEquals(Foo.Baz, enumClass.reload(entity)!!.enum)
//                entity.enum = Foo.Bar
//                assertEquals(Foo.Bar, enumClass.reload(entity, true)!!.enum)
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
                is MysqlDialect -> "ENUM('Bar', 'Baz')"
                is PostgreSQLDialect -> "fooenum"
                else -> error("Unsupported case")
            }
            try {
                if (currentDialectTest is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS fooenum;")
                    exec("CREATE TYPE fooenum AS ENUM ('Bar', 'Baz');")
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
                } catch (ignore: Exception) {}
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

        withDb(supportsCustomEnumerationDB, excludeSettings = TestDB.ALL_POSTGRES) {
            val sqlType = when (currentDialectTest) {
                is MysqlDialect -> "ENUM('Bar', 'Baz')"
                is PostgreSQLDialect -> "fooenum"
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

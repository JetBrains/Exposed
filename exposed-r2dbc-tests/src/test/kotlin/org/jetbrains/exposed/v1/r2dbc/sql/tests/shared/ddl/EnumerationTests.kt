package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.codec.EnumCodec
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

class EnumerationTests : R2dbcDatabaseTestsBase() {
    // NOTE: UNSUPPORTED r2dbc-h2
    // declined feature request for enum codec support: https://github.com/r2dbc/r2dbc-h2/issues/131
    private val supportsCustomEnumerationDB = TestDB.ALL_MYSQL_MARIADB + TestDB.ALL_POSTGRES

    internal enum class Foo {
        Bar, Baz
    }

    private fun connectWithEnumCodec(enum: String): R2dbcDatabase {
        val options = PostgresqlConnectionConfiguration.builder()
            .host("127.0.0.1")
            .port(3004)
            .username(TestDB.POSTGRESQL.user)
            .password(TestDB.POSTGRESQL.pass)
            .database("postgres")
            .options(mapOf("lc_messages" to "en_US.UTF-8"))
            // registered SQL enum name must match final name in database (PG always lower-case wrapped)
            .codecRegistrar(EnumCodec.builder().withEnum(enum.lowercase(), Foo::class.java).build())
            .build()
        val cxFactory = PostgresqlConnectionFactory(options)

        return R2dbcDatabase.connect(
            connectionFactory = cxFactory,
            databaseConfig = R2dbcDatabaseConfig {
                explicitDialect = PostgreSQLDialect()
            }
        )
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
                        is PostgreSQLDialect -> value
                        else -> value.name
                    }
                }
            )
        }
    }

    // NOTE: DAO part of test uncommented
    @Test
    fun testCustomEnumeration01() = runTest {
        Assumptions.assumeTrue(supportsCustomEnumerationDB.containsAll(TestDB.enabledDialects()))
        var sqlType = ""

//          class EnumEntity(id: EntityID<Int>) : IntEntity(id) {
//            var enum by EnumTable.enumColumn
//          }

//          val enumClass = object : IntEntityClass<EnumEntity>(EnumTable, EnumEntity::class.java) {}

        TestDB.enabledDialects().forEach { db ->
            val initialDb = db.connect()
            try {
                suspendTransaction(initialDb) {
                    sqlType = when (currentDialectTest) {
                        is MysqlDialect -> "ENUM('Bar', 'Baz')"
                        is PostgreSQLDialect -> "FooEnum"
                        else -> error("Unsupported case")
                    }
                    // PG enum codec can only be registered on connection if enum type already exists in database
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
                }

                // PG needs 1 db connection to simulate an existing enum type, then another to actually test the codec
                suspendTransaction(
                    db = if (db in TestDB.ALL_POSTGRES) connectWithEnumCodec(sqlType) else initialDb
                ) {
                    EnumTable.insert {
                        it[EnumTable.enumColumn] = Foo.Bar
                    }
                    assertEquals(Foo.Bar, EnumTable.selectAll().single()[EnumTable.enumColumn])

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
                }
            } finally {
                try {
                    suspendTransaction(initialDb) {
                        SchemaUtils.drop(EnumTable)
                    }
                } catch (ignore: Exception) {}
            }
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

                // No need for use of PG DB with enum codec because insert statement relies on database defaults (no binding)
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
    fun testCustomEnumerationWithReference() = runTest {
        Assumptions.assumeTrue(supportsCustomEnumerationDB.containsAll(TestDB.enabledDialects()))
        var sqlType = ""

        val referenceTable = object : Table("ref_table") {
            var referenceColumn: Column<Foo> = enumeration("ref_column")

            fun initRefColumn() {
                (columns as MutableList<Column<*>>).remove(referenceColumn)
                referenceColumn = reference("ref_column", EnumTable.enumColumn)
            }
        }

        TestDB.enabledDialects().forEach { db ->
            val initialDb = db.connect()
            try {
                suspendTransaction(initialDb) {
                    sqlType = when (currentDialectTest) {
                        is MysqlDialect -> "ENUM('Bar', 'Baz')"
                        is PostgreSQLDialect -> "RefEnum"
                        else -> error("Unsupported case")
                    }
                    // PG enum codec can only be registered on connection if enum type already exists in database
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
                }

                // PG needs 1 db connection to simulate an existing enum type, then another to actually test the codec
                suspendTransaction(
                    db = if (db in TestDB.ALL_POSTGRES) connectWithEnumCodec(sqlType) else initialDb
                ) {
                    val fooBar = Foo.Bar
                    val id1 = EnumTable.insert {
                        it[enumColumn] = fooBar
                    } get EnumTable.enumColumn
                    referenceTable.insert {
                        it[referenceColumn] = id1
                    }

                    assertEquals(fooBar, EnumTable.selectAll().single()[EnumTable.enumColumn])
                    assertEquals(fooBar, referenceTable.selectAll().single()[referenceTable.referenceColumn])
                }
            } finally {
                try {
                    suspendTransaction(initialDb) {
                        SchemaUtils.drop(referenceTable)
                        exec(EnumTable.indices.first().dropStatement().single())
                        SchemaUtils.drop(EnumTable)
                    }
                } catch (ignore: Exception) {
                }
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

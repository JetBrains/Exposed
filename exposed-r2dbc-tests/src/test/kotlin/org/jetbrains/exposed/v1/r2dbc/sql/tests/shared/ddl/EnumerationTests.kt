package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.codec.EnumCodec
import io.r2dbc.postgresql.codec.PostgresqlObjectId
import io.r2dbc.spi.Parameters
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
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
import org.junit.jupiter.api.assertNull
import kotlin.collections.single
import kotlin.test.assertNotNull

class EnumerationTests : R2dbcDatabaseTestsBase() {
    // NOTE: UNSUPPORTED r2dbc-h2
    // declined feature request for enum codec support: https://github.com/r2dbc/r2dbc-h2/issues/131
    private val supportsCustomEnumerationDB = TestDB.ALL_MYSQL_MARIADB + TestDB.ALL_POSTGRES

    internal enum class Foo {
        Bar, Baz;

        override fun toString(): String = "Foo Enum ToString: $name"
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

        /**
         * @param bindUntyped Sends the value as an untyped parameter on PostgreSQL, which the server then coerces
         * to the enum type. Use it to bind an enum over a connection with no [EnumCodec] registered; the resulting
         * value is not usable as a DDL default, so it is opt-in.
         * @param ddlSafeName On PostgreSQL, converts the enum to its plain name (e.g. `Bar`) instead of the raw enum
         * value. The raw enum's `toString()` is overridden here, so using it for a `DEFAULT` clause would emit an
         * invalid literal (`'Foo Enum ToString: Bar'`); this option keeps the DDL default valid. Only suitable when
         * the column value is never bound as a parameter (the insert relies on the database-side default).
         */
        internal fun initEnumColumn(sql: String, bindUntyped: Boolean = false, ddlSafeName: Boolean = false) {
            (columns as MutableList<Column<*>>).remove(enumColumn)
            enumColumn = customEnumeration(
                "enumColumn", sql,
                { value -> Foo.valueOf(value as String) },
                { value ->
                    when {
                        currentDialectTest !is PostgreSQLDialect -> value.name
                        bindUntyped -> Parameters.`in`(PostgresqlObjectId.UNSPECIFIED, value.name)
                        ddlSafeName -> value.name
                        else -> value
                    }
                }
            )
        }
    }

    @OptIn(ExperimentalR2dbcDaoApi::class)
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

            val enumClass = object : IntEntityClass<EnumEntity>(EnumTable, EnumEntity::class.java) {}

            try {
                if (currentDialectTest is PostgreSQLDialect) {
                    exec("DROP TYPE IF EXISTS FooEnum;")
                    exec("CREATE TYPE FooEnum AS ENUM ('Bar', 'Baz');")
                }
                // no EnumCodec is registered on this connection, so the enum has to go out untyped
                EnumTable.initEnumColumn(sqlType, bindUntyped = true)
                SchemaUtils.create(EnumTable)
                // drop shared table object's unique index if created in other test
                if (EnumTable.indices.isNotEmpty()) {
                    exec(EnumTable.indices.first().dropStatement().single())
                }
                EnumTable.insert {
                    it[enumColumn] = Foo.Bar
                }
                assertEquals(Foo.Bar, EnumTable.selectAll().single()[EnumTable.enumColumn])

                EnumTable.update {
                    it[enumColumn] = Foo.Baz
                }

                val entity = enumClass.newSuspend {
                    enum = Foo.Baz
                }
                assertEquals(Foo.Baz, entity.enum)

                val reloaded = assertNotNull(enumClass.reload(entity))
                assertEquals(Foo.Baz, reloaded.enum)
                reloaded.enum = Foo.Bar
                assertEquals(Foo.Bar, assertNotNull(enumClass.reload(reloaded, true)).enum)
            } finally {
                try {
                    SchemaUtils.drop(EnumTable)
                } catch (ignore: Exception) {
                }
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
                // `ddlSafeName` makes the PostgreSQL DEFAULT render as 'Bar' rather than the enum's overridden
                // toString(); this test never binds the enum (the insert uses the database-side default).
                EnumTable.initEnumColumn(sqlType, ddlSafeName = true)
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
                } catch (ignore: Exception) {
                }
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

    @Test
    fun testNullableEnumerationColumns() {
        val tester = object : Table("nullable_tester") {
            val enumColumn = enumeration<Foo>("enum_column").nullable()
            val enumNameColumn = enumerationByName<Foo>("enum_name_column", 32).nullable()
        }

        withTables(tester, tester) {
            tester.insert {
                it[enumColumn] = null
                it[enumNameColumn] = null
            }

            val result = tester.selectAll().single()
            assertNull(result[tester.enumColumn])
            assertNull(result[tester.enumNameColumn])
        }
    }
}

package org.jetbrains.exposed.sql.exposed.r2dbc.tests.shared.ddl

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.r2dbc.sql.exists
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test
import kotlin.test.expect

@Suppress("LargeClass")
class DDLTests : R2dbcDatabaseTestsBase() {
    @Test
    fun tableExists01() = runTest {
        val testTable = object : Table() {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
        }

        withTables {
            assertEquals(false, testTable.exists())
        }
    }

    @Test
    fun tableExists02() = runTest {
        val testTable = object : Table() {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTable) {
            assertEquals(true, testTable.exists())
        }
    }

    val unnamedTable = object : Table() {
        val id = integer("id")
        val name = varchar("name", length = 42)

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun unnamedTableWithQuotesSQL() = runTest {
        withTables(unnamedTable) { testDb ->
            val q = db.identifierManager.quoteString
            val tableName = if (currentDialectTest.needsQuotesWhenSymbolsInNames) {
                "$q${"unnamedTable$1".inProperCase()}$q"
            } else {
                "unnamedTable$1".inProperCase()
            }
            val integerType = currentDialectTest.dataTypeProvider.integerType()
            val varCharType = currentDialectTest.dataTypeProvider.varcharType(42)
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "$tableName " +
                    "(${"id".inProperCase()} $integerType PRIMARY KEY, $q${"name".inProperCase()}$q $varCharType NOT NULL" +
                    when (testDb) {
                        TestDB.ORACLE -> ", CONSTRAINT chk_unnamedTable$1_signed_integer_id CHECK (ID BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})"
                        else -> ""
                    } +
                    ")",
                unnamedTable.ddl
            )
        }
    }

    @Test
    fun namedEmptyTableWithoutQuotesSQL() = runTest {
        val testTable = object : Table("test_named_table") {}

        withDb(TestDB.H2_V2) {
            assertEquals("CREATE TABLE IF NOT EXISTS ${"test_named_table".inProperCase()}", testTable.ddl)
        }
    }

    @Test
    fun tableWithDifferentColumnTypesSQL01() = runTest {
        val testTable = object : Table("different_column_types") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 42)
            val age = integer("age").nullable()

            override val primaryKey = PrimaryKey(name)
        }

        withTables(excludeSettings = TestDB.ALL_MYSQL + TestDB.ALL_MARIADB + TestDB.ALL_ORACLE_LIKE, tables = arrayOf(testTable)) {
            val varCharType = currentDialectTest.dataTypeProvider.varcharType(42)
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${"different_column_types".inProperCase()} " +
                    "(${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerAutoincType()} NOT NULL, " +
                    "\"${"name".inProperCase()}\" $varCharType PRIMARY KEY, " +
                    "${"age".inProperCase()} ${currentDialectTest.dataTypeProvider.integerType()} NULL)",
                testTable.ddl
            )
        }
    }

    @Test
    fun tableWithDifferentColumnTypesSQL02() = runTest {
        val testTable = object : Table("with_different_column_types") {
            val id = integer("id")
            val name = varchar("name", 42)
            val age = integer("age").nullable()

            override val primaryKey = PrimaryKey(id, name)
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL_V5), tables = arrayOf(testTable)) { testDb ->
            val q = db.identifierManager.quoteString
            val varCharType = currentDialectTest.dataTypeProvider.varcharType(42)
            val tableDescription = "CREATE TABLE " + addIfNotExistsIfSupported() + "with_different_column_types".inProperCase()
            val idDescription = "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerType()}"
            val nameDescription = "$q${"name".inProperCase()}$q $varCharType"
            val ageDescription = "${"age".inProperCase()} ${db.dialect.dataTypeProvider.integerType()} NULL"
            val primaryKeyConstraint = "CONSTRAINT pk_with_different_column_types PRIMARY KEY (${"id".inProperCase()}, $q${"name".inProperCase()}$q)"
            val checkConstraint = when (testDb) {
                TestDB.ORACLE ->
                    ", CONSTRAINT chk_with_different_column_types_signed_integer_id CHECK (ID BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})" +
                        ", CONSTRAINT chk_with_different_column_types_signed_integer_age CHECK (AGE BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})"
                else -> ""
            }

            assertEquals("$tableDescription ($idDescription, $nameDescription, $ageDescription, $primaryKeyConstraint$checkConstraint)", testTable.ddl)
        }
    }

    @Test
    fun testPrimaryKeyOnTextColumnInH2() = runTest {
        val testTable = object : Table("test_pk_table") {
            val column1 = text("column_1")

            override val primaryKey = PrimaryKey(column1)
        }

        withDb(TestDB.ALL_H2_V2) {
            val h2Dialect = currentDialectTest as H2Dialect
            val isOracleMode = h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
            val singleColumnDescription = testTable.columns.single().descriptionDdl(false)

            assertTrue(singleColumnDescription.contains("PRIMARY KEY"))

            if (h2Dialect.isSecondVersion && !isOracleMode) {
                expect(Unit) {
                    SchemaUtils.create(testTable)
                    SchemaUtils.drop(testTable)
                }
            } else {
                expectException<R2dbcException> {
                    SchemaUtils.create(testTable)
                }
            }
        }
    }

    @Test
    fun testIndices01() = runTest {
        val t = object : Table("t1") {
            val id = integer("id")
            val name = varchar("name", 255).index()

            override val primaryKey = PrimaryKey(id)
        }

        withTables(t) {
            val alter = SchemaUtils.createIndex(t.indices[0])
            val q = db.identifierManager.quoteString
            assertEquals("CREATE INDEX ${"t1_name".inProperCase()} ON ${"t1".inProperCase()} ($q${"name".inProperCase()}$q)", alter)
        }
    }

    @Test
    fun testIndices02() = runTest {
        val t = object : Table("t2") {
            val id = integer("id")
            val lvalue = integer("lvalue")
            val rvalue = integer("rvalue")
            val name = varchar("name", 255).index()

            override val primaryKey = PrimaryKey(id)

            init {
                index(false, lvalue, rvalue)
            }
        }

        withTables(t) {
            val a1 = SchemaUtils.createIndex(t.indices[0])
            val q = db.identifierManager.quoteString
            assertEquals("CREATE INDEX ${"t2_name".inProperCase()} ON ${"t2".inProperCase()} ($q${"name".inProperCase()}$q)", a1)

            val a2 = SchemaUtils.createIndex(t.indices[1])
            assertEquals(
                "CREATE INDEX ${"t2_lvalue_rvalue".inProperCase()} ON ${"t2".inProperCase()} " +
                    "(${"lvalue".inProperCase()}, ${"rvalue".inProperCase()})",
                a2
            )
        }
    }

    @Test
    fun testIndexOnTextColumnInH2() = runTest {
        val testTable = object : Table("test_index_table") {
            val column1 = text("column_1")

            init {
                index(isUnique = false, column1)
            }
        }

        withDb(TestDB.ALL_H2_V2) {
            val h2Dialect = currentDialectTest as H2Dialect
            val isOracleMode = h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
            val tableProperName = testTable.tableName.inProperCase()
            val columnProperName = testTable.columns.single().name.inProperCase()
            val indexProperName = "${tableProperName}_$columnProperName"

            val indexStatement = SchemaUtils.createIndex(testTable.indices.single())

            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + tableProperName +
                    " (" + testTable.columns.single().descriptionDdl(false) + ")",
                testTable.ddl
            )

            if (h2Dialect.isSecondVersion && !isOracleMode) {
                assertEquals(
                    "CREATE INDEX $indexProperName ON $tableProperName ($columnProperName)",
                    indexStatement
                )
            } else {
                assertTrue(indexStatement.single().isEmpty())
            }
        }
    }

    @Test
    fun testUniqueIndices01() = runTest {
        val t = object : Table("t1") {
            val id = integer("id")
            val name = varchar("name", 255).uniqueIndex()

            override val primaryKey = PrimaryKey(id)
        }

        withTables(t) {
            val alter = SchemaUtils.createIndex(t.indices[0])
            val q = db.identifierManager.quoteString
            if (currentDialectTest is SQLiteDialect) {
                assertEquals("CREATE UNIQUE INDEX ${"t1_name".inProperCase()} ON ${"t1".inProperCase()} ($q${"name".inProperCase()}$q)", alter)
            } else {
                assertEquals("ALTER TABLE ${"t1".inProperCase()} ADD CONSTRAINT ${"t1_name_unique".inProperCase()} UNIQUE ($q${"name".inProperCase()}$q)", alter)
            }
        }
    }

    @Test
    fun testUniqueIndicesCustomName() = runTest {
        val t = object : Table("t1") {
            val id = integer("id")
            val name = varchar("name", 255).uniqueIndex("U_T1_NAME")

            override val primaryKey = PrimaryKey(id)
        }

        withTables(t) {
            val q = db.identifierManager.quoteString
            val alter = SchemaUtils.createIndex(t.indices[0])
            if (currentDialectTest is SQLiteDialect) {
                assertEquals("CREATE UNIQUE INDEX ${"U_T1_NAME"} ON ${"t1".inProperCase()} ($q${"name".inProperCase()}$q)", alter)
            } else {
                assertEquals("ALTER TABLE ${"t1".inProperCase()} ADD CONSTRAINT ${"U_T1_NAME"} UNIQUE ($q${"name".inProperCase()}$q)", alter)
            }
        }
    }

    @Test
    @Suppress("MaximumLineLength")
    fun testMultiColumnIndex() = runTest {
        val t = object : Table("t1") {
            val type = varchar("type", 255)
            val name = varchar("name", 255)

            init {
                index(false, name, type)
                uniqueIndex(type, name)
            }
        }

        withTables(t) {
            val indexAlter = SchemaUtils.createIndex(t.indices[0])
            val uniqueAlter = SchemaUtils.createIndex(t.indices[1])
            val q = db.identifierManager.quoteString
            assertEquals(
                "CREATE INDEX ${"t1_name_type".inProperCase()} ON ${"t1".inProperCase()} ($q${"name".inProperCase()}$q, $q${"type".inProperCase()}$q)",
                indexAlter
            )
            if (currentDialectTest is SQLiteDialect) {
                assertEquals(
                    "CREATE UNIQUE INDEX ${"t1_type_name".inProperCase()} ON ${"t1".inProperCase()} ($q${"type".inProperCase()}$q, $q${"name".inProperCase()}$q)",
                    uniqueAlter
                )
            } else {
                assertEquals(
                    "ALTER TABLE ${"t1".inProperCase()} ADD CONSTRAINT ${"t1_type_name_unique".inProperCase()} UNIQUE ($q${"type".inProperCase()}$q, $q${"name".inProperCase()}$q)",
                    uniqueAlter
                )
            }
        }
    }

    @Test
    fun testMultiColumnIndexCustomName() = runTest {
        val t = object : Table("t1") {
            val type = varchar("type", 255)
            val name = varchar("name", 255)

            init {
                index("I_T1_NAME_TYPE", false, name, type)
                uniqueIndex("U_T1_TYPE_NAME", type, name)
            }
        }

        withTables(t) {
            val indexAlter = SchemaUtils.createIndex(t.indices[0])
            val uniqueAlter = SchemaUtils.createIndex(t.indices[1])
            val q = db.identifierManager.quoteString
            assertEquals("CREATE INDEX ${"I_T1_NAME_TYPE"} ON ${"t1".inProperCase()} ($q${"name".inProperCase()}$q, $q${"type".inProperCase()}$q)", indexAlter)
            if (currentDialectTest is SQLiteDialect) {
                assertEquals(
                    "CREATE UNIQUE INDEX ${"U_T1_TYPE_NAME"} ON ${"t1".inProperCase()} ($q${"type".inProperCase()}$q, $q${"name".inProperCase()}$q)",
                    uniqueAlter
                )
            } else {
                assertEquals(
                    "ALTER TABLE ${"t1".inProperCase()} ADD CONSTRAINT ${"U_T1_TYPE_NAME"} UNIQUE ($q${"type".inProperCase()}$q, $q${"name".inProperCase()}$q)",
                    uniqueAlter
                )
            }
        }
    }

    @Test
    fun testIndexWithFunctions() = runTest {
        val tester = object : Table("tester") {
            val amount = integer("amount")
            val price = integer("price")
            val item = varchar("item", 32).nullable()

            init {
                index(customIndexName = "tester_plus_index", isUnique = false, functions = listOf(amount.plus(price)))
                index(isUnique = false, functions = listOf(item.lowerCase()))
                uniqueIndex(columns = arrayOf(price), functions = listOf(Coalesce(item, stringLiteral("*"))))
            }
        }

        withDb { testDb ->
            val functionsNotSupported = testDb in TestDB.ALL_MARIADB + TestDB.ALL_H2_V2 + TestDB.SQLSERVER + TestDB.MYSQL_V5

            val tableProperName = tester.tableName.inProperCase()
            val priceColumnName = tester.price.nameInDatabaseCase()
            val uniqueIndexName = "tester_price_coalesce_unique".inProperCase()
            val (p1, p2) = when (testDb) {
                // MySql 8 requires double parenthesis on function index in order to differentiate it from columns
                // https://dev.mysql.com/doc/refman/8.0/en/create-index.html#create-index-functional-key-parts
                TestDB.MYSQL_V8 -> "(" to ")"
                else -> "" to ""
            }
            val functionStrings = when (testDb) {
                TestDB.ORACLE -> listOf("(amount + price)", "LOWER(item)", "COALESCE(item, '*')").map(String::inProperCase)
                else -> listOf(
                    tester.amount.plus(tester.price).toString(),
                    "$p1${tester.item.lowerCase()}$p2",
                    "$p1${Coalesce(tester.item, stringLiteral("*"))}$p2"
                )
            }

            val expectedStatements = if (functionsNotSupported) {
                List(3) { "" }
            } else {
                listOf(
                    "CREATE INDEX tester_plus_index ON $tableProperName (${functionStrings[0]})",
                    "CREATE INDEX ${"tester_lower".inProperCase()} ON $tableProperName (${functionStrings[1]})",
                    "CREATE UNIQUE INDEX $uniqueIndexName ON $tableProperName ($priceColumnName, ${functionStrings[2]})"
                )
            }

            repeat(3) { i ->
                val actualStatement = SchemaUtils.createIndex(tester.indices[i])
                assertEquals(expectedStatements[i], actualStatement)
            }
        }
    }

    @Test
    fun testEscapeStringColumnType() = runTest {
        withDb(TestDB.H2_V2) {
            assertEquals("VARCHAR(255) COLLATE utf8_general_ci", VarCharColumnType(collate = "utf8_general_ci").sqlType())
            assertEquals("VARCHAR(255) COLLATE injected''code", VarCharColumnType(collate = "injected'code").sqlType())
            assertEquals("'value'", VarCharColumnType().nonNullValueToString("value"))
            assertEquals("'injected''value'", VarCharColumnType().nonNullValueToString("injected'value"))

            assertEquals("TEXT COLLATE utf8_general_ci", TextColumnType(collate = "utf8_general_ci").sqlType())
            assertEquals("TEXT COLLATE injected''code", TextColumnType(collate = "injected'code").sqlType())
            assertEquals("'value'", TextColumnType().nonNullValueToString("value"))
            assertEquals("'injected''value'", TextColumnType().nonNullValueToString("injected'value"))
        }
    }

    object Table1 : IntIdTable() {
        val table2 = reference("teamId", Table2, onDelete = ReferenceOption.NO_ACTION)
    }

    object Table2 : IntIdTable() {
        val table1 = optReference("teamId", Table1, onDelete = ReferenceOption.NO_ACTION)
    }

    @Test
    fun testDeleteMissingTable() = runTest {
        val missingTable = Table("missingTable")
        withDb {
            SchemaUtils.drop(missingTable)
        }
    }

    @Test
    fun testEqOperatorWithoutDBConnection() = runTest {
        object : Table("test") {
            val testColumn: Column<Int?> = integer("test_column").nullable()

            init {
                check("test_constraint") {
                    testColumn.isNotNull() eq Op.TRUE
                }
            }
        }
    }

    @Test
    fun testNeqOperatorWithoutDBConnection() = runTest {
        object : Table("test") {
            val testColumn: Column<Int?> = integer("test_column").nullable()

            init {
                check("test_constraint") {
                    testColumn.isNotNull() neq Op.TRUE
                }
            }
        }
    }

    @Test
    fun testInnerJoinWithMultipleForeignKeys() = runTest {
        val users = object : IntIdTable() {}

        val subscriptions = object : LongIdTable() {
            val user = reference("user", users)
            val adminBy = reference("adminBy", users).nullable()
        }

        withTables(users, subscriptions) {
            val query = subscriptions.join(users, JoinType.INNER, additionalConstraint = { subscriptions.user eq users.id }).selectAll()
            assertEquals(0L, query.count())
        }
    }
}

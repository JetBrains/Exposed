package org.jetbrains.exposed.sql.tests.shared

import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Assume
import org.junit.Test
import org.postgresql.util.PGobject
import java.util.*
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.test.expect

@Suppress("LargeClass")
class DDLTests : DatabaseTestsBase() {

    @Test
    fun tableExists01() {
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
    fun tableExists02() {
        val testTable = object : Table() {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
        }

        withTables(testTable) {
            assertEquals(true, testTable.exists())
        }
    }

    // EXPOSED-206
    @Test
    fun testKeywordIdentifiersWithoutOptIn() {
        val keywords = listOf("Integer", "name")
        val tester = object : Table(keywords[0]) {
            val name = varchar(keywords[1], 32)
        }

        withDb(excludeSettings = TestDB.allH2TestDB - TestDB.H2) {
            assertFalse(db.config.preserveKeywordCasing)

            SchemaUtils.create(tester)
            assertTrue(tester.exists())

            val (tableName, columnName) = keywords.map {
                when (currentDialectTest) {
                    is MysqlDialect -> "`$it`"
                    is PostgreSQLDialect -> "\"${it.lowercase()}\""
                    is OracleDialect, is H2Dialect -> "\"${it.uppercase()}\""
                    else -> "\"$it\""
                }
            }

            val expectedCreate = "CREATE TABLE ${addIfNotExistsIfSupported()}$tableName (" +
                "$columnName ${tester.name.columnType.sqlType()} NOT NULL)"
            assertEquals(expectedCreate, tester.ddl.single())

            // check that insert and select statement identifiers also match in DB without throwing SQLException
            tester.insert { it[name] = "A" }

            val expectedSelect = "SELECT $tableName.$columnName FROM $tableName"
            tester.selectAll().also {
                assertEquals(expectedSelect, it.prepareSQL(this, prepared = false))
            }

            // check that identifiers match with returned jdbc metadata
            val statements = SchemaUtils.statementsRequiredToActualizeScheme(tester)
            assertTrue(statements.isEmpty())

            SchemaUtils.drop(tester)
        }
    }

    private object FlagTestTable : Table("BooLean") {
        val name = varchar("name", 32)
    }

    // EXPOSED-206
    @Test
    fun testKeywordIdentifiersLogWarningWithoutOptIn() {
        withDb {
            val logCaptor = LogCaptor.forName(exposedLogger.name)
            try {
                SchemaUtils.create(FlagTestTable)
                assertEquals(2, logCaptor.warnLogs.size)
                logCaptor.clearLogs()

                FlagTestTable.insert { it[name] = "A" }
                FlagTestTable.selectAll().toList()
                SchemaUtils.drop(FlagTestTable)
                assertEquals(0, logCaptor.warnLogs.size)
            } finally {
                logCaptor.clearLogs()
            }
        }
    }

    private val keywordFlagDB by lazy {
        Database.connect(
            url = "jdbc:h2:mem:flagtest;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
            databaseConfig = DatabaseConfig {
                @OptIn(ExperimentalKeywordApi::class)
                preserveKeywordCasing = true
            }
        )
    }

    // EXPOSED-206
    @Test
    fun testKeywordIdentifiersWithOptIn() {
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledDialects())

        val keywords = listOf("data", "public", "key", "constraint")
        val keywordTable = object : Table(keywords[0]) {
            val public = bool(keywords[1])
            val data = integer(keywords[2])
            val constraint = varchar(keywords[3], 32)
        }

        transaction(keywordFlagDB) {
            assertTrue(db.config.preserveKeywordCasing)

            SchemaUtils.create(keywordTable)
            assertTrue(keywordTable.exists())

            val (tableName, publicName, dataName, constraintName) = keywords.map { "\"$it\"" }

            val expectedCreate = "CREATE TABLE ${addIfNotExistsIfSupported()}$tableName (" +
                "$publicName ${keywordTable.public.columnType.sqlType()} NOT NULL, " +
                "$dataName ${keywordTable.data.columnType.sqlType()} NOT NULL, " +
                "$constraintName ${keywordTable.constraint.columnType.sqlType()} NOT NULL)"
            assertEquals(expectedCreate, keywordTable.ddl.single())

            // check that insert and select statement identifiers also match in DB without throwing SQLException
            keywordTable.insert {
                it[public] = false
                it[data] = 999
                it[constraint] = "unique"
            }

            val expectedSelect = "SELECT $tableName.$publicName, $tableName.$dataName, $tableName.$constraintName FROM $tableName"
            keywordTable.selectAll().also {
                assertEquals(expectedSelect, it.prepareSQL(this, prepared = false))
            }

            // check that identifiers match with returned jdbc metadata
            val statements = SchemaUtils.statementsRequiredToActualizeScheme(keywordTable)
            assertTrue(statements.isEmpty())

            SchemaUtils.drop(keywordTable)
        }
        TransactionManager.closeAndUnregister(keywordFlagDB)
    }

    // EXPOSED-206
    @Test
    fun testKeywordIdentifiersLogNoWarningWithOptIn() {
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledDialects())

        val logCaptor = LogCaptor.forName(exposedLogger.name)
        try {
            transaction(keywordFlagDB) {
                SchemaUtils.create(FlagTestTable)
                assertEquals(0, logCaptor.warnLogs.size)
                logCaptor.clearLogs()

                SchemaUtils.drop(FlagTestTable)
                assertEquals(0, logCaptor.warnLogs.size)
            }
        } finally {
            logCaptor.clearLogs()
            TransactionManager.closeAndUnregister(keywordFlagDB)
        }
    }

    // Placed outside test function to shorten generated name
    val unnamedTable = object : Table() {
        val id = integer("id")
        val name = varchar("name", length = 42)

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun unnamedTableWithQuotesSQL() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tables = arrayOf(unnamedTable)) {
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
                    "(${"id".inProperCase()} $integerType PRIMARY KEY, $q${"name".inProperCase()}$q $varCharType NOT NULL)",
                unnamedTable.ddl
            )
        }
    }

    @Test
    fun unnamedTableWithQuotesSQLInSQLite() {
        withDb(TestDB.SQLITE) {
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
                    "(${"id".inProperCase()} $integerType NOT NULL PRIMARY KEY, $q${"name".inProperCase()}$q $varCharType NOT NULL)",
                unnamedTable.ddl
            )
        }
    }

    @Test
    fun namedEmptyTableWithoutQuotesSQL() {
        val testTable = object : Table("test_named_table") {}

        withDb(TestDB.H2) {
            assertEquals("CREATE TABLE IF NOT EXISTS ${"test_named_table".inProperCase()}", testTable.ddl)
            DMLTestsData.Users.select {
                exists(DMLTestsData.UserData.select { DMLTestsData.Users.id eq DMLTestsData.UserData.user_id })
            }
        }
    }

    @Test
    fun tableWithDifferentColumnTypesSQL01() {
        val testTable = object : Table("different_column_types") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 42)
            val age = integer("age").nullable()

            override val primaryKey = PrimaryKey(name)
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.ORACLE, TestDB.MARIADB, TestDB.SQLITE, TestDB.H2_ORACLE), tables = arrayOf(testTable)) {
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
    fun tableWithDifferentColumnTypesSQL02() {
        val testTable = object : Table("with_different_column_types") {
            val id = integer("id")
            val name = varchar("name", 42)
            val age = integer("age").nullable()

            override val primaryKey = PrimaryKey(id, name)
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.SQLITE), tables = arrayOf(testTable)) {
            val q = db.identifierManager.quoteString
            val varCharType = currentDialectTest.dataTypeProvider.varcharType(42)
            val tableDescription = "CREATE TABLE " + addIfNotExistsIfSupported() + "with_different_column_types".inProperCase()
            val idDescription = "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerType()}"
            val nameDescription = "$q${"name".inProperCase()}$q $varCharType"
            val ageDescription = "${"age".inProperCase()} ${db.dialect.dataTypeProvider.integerType()} NULL"
            val constraint = "CONSTRAINT pk_with_different_column_types PRIMARY KEY (${"id".inProperCase()}, $q${"name".inProperCase()}$q)"

            assertEquals("$tableDescription ($idDescription, $nameDescription, $ageDescription, $constraint)", testTable.ddl)
        }
    }

    @Test
    fun tableWithDifferentColumnTypesInSQLite() {
        val testTable = object : Table("with_different_column_types") {
            val id = integer("id")
            val name = varchar("name", 42)
            val age = integer("age").nullable()

            override val primaryKey = PrimaryKey(id, name)
        }

        withDb(TestDB.SQLITE) {
            val q = db.identifierManager.quoteString

            val tableDescription = "CREATE TABLE " + addIfNotExistsIfSupported() + "with_different_column_types".inProperCase()
            val idDescription = "${"id".inProperCase()} ${currentDialectTest.dataTypeProvider.integerType()} NOT NULL"
            val nameDescription = "$q${"name".inProperCase()}$q VARCHAR(42) NOT NULL"
            val ageDescription = "${"age".inProperCase()} ${db.dialect.dataTypeProvider.integerType()} NULL"
            val constraint = "CONSTRAINT pk_with_different_column_types PRIMARY KEY (${"id".inProperCase()}, $q${"name".inProperCase()}$q)"

            assertEquals("$tableDescription ($idDescription, $nameDescription, $ageDescription, $constraint)", testTable.ddl)
        }
    }

    @Test
    fun tableWithMultiPKandAutoIncrement() {
        val foo = object : IdTable<Long>("FooTable") {
            val bar = integer("bar")
            override val id: Column<EntityID<Long>> = long("id").entityId().autoIncrement()

            override val primaryKey = PrimaryKey(bar, id)
        }

        withTables(excludeSettings = listOf(TestDB.SQLITE), foo) {
            foo.insert {
                it[foo.bar] = 1
            }
            foo.insert {
                it[foo.bar] = 2
            }

            val result = foo.selectAll().map { it[foo.id] to it[foo.bar] }
            assertEquals(2, result.size)
            assertEquals(1, result[0].second)
            assertEquals(2, result[1].second)
        }

        withDb(TestDB.SQLITE) {
            expectException<UnsupportedByDialectException> {
                SchemaUtils.create(foo)
            }
        }
    }

    @Test
    fun testPrimaryKeyOnTextColumnInH2() {
        val testTable = object : Table("test_pk_table") {
            val column1 = text("column_1")

            override val primaryKey = PrimaryKey(column1)
        }

        withDb(TestDB.allH2TestDB) {
            val h2Dialect = currentDialectTest as H2Dialect
            val isOracleMode = h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
            val singleColumnDescription = testTable.columns.single().descriptionDdl(false)

            assertTrue(singleColumnDescription.contains("PRIMARY KEY"))

            if (h2Dialect.isSecondVersion && !isOracleMode) {
                expect(Unit) {
                    SchemaUtils.create(testTable)
                }
            } else {
                expectException<ExposedSQLException> {
                    SchemaUtils.create(testTable)
                }
            }
        }
    }

    @Test
    fun testIndices01() {
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
    fun testIndices02() {
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
    fun testIndexOnTextColumnInH2() {
        val testTable = object : Table("test_index_table") {
            val column1 = text("column_1")

            init {
                index(isUnique = false, column1)
            }
        }

        withDb(TestDB.allH2TestDB) {
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
    fun testUniqueIndices01() {
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
    fun testUniqueIndicesCustomName() {
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
    fun testMultiColumnIndex() {
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
    fun testMultiColumnIndexCustomName() {
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
    fun testIndexWithFunctions() {
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
            val tableProperName = tester.tableName.inProperCase()
            val priceColumnName = tester.price.nameInDatabaseCase()
            val uniqueIndexName = "tester_price_coalesce${if (testDb == TestDB.SQLITE) "" else "_unique"}".inProperCase()
            val (p1, p2) = if (testDb == TestDB.MYSQL) "(" to ")" else "" to ""
            val functionStrings = when (testDb) {
                TestDB.SQLITE, TestDB.ORACLE -> listOf("(amount + price)", "LOWER(item)", "COALESCE(item, '*')").map(String::inProperCase)
                else -> listOf(
                    tester.amount.plus(tester.price).toString(),
                    "$p1${tester.item.lowerCase()}$p2",
                    "$p1${Coalesce(tester.item, stringLiteral("*"))}$p2"
                )
            }

            val functionsNotSupported = testDb in (TestDB.allH2TestDB + TestDB.SQLSERVER + TestDB.MARIADB) || isOldMySql()
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
    fun testBlob() {
        val t = object : Table("t1") {
            val id = integer("id").autoIncrement()
            val b = blob("blob")

            override val primaryKey = PrimaryKey(id)
        }

        withTables(t) {
            val shortBytes = "Hello there!".toByteArray()
            val longBytes = Random.nextBytes(1024)
            val shortBlob = ExposedBlob(shortBytes)
            val longBlob = ExposedBlob(longBytes)

            val id1 = t.insert {
                it[t.b] = shortBlob
            } get (t.id)

            val id2 = t.insert {
                it[t.b] = longBlob
            } get (t.id)

            val id3 = t.insert {
                it[t.b] = blobParam(ExposedBlob(shortBytes))
            } get (t.id)

            val readOn1 = t.select { t.id eq id1 }.first()[t.b]
            val text1 = String(readOn1.bytes)
            val text2 = readOn1.inputStream.bufferedReader().readText()

            assertEquals("Hello there!", text1)
            assertEquals("Hello there!", text2)

            val readOn2 = t.select { t.id eq id2 }.first()[t.b]
            val bytes1 = readOn2.bytes
            val bytes2 = readOn2.inputStream.readBytes()

            assertTrue(longBytes.contentEquals(bytes1))
            assertTrue(longBytes.contentEquals(bytes2))

            val bytes3 = t.select { t.id eq id3 }.first()[t.b].inputStream.readBytes()
            assertTrue(shortBytes.contentEquals(bytes3))
        }
    }

    @Test
    fun testBlobDefault() {
        val defaultBlobStr = "test"
        val defaultBlob = ExposedBlob(defaultBlobStr.encodeToByteArray())

        val testTable = object : Table("TestTable") {
            val number = integer("number")
            val blobWithDefault = blob("blobWithDefault")
                .default(defaultBlob)
        }

        withDb { testDb ->
            when (testDb) {
                TestDB.MYSQL -> {
                    expectException<ExposedSQLException> {
                        SchemaUtils.create(testTable)
                    }
                }
                else -> {
                    SchemaUtils.drop(testTable)
                    SchemaUtils.create(testTable)

                    testTable.insert {
                        it[number] = 1
                    }
                    assertEquals(defaultBlobStr, String(testTable.selectAll().first()[testTable.blobWithDefault].bytes))

                    SchemaUtils.drop(testTable)
                }
            }
        }
    }

    @Test
    fun testBinaryWithoutLength() {
        val tableWithBinary = object : Table("TableWithBinary") {
            val binaryColumn = binary("binaryColumn")
        }

        fun SizedIterable<ResultRow>.readAsString() = map { String(it[tableWithBinary.binaryColumn]) }

        withDb(listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.SQLITE, TestDB.H2_PSQL)) {
            val exposedBytes = "Exposed".toByteArray()
            val kotlinBytes = "Kotlin".toByteArray()

            SchemaUtils.create(tableWithBinary)

            tableWithBinary.insert {
                it[tableWithBinary.binaryColumn] = exposedBytes
            }
            val insertedExposed = tableWithBinary.selectAll().readAsString().single()

            assertEquals("Exposed", insertedExposed)

            tableWithBinary.insert {
                it[tableWithBinary.binaryColumn] = kotlinBytes
            }

            assertEqualCollections(tableWithBinary.selectAll().readAsString(), "Exposed", "Kotlin")

            val insertedKotlin = tableWithBinary.select { tableWithBinary.binaryColumn eq kotlinBytes }.readAsString()
            assertEqualCollections(insertedKotlin, "Kotlin")

            SchemaUtils.drop(tableWithBinary)
        }
    }

    @Test
    fun testBinary() {
        val t = object : Table("t") {
            val binary = binary("bytes", 10).nullable()
            val byteCol = binary("byteCol", 1).clientDefault { byteArrayOf(0) }
        }

        fun SizedIterable<ResultRow>.readAsString() = map { it[t.binary]?.let { String(it) } }

        withTables(t) {
            t.insert { it[t.binary] = "Hello!".toByteArray() }

            val hello = t.selectAll().readAsString().single()

            assertEquals("Hello!", hello)

            val worldBytes = "World!".toByteArray()

            t.insert {
                it[t.binary] = worldBytes
                it[t.byteCol] = byteArrayOf(1)
            }

            assertEqualCollections(t.selectAll().readAsString(), "Hello!", "World!")

            t.insert {
                it[t.binary] = null
                it[t.byteCol] = byteArrayOf(2)
            }

            assertEqualCollections(t.selectAll().readAsString(), "Hello!", "World!", null)

            val world = t.select { t.binary eq worldBytes }.readAsString()
            assertEqualCollections(world, "World!")

            val worldByBitCol = t.select { t.byteCol eq byteArrayOf(1) }.readAsString()
            assertEqualCollections(worldByBitCol, "World!")
        }
    }

    @Test
    fun testEscapeStringColumnType() {
        withDb(TestDB.H2) {
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

    private abstract class EntityTable(name: String = "") : IdTable<String>(name) {
        override val id: Column<EntityID<String>> = varchar("id", 64).clientDefault { UUID.randomUUID().toString() }.entityId()

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun complexTest01() {
        val user = object : EntityTable() {
            val name = varchar("name", 255)
            val email = varchar("email", 255)
        }

        val repository = object : EntityTable() {
            val name = varchar("name", 255)
        }

        val userToRepo = object : EntityTable() {
            val user = reference("user", user)
            val repo = reference("repo", repository)
        }

        withTables(user, repository, userToRepo) {
            user.insert {
                it[user.name] = "foo"
                it[user.email] = "bar"
            }

            val userID = user.selectAll().single()[user.id]

            repository.insert {
                it[repository.name] = "foo"
            }
            val repo = repository.selectAll().single()[repository.id]

            userToRepo.insert {
                it[userToRepo.user] = userID
                it[userToRepo.repo] = repo
            }

            assertEquals(1L, userToRepo.selectAll().count())
            userToRepo.insert {
                it[userToRepo.user] = userID
                it[userToRepo.repo] = repo
            }

            assertEquals(2L, userToRepo.selectAll().count())
        }
    }

    object Table1 : IntIdTable() {
        val table2 = reference("teamId", Table2, onDelete = ReferenceOption.NO_ACTION)
    }

    object Table2 : IntIdTable() {
        val table1 = optReference("teamId", Table1, onDelete = ReferenceOption.NO_ACTION)
    }

    @Test
    fun testCrossReference() {
        withTables(Table1, Table2) {
            val table2id = Table2.insertAndGetId {}
            val table1id = Table1.insertAndGetId {
                it[table2] = table2id
            }

            Table2.insertAndGetId {
                it[table1] = table1id
            }

            assertEquals(1L, Table1.selectAll().count())
            assertEquals(2L, Table2.selectAll().count())

            Table2.update {
                it[table1] = null
            }

            Table1.deleteAll()
            Table2.deleteAll()

            if (currentDialectTest !is SQLiteDialect) {
                exec(Table2.table1.foreignKey!!.dropStatement().single())
            }
        }
    }

    @Test
    fun testUUIDColumnType() {
        val node = object : IntIdTable("node") {
            val uuid = uuid("uuid")
        }

        withTables(node) {
            val key: UUID = UUID.randomUUID()
            val id = node.insertAndGetId { it[uuid] = key }
            assertNotNull(id)
            val uidById = node.select { node.id eq id }.singleOrNull()?.get(node.uuid)
            assertEquals(key, uidById)
            val uidByKey = node.select { node.uuid eq key }.singleOrNull()?.get(node.uuid)
            assertEquals(key, uidByKey)
        }
    }

    @Test
    fun testBooleanColumnType() {
        val boolTable = object : Table("booleanTable") {
            val bool = bool("bool")
        }

        withTables(boolTable) {
            boolTable.insert {
                it[bool] = true
            }
            val result = boolTable.selectAll().toList()
            assertEquals(1, result.size)
            assertEquals(true, result.single()[boolTable.bool])
        }
    }

    @Test
    fun tableWithDifferentTextTypes() {
        val testTable = object : Table("different_text_column_types") {
            val id = integer("id").autoIncrement()
            val txt = text("txt")
            val txtMed = mediumText("txt_med")
            val txtLong = largeText("txt_large")

            override val primaryKey: PrimaryKey = PrimaryKey(id)
        }

        withDb(listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.MYSQL, TestDB.H2_PSQL)) { testDb ->
            SchemaUtils.create(testTable)
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${"different_text_column_types".inProperCase()} " +
                    "(${testTable.id.nameInDatabaseCase()} ${currentDialectTest.dataTypeProvider.integerAutoincType()} PRIMARY KEY, " +
                    "${testTable.txt.nameInDatabaseCase()} ${currentDialectTest.dataTypeProvider.textType()} NOT NULL, " +
                    "${testTable.txtMed.nameInDatabaseCase()} ${currentDialectTest.dataTypeProvider.mediumTextType()} NOT NULL, " +
                    "${testTable.txtLong.nameInDatabaseCase()} ${currentDialectTest.dataTypeProvider.largeTextType()} NOT NULL)",
                testTable.ddl
            )

            // double check that different types were applied indeed
            assert(
                testDb != TestDB.MYSQL ||
                    (
                        currentDialectTest.dataTypeProvider.textType() != currentDialectTest.dataTypeProvider.mediumTextType() &&
                            currentDialectTest.dataTypeProvider.mediumTextType() != currentDialectTest.dataTypeProvider.largeTextType() &&
                            currentDialectTest.dataTypeProvider.textType() != currentDialectTest.dataTypeProvider.largeTextType()
                        )
            )

            testTable.insert {
                it[txt] = "1Txt"
                it[txtMed] = "1TxtMed"
                it[txtLong] = "1TxtLong"
            }

            val concat = SqlExpressionBuilder.concat(
                separator = " ",
                listOf(LowerCase(testTable.txt), UpperCase(testTable.txtMed), LowerCase(testTable.txtLong))
            )

            // just to be sure new type didn't break the functions
            testTable.slice(concat).selectAll().forEach {
                assertEquals(it[concat], "1txt 1TXTMED 1txtlong")
            }
        }
    }

    @Test
    fun testDeleteMissingTable() {
        val missingTable = Table("missingTable")
        withDb {
            SchemaUtils.drop(missingTable)
        }
    }

    @Test
    fun testCheckConstraint01() {
        val checkTable = object : Table("checkTable") {
            val positive = integer("positive").check { it greaterEq 0 }
            val negative = integer("negative").check("subZero") { it less 0 }
        }

        withTables(checkTable) {
            if (!isOldMySql()) {
                checkTable.insert {
                    it[positive] = 42
                    it[negative] = -14
                }

                assertEquals(1L, checkTable.selectAll().count())

                assertFailAndRollback("Check constraint 1") {
                    checkTable.insert {
                        it[positive] = -472
                        it[negative] = -354
                    }
                }

                assertFailAndRollback("Check constraint 2") {
                    checkTable.insert {
                        it[positive] = 538
                        it[negative] = 915
                    }
                }
            }
        }
    }

    @Test
    fun testCheckConstraint02() {
        val checkTable = object : Table("multiCheckTable") {
            val positive = integer("positive")
            val negative = integer("negative")

            init {
                check("multi") { (negative less 0) and (positive greaterEq 0) }
            }
        }

        withTables(checkTable) {
            if (!isOldMySql()) {
                checkTable.insert {
                    it[positive] = 57
                    it[negative] = -32
                }

                assertEquals(1L, checkTable.selectAll().count())

                assertFailAndRollback("Check constraint 1") {
                    checkTable.insert {
                        it[positive] = -47
                        it[negative] = -35
                    }
                }

                assertFailAndRollback("Check constraint 2") {
                    checkTable.insert {
                        it[positive] = 53
                        it[negative] = 91
                    }
                }
            }
        }
    }

    @Test
    fun testCreateAndDropCheckConstraint() {
        val tester = object : Table("tester") {
            val amount = integer("amount")
        }

        withTables(tester) { testDb ->
            val constraintName = "check_tester_positive"
            val constraintOp = "${"amount".inProperCase()} > 0"
            val (createConstraint, dropConstraint) = CheckConstraint("tester", constraintName, constraintOp).run {
                createStatement() to dropStatement()
            }

            if (testDb == TestDB.SQLITE || isOldMySql()) { // cannot alter existing check constraint
                assertTrue(createConstraint.isEmpty() && dropConstraint.isEmpty())
            } else {
                val negative = -9
                tester.insert { it[amount] = negative }

                // fails to create check constraint because negative values already stored
                assertFailAndRollback("Check constraint violation") {
                    exec(createConstraint.single())
                }

                tester.deleteAll()
                exec(createConstraint.single())

                assertFailAndRollback("Check constraint violation") {
                    tester.insert { it[amount] = negative }
                }

                exec(dropConstraint.single())

                tester.insert { it[amount] = negative }
                assertEquals(negative, tester.selectAll().single()[tester.amount])
            }
        }
    }

    @Test
    fun testEqOperatorWithoutDBConnection() {
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
    fun testNeqOperatorWithoutDBConnection() {
        object : Table("test") {
            val testColumn: Column<Int?> = integer("test_column").nullable()

            init {
                check("test_constraint") {
                    testColumn.isNotNull() neq Op.TRUE
                }
            }
        }
    }

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

    object KeyWordTable : IntIdTable(name = "keywords") {
        val bool = bool("bool")
    }

    // https://github.com/JetBrains/Exposed/issues/112
    @Test
    fun testDropTableFlushesCache() {
        withDb {
            class Keyword(id: EntityID<Int>) : IntEntity(id) {
                var bool by KeyWordTable.bool
            }

            val keywordEntityClass = object : IntEntityClass<Keyword>(KeyWordTable, Keyword::class.java) {}

            SchemaUtils.create(KeyWordTable)

            keywordEntityClass.new { bool = true }

            SchemaUtils.drop(KeyWordTable)
        }
    }

    // https://github.com/JetBrains/Exposed/issues/522
    @Test
    fun testInnerJoinWithMultipleForeignKeys() {
        val users = object : IntIdTable() {}

        val subscriptions = object : LongIdTable() {
            val user = reference("user", users)
            val adminBy = reference("adminBy", users).nullable()
        }

        withTables(subscriptions) {
            val query = subscriptions.join(users, JoinType.INNER, additionalConstraint = { subscriptions.user eq users.id }).selectAll()
            assertEquals(0L, query.count())
        }
    }

    @Test
    fun createTableWithForeignKeyToAnotherSchema() {
        val one = prepareSchemaForTest("one")
        val two = prepareSchemaForTest("two")
        withSchemas(two, one) {
            SchemaUtils.drop(TableFromSchemeOne)
            SchemaUtils.create(TableFromSchemeOne)

            if (currentDialectTest is OracleDialect) {
                exec("GRANT REFERENCES ON ${TableFromSchemeOne.tableName} to TWO")
            }

            SchemaUtils.drop(TableFromSchemeTwo)
            SchemaUtils.create(TableFromSchemeTwo)
            val idFromOne = TableFromSchemeOne.insertAndGetId { }

            TableFromSchemeTwo.insert {
                it[reference] = idFromOne
            }

            assertEquals(1L, TableFromSchemeOne.selectAll().count())
            assertEquals(1L, TableFromSchemeTwo.selectAll().count())

            if (currentDialectTest is SQLServerDialect) {
                SchemaUtils.drop(TableFromSchemeTwo, TableFromSchemeOne)
            }
        }
    }

    object TableFromSchemeOne : IntIdTable("one.test")

    object TableFromSchemeTwo : IntIdTable("two.test") {
        val reference = reference("testOne", TableFromSchemeOne)
    }

    @Test
    fun testCompositeFKReferencingUniqueIndex() {
        val tableA = object : Table("TableA") {
            val idA = integer("id_a")
            val idB = integer("id_b")

            init {
                uniqueIndex(idA, idB)
            }
        }

        val tableB = object : Table("TableB") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            val idC = integer("id_c")
            override val primaryKey = PrimaryKey(idA, idB, idC)

            init {
                foreignKey(idA to tableA.idA, idB to tableA.idB)
            }
        }

        withTables(excludeSettings = listOf(TestDB.SQLITE), tableA, tableB) {
            tableA.insert {
                it[idA] = 1
                it[idB] = 2
            }

            tableB.insert {
                it[idA] = 1
                it[idB] = 2
                it[idC] = 3
            }

            assertFailAndRollback("check violation composite foreign key constraint (insert key into child table not present in parent table)") {
                tableB.insert {
                    it[idA] = 1
                    it[idB] = 1
                    it[idC] = 3
                }
            }
        }
    }

    @Test
    fun testCompositeFKReferencingPrimaryKey() {
        val tableA = object : Table("TableA") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            override val primaryKey = PrimaryKey(idA, idB)
        }

        val tableB = object : Table("TableB") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            val idC = integer("id_c")
            override val primaryKey = PrimaryKey(idA, idB, idC)

            init {
                foreignKey(idA, idB, target = tableA.primaryKey)
            }
        }

        withTables(excludeSettings = listOf(TestDB.SQLITE), tableA, tableB) {
            tableA.insert {
                it[idA] = 1
                it[idB] = 2
            }

            tableB.insert {
                it[idA] = 1
                it[idB] = 2
                it[idC] = 3
            }

            assertFailAndRollback("check violation composite foreign key constraint (insert key into child table not present in parent table)") {
                tableB.insert {
                    it[idA] = 1
                    it[idB] = 1
                    it[idC] = 3
                }
            }
        }
    }

    @Test
    fun testMultipleFK() {
        val tableA = object : Table("TableA") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            override val primaryKey = PrimaryKey(idA, idB)
        }

        val tableC = object : Table("TableC") {
            val idC = integer("id_c").uniqueIndex()
        }

        val tableB = object : Table("TableB") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            val idC = integer("id_c") references tableC.idC
            override val primaryKey = PrimaryKey(idA, idB, idC)

            init {
                foreignKey(idA, idB, target = tableA.primaryKey)
            }
        }

        withTables(excludeSettings = listOf(TestDB.SQLITE), tableA, tableB, tableC) {
            tableA.insert {
                it[idA] = 1
                it[idB] = 2
            }

            tableC.insert {
                it[idC] = 3
            }

            tableB.insert {
                it[idA] = 1
                it[idB] = 2
                it[idC] = 3
            }

            assertFailAndRollback("check violation composite foreign key constraint (insert key into child table not present in parent table)") {
                tableB.insert {
                    it[idA] = 1
                    it[idB] = 1
                    it[idC] = 3
                }
            }

            assertFailAndRollback("check violation foreign key constraint (insert key into child table not present in parent table)") {
                tableB.insert {
                    it[idA] = 1
                    it[idB] = 2
                    it[idC] = 1
                }
            }
        }
    }

    @Test
    fun createTableWithCompositePrimaryKeyAndSchema() {
        val one = prepareSchemaForTest("test")
        val tableA = object : Table("test.table_a") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            override val primaryKey = PrimaryKey(idA, idB)
        }

        val tableB = object : Table("test.table_b") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            override val primaryKey = PrimaryKey(arrayOf(idA, idB))
        }

        withSchemas(one) {
            SchemaUtils.create(tableA, tableB)
            tableA.insert {
                it[idA] = 1
                it[idB] = 1
            }
            tableB.insert {
                it[idA] = 1
                it[idB] = 1
            }

            assertEquals(1, tableA.selectAll().count())
            assertEquals(1, tableB.selectAll().count())

            if (currentDialectTest is SQLServerDialect) {
                SchemaUtils.drop(tableA, tableB)
            }
        }
    }
}

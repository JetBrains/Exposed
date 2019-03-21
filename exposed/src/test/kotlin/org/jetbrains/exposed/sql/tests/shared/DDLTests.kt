package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Assert.assertTrue
import org.junit.Test
import org.postgresql.util.PGobject
import java.sql.SQLException
import java.util.*
import javax.sql.rowset.serial.SerialBlob
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class DDLTests : DatabaseTestsBase() {
    @Test fun tableExists01() {
        val TestTable = object : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables {
            assertEquals (false, TestTable.exists())
        }
    }

    @Test fun tableExists02() {
        val TestTable = object : Table() {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
        }

        withTables(TestTable) {
            assertEquals (true, TestTable.exists())
        }
    }

    object KeyWordTable : IntIdTable(name ="keywords") {
        val bool = bool("bool")
    }

    @Test fun tableExistsWithKeyword() {
        withTables(KeyWordTable) {
            assertEquals (true, KeyWordTable.exists())
            KeyWordTable.insert {
                it[KeyWordTable.bool] = true
            }
        }
    }


    @Test fun testCreateMissingTablesAndColumns01() {
        val TestTable = object : Table("test_table") {
            val id = integer("id").primaryKey()
            val name = varchar("name", length = 42)
            val time = datetime("time").uniqueIndex()
        }

        withTables(excludeSettings = listOf(TestDB.H2_MYSQL), tables = *arrayOf(TestTable)) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            SchemaUtils.drop(TestTable)
        }
    }

    @Test fun testCreateMissingTablesAndColumns02() {
        val TestTable = object : IdTable<String>("Users2") {
            override val id: Column<EntityID<String>> = varchar("id", 64).clientDefault { UUID.randomUUID().toString() }.primaryKey().entityId()

            val name = varchar("name", 255)
            val email = varchar("email", 255).uniqueIndex()
        }

        withDb(TestDB.H2) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            try {
                SchemaUtils.createMissingTablesAndColumns(TestTable)
            } finally {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Test fun testCreateMissingTablesAndColumnsChangeNullability() {
        val t1 = object : IntIdTable("foo") {
            val foo = varchar("foo", 50)
        }

        val t2 = object : IntIdTable("foo") {
            val foo = varchar("foo", 50).nullable()
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            SchemaUtils.createMissingTablesAndColumns(t1)
            t1.insert { it[foo] = "ABC" }
            assertFailAndRollback("Can't insert to not-null column") {
                t2.insert { it[foo] = null }
            }

            SchemaUtils.createMissingTablesAndColumns(t2)
            t2.insert { it[foo] = null }
            assertFailAndRollback("Can't make column non-null while has null value") {
                SchemaUtils.createMissingTablesAndColumns(t1)
            }

            t2.deleteWhere { t2.foo.isNull() }

            SchemaUtils.createMissingTablesAndColumns(t1)
            assertFailAndRollback("Can't insert to nullable column") {
                t2.insert { it[foo] = null }
            }
            SchemaUtils.drop(t1)
        }
    }

    @Test fun testCreateMissingTablesAndColumnsChangeCascadeType() {
        val fooTable = object : IntIdTable("foo") {
            val foo = varchar("foo", 50)
        }

        val barTable1 = object : IntIdTable("bar") {
            val foo = optReference("foo", fooTable, onDelete = ReferenceOption.NO_ACTION)
        }

        val barTable2 = object : IntIdTable("bar") {
            val foo = optReference("foo", fooTable, onDelete = ReferenceOption.CASCADE)
        }

        withTables(fooTable, barTable1) {
            SchemaUtils.createMissingTablesAndColumns(barTable2)
        }
    }

    // Placed outside test function to shorten generated name
    val UnnamedTable = object : Table() {
        val id = integer("id").primaryKey()
        val name = varchar("name", length = 42)
    }

    @Test fun unnamedTableWithQuotesSQL() {
        withTables(UnnamedTable) {
            val q = db.identityQuoteString
            val tableName = if (currentDialect.needsQuotesWhenSymbolsInNames) { "$q${"UnnamedTable$1".inProperCase()}$q" } else { "UnnamedTable$1".inProperCase() }
            assertEquals("CREATE TABLE " + addIfNotExistsIfSupported() + "$tableName " +
                    "(${"id".inProperCase()} ${currentDialect.dataTypeProvider.shortType()} PRIMARY KEY, ${"name".inProperCase()} VARCHAR(42) NOT NULL)", UnnamedTable.ddl)
        }
    }

    @Test fun namedEmptyTableWithoutQuotesSQL() {
        val TestTable = object : Table("test_named_table") {
        }

        withDb (TestDB.H2 ) {
            assertEquals("CREATE TABLE IF NOT EXISTS ${"test_named_table".inProperCase()}", TestTable.ddl)
            DMLTestsData.Users.select {
                exists(DMLTestsData.UserData.select { DMLTestsData.Users.id eq DMLTestsData.UserData.user_id })
            }
        }
    }

    @Test fun tableWithDifferentColumnTypesSQL01() {
        val TestTable = object : Table("different_column_types") {
            val id = integer("id").autoIncrement()
            val name = varchar("name", 42).primaryKey()
            val age = integer("age").nullable()
            // not applicable in H2 database
            //            val testCollate = varchar("testCollate", 2, "ascii_general_ci")
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.ORACLE, TestDB.MARIADB), tables = *arrayOf(TestTable)) {
            val shortAutoIncType = if (currentDialect is SQLiteDialect)
                currentDialect.dataTypeProvider.shortAutoincType().replace(" AUTOINCREMENT", "")
            else
                currentDialect.dataTypeProvider.shortAutoincType()
            assertEquals("CREATE TABLE " + addIfNotExistsIfSupported() + "${"different_column_types".inProperCase()} " +
                    "(${"id".inProperCase()} $shortAutoIncType NOT NULL, ${"name".inProperCase()} VARCHAR(42) PRIMARY KEY, " +
                    "${"age".inProperCase()} ${currentDialect.dataTypeProvider.shortType()} NULL)", TestTable.ddl)
        }
    }

    @Test fun tableWithDifferentColumnTypesSQL02() {
        val TestTable = object : Table("with_different_column_types") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 42).primaryKey()
            val age = integer("age").nullable()
        }

        withTables(excludeSettings = listOf(TestDB.MYSQL), tables = *arrayOf(TestTable)) {
            assertEquals("CREATE TABLE " + addIfNotExistsIfSupported() + "${"with_different_column_types".inProperCase()} " +
                    "(${"id".inProperCase()} ${currentDialect.dataTypeProvider.shortType()}, ${"name".inProperCase()} VARCHAR(42), ${"age".inProperCase()} ${db.dialect.dataTypeProvider.shortType()} NULL, " +
                    "CONSTRAINT pk_with_different_column_types PRIMARY KEY (${"id".inProperCase()}, ${"name".inProperCase()}))", TestTable.ddl)
        }
    }

    @Test fun tableWithMultiPKandAutoIncrement() {
        val Foo = object : IdTable<Long>("FooTable") {
            val bar = integer("bar").primaryKey()
            override val id: Column<EntityID<Long>> = long("id").entityId().autoIncrement().primaryKey()
        }

        withTables(Foo) {
            Foo.insert {
                it[Foo.bar] = 1
            }
            Foo.insert {
                it[Foo.bar] = 2
            }

            val result = Foo.selectAll().map { it[Foo.id] to it[Foo.bar] }
            assertEquals(2, result.size)
            assertEquals(1, result[0].second)
            assertEquals(2, result[1].second)
        }
    }

    @Test fun testDefaults01() {
        val currentDT = CurrentDateTime()
        val nowExpression = object : Expression<DateTime>() {
            override fun toSQL(queryBuilder: QueryBuilder) = when (currentDialect) {
                is OracleDialect -> "SYSDATE"
                is SQLServerDialect -> "GETDATE()"
                else -> "NOW()"
            }
        }
        val dtConstValue = DateTime.parse("2010-01-01").withZone(DateTimeZone.UTC)
        val dtLiteral = dateLiteral(dtConstValue)
        val TestTable = object : IntIdTable("t") {
            val s = varchar("s", 100).default("test")
            val sn = varchar("sn", 100).default("testNullable").nullable()
            val l = long("l").default(42)
            val c = char("c").default('X')
            val t1 = datetime("t1").defaultExpression(currentDT)
            val t2 = datetime("t2").defaultExpression(nowExpression)
            val t3 = datetime("t3").defaultExpression(dtLiteral)
            val t4 = date("t4").default(dtConstValue)
        }

        fun Expression<*>.itOrNull() = when {
            currentDialect.isAllowedAsColumnDefault(this)  ->
                "DEFAULT ${currentDialect.dataTypeProvider.processForDefaultValue(this)} NOT NULL"
            else -> "NULL"
        }

        withTables(listOf(TestDB.SQLITE), TestTable) {
            val dtType = currentDialect.dataTypeProvider.dateTimeType()
            assertEquals("CREATE TABLE " + addIfNotExistsIfSupported() +
                    "${"t".inProperCase()} (" +
                    "${"id".inProperCase()} ${currentDialect.dataTypeProvider.shortAutoincType()} PRIMARY KEY, " +
                    "${"s".inProperCase()} VARCHAR(100) DEFAULT 'test' NOT NULL, " +
                    "${"sn".inProperCase()} VARCHAR(100) DEFAULT 'testNullable' NULL, " +
                    "${"l".inProperCase()} ${currentDialect.dataTypeProvider.longType()} DEFAULT 42 NOT NULL, " +
                    "${"c".inProperCase()} CHAR DEFAULT 'X' NOT NULL, " +
                    "${"t1".inProperCase()} $dtType ${currentDT.itOrNull()}, " +
                    "${"t2".inProperCase()} $dtType ${nowExpression.itOrNull()}, " +
                    "${"t3".inProperCase()} $dtType ${dtLiteral.itOrNull()}, " +
                    "${"t4".inProperCase()} DATE ${dtLiteral.itOrNull()}" +
                ")", TestTable.ddl)

            val id1 = TestTable.insertAndGetId {  }

            val row1 = TestTable.select { TestTable.id eq id1 }.single()
            assertEquals("test", row1[TestTable.s])
            assertEquals("testNullable", row1[TestTable.sn])
            assertEquals(42, row1[TestTable.l])
            assertEquals('X', row1[TestTable.c])
            assertEqualDateTime(dtConstValue.withTimeAtStartOfDay(), row1[TestTable.t3].withTimeAtStartOfDay())
            assertEqualDateTime(dtConstValue.withTimeAtStartOfDay(), row1[TestTable.t4].withTimeAtStartOfDay())

            val id2 = TestTable.insertAndGetId { it[TestTable.sn] = null }

            val row2 = TestTable.select { TestTable.id eq id2 }.single()
        }
    }

    @Test fun testIndices01() {
        val t = object : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).index()
        }

        withTables(t) {
            val alter = SchemaUtils.createIndex(t.indices[0])
            assertEquals("CREATE INDEX ${"t1_name".inProperCase()} ON ${"t1".inProperCase()} (${"name".inProperCase()})", alter)
        }
    }

    @Test fun testIndices02() {
        val t = object : Table("t2") {
            val id = integer("id").primaryKey()
            val lvalue = integer("lvalue")
            val rvalue = integer("rvalue")
            val name = varchar("name", 255).index()

            init {
                index (false, lvalue, rvalue)
            }
        }

        withTables(t) {
            val a1 = SchemaUtils.createIndex(t.indices[0])
            assertEquals("CREATE INDEX ${"t2_name".inProperCase()} ON ${"t2".inProperCase()} (${"name".inProperCase()})", a1)

            val a2 = SchemaUtils.createIndex(t.indices[1])
            assertEquals("CREATE INDEX ${"t2_lvalue_rvalue".inProperCase()} ON ${"t2".inProperCase()} " +
                    "(${"lvalue".inProperCase()}, ${"rvalue".inProperCase()})", a2)
        }
    }

    @Test fun testUniqueIndices01() {
        val t = object : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).uniqueIndex()
        }

        withTables(t) {
            val alter = SchemaUtils.createIndex(t.indices[0])
            if (currentDialect is SQLiteDialect)
                assertEquals("CREATE UNIQUE INDEX ${"t1_name".inProperCase()} ON ${"t1".inProperCase()} (${"name".inProperCase()})", alter)
            else
                assertEquals("ALTER TABLE ${"t1".inProperCase()} ADD CONSTRAINT ${"t1_name_unique".inProperCase()} UNIQUE (${"name".inProperCase()})", alter)

        }
    }

    @Test fun testUniqueIndicesCustomName() {
        val t = object : Table("t1") {
            val id = integer("id").primaryKey()
            val name = varchar("name", 255).uniqueIndex("U_T1_NAME")
        }

        withTables(t) {
            val alter = SchemaUtils.createIndex(t.indices[0])
            if (currentDialect is SQLiteDialect)
                assertEquals("CREATE UNIQUE INDEX ${"U_T1_NAME"} ON ${"t1".inProperCase()} (${"name".inProperCase()})", alter)
            else
                assertEquals("ALTER TABLE ${"t1".inProperCase()} ADD CONSTRAINT ${"U_T1_NAME"} UNIQUE (${"name".inProperCase()})", alter)

        }
    }

    @Test fun testCompositePrimaryKeyCreateTable() {
        val tableName = "Foo"
        val t = object : Table(tableName) {
            val id1 = integer("id1").primaryKey()
            val id2 = integer("id2").primaryKey()
        }

        withTables(t) {
            val id1ProperName = t.id1.name.inProperCase()
            val id2ProperName = t.id2.name.inProperCase()

            assertEquals(
                    "CREATE TABLE " + addIfNotExistsIfSupported() + "${tableName.inProperCase()} (" +
                            "${t.columns.joinToString { it.descriptionDdl() }}, " +
                            "CONSTRAINT pk_$tableName PRIMARY KEY ($id1ProperName, $id2ProperName)" +
                            ")",
                    t.ddl)
        }
    }

    @Test fun testAddCompositePrimaryKeyToTableH2() {
        val tableName = "Foo"
        val t = object : Table(tableName) {
            val id1 = integer("id1").primaryKey()
            val id2 = integer("id2").primaryKey()
        }

        withDb(TestDB.H2) {
            val tableProperName = tableName.inProperCase()
            val id1ProperName = t.id1.name.inProperCase()
            val ddlId1 = t.id1.ddl
            val id2ProperName = t.id2.name.inProperCase()
            val ddlId2 = t.id2.ddl

            assertEquals(1, ddlId1.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${t.id1.descriptionDdl()}", ddlId1.first())

            assertEquals(2, ddlId2.size)
            assertEquals("ALTER TABLE $tableProperName ADD $id2ProperName ${t.id2.columnType.sqlType()}", ddlId2.first())
            assertEquals("ALTER TABLE $tableProperName ADD CONSTRAINT pk_$tableName PRIMARY KEY ($id1ProperName, $id2ProperName)", t.id2.ddl.last())
        }
    }

    @Test fun testAddCompositePrimaryKeyToTableNotH2() {
        val tableName = "Foo"
        val t = object : Table(tableName) {
            val id1 = integer("id1").primaryKey()
            val id2 = integer("id2").primaryKey()
        }

        withTables(excludeSettings = listOf(TestDB.H2, TestDB.H2_MYSQL), tables = *arrayOf(t)) {
            val tableProperName = tableName.inProperCase()
            val id1ProperName = t.id1.name.inProperCase()
            val ddlId1 = t.id1.ddl
            val id2ProperName = t.id2.name.inProperCase()
            val ddlId2 = t.id2.ddl

            assertEquals(1, ddlId1.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${t.id1.descriptionDdl()}", ddlId1.first())

            assertEquals(1, ddlId2.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${t.id2.descriptionDdl()}, ADD CONSTRAINT pk_$tableName PRIMARY KEY ($id1ProperName, $id2ProperName)", ddlId2.first())
        }
    }

    @Test fun testMultiColumnIndex() {
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
            assertEquals("CREATE INDEX ${"t1_name_type".inProperCase()} ON ${"t1".inProperCase()} (${"name".inProperCase()}, ${"type".inProperCase()})", indexAlter)
            if (currentDialect is SQLiteDialect)
                assertEquals("CREATE UNIQUE INDEX ${"t1_type_name".inProperCase()} ON ${"t1".inProperCase()} (${"type".inProperCase()}, ${"name".inProperCase()})", uniqueAlter)
            else
                assertEquals("ALTER TABLE ${"t1".inProperCase()} ADD CONSTRAINT ${"t1_type_name_unique".inProperCase()} UNIQUE (${"type".inProperCase()}, ${"name".inProperCase()})", uniqueAlter)
        }
    }

    @Test fun testMultiColumnIndexCustomName() {
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
            assertEquals("CREATE INDEX ${"I_T1_NAME_TYPE"} ON ${"t1".inProperCase()} (${"name".inProperCase()}, ${"type".inProperCase()})", indexAlter)
            if (currentDialect is SQLiteDialect)
                assertEquals("CREATE UNIQUE INDEX ${"U_T1_TYPE_NAME"} ON ${"t1".inProperCase()} (${"type".inProperCase()}, ${"name".inProperCase()})", uniqueAlter)
            else
                assertEquals("ALTER TABLE ${"t1".inProperCase()} ADD CONSTRAINT ${"U_T1_TYPE_NAME"} UNIQUE (${"type".inProperCase()}, ${"name".inProperCase()})", uniqueAlter)
        }
    }

    @Test fun testBlob() {
        val t = object: Table("t1") {
            val id = integer("id").autoIncrement("t1_seq").primaryKey()
            val b = blob("blob")
        }

        withTables(t) {
            val bytes = "Hello there!".toByteArray()
            val blob = if (currentDialect.dataTypeProvider.blobAsStream) {
                    SerialBlob(bytes)
                } else connection.createBlob().apply {
                    setBytes(1, bytes)
                }

            val id = t.insert {
                it[t.b] = blob
            } get (t.id)


            val readOn = t.select { t.id eq id }.first()[t.b]
            val text = readOn.binaryStream.reader().readText()

            assertEquals("Hello there!", text)
        }
    }

    @Test fun testBinary() {
        val t = object : Table() {
            val binary = binary("bytes", 10)
            val byteCol = binary("byteCol", 1).clientDefault { byteArrayOf(0) }
        }

        fun SizedIterable<ResultRow>.readAsString() = map { String(it[t.binary]) }

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

            val world = t.select { t.binary eq worldBytes }.readAsString()
            assertEqualCollections(world, "World!")

            val worldByBitCol = t.select { t.byteCol eq byteArrayOf(1) }.readAsString()
            assertEqualCollections(worldByBitCol, "World!")
        }
    }

    @Test fun addAutoPrimaryKey() {
        val tableName = "Foo"
        val initialTable = object : Table(tableName) {
            val bar = text("bar")
        }
        val t = IntIdTable(tableName)


        withDb(TestDB.H2) {
            SchemaUtils.createMissingTablesAndColumns(initialTable)
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()}", t.id.ddl.first())
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD CONSTRAINT pk_$tableName PRIMARY KEY (${"id".inProperCase()})", t.id.ddl[1])
            assertEquals(1, currentDialect.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialect.tableColumns(t)[t]!!.size)
            SchemaUtils.drop(t)
        }

        withDb(TestDB.SQLITE) {
            try {
                SchemaUtils.createMissingTablesAndColumns(t)
                assertFalse(db.supportsAlterTableWithAddColumn)
            } catch (e: SQLException) {
                // SQLite doesn't support
            } finally {
                SchemaUtils.drop(t)
            }
        }

        withTables(excludeSettings = listOf(TestDB.H2, TestDB.H2_MYSQL, TestDB.SQLITE), tables = *arrayOf(initialTable)) {
            assertEquals("ALTER TABLE ${tableName.inProperCase()} ADD ${"id".inProperCase()} ${t.id.columnType.sqlType()} PRIMARY KEY", t.id.ddl)
            assertEquals(1, currentDialect.tableColumns(t)[t]!!.size)
            SchemaUtils.createMissingTablesAndColumns(t)
            assertEquals(2, currentDialect.tableColumns(t)[t]!!.size)
        }
    }


    private abstract class EntityTable(name: String = "") : IdTable<String>(name) {
        override val id: Column<EntityID<String>> = varchar("id", 64).clientDefault { UUID.randomUUID().toString() }.primaryKey().entityId()
    }

    @Test fun complexTest01() {
        val User = object : EntityTable() {
            val name = varchar("name", 255)
            val email = varchar("email", 255)
        }

        val Repository = object : EntityTable() {
            val name = varchar("name", 255)
        }

        val UserToRepo = object : EntityTable() {
            val user = reference("user", User)
            val repo = reference("repo", Repository)
        }

        withTables(User, Repository, UserToRepo) {
            User.insert {
                it[User.name] = "foo"
                it[User.email] = "bar"
            }

            val userID = User.selectAll().single()[User.id]

            Repository.insert {
                it[Repository.name] = "foo"
            }
            val repo = Repository.selectAll().single()[Repository.id]

            UserToRepo.insert {
                it[UserToRepo.user] = userID
                it[UserToRepo.repo] = repo
            }

            assertEquals(1, UserToRepo.selectAll().count())
            UserToRepo.insert {
                it[UserToRepo.user] = userID
                it[UserToRepo.repo] = repo
            }

            assertEquals(2, UserToRepo.selectAll().count())
        }
    }

    object Table1 : IntIdTable() {
        val table2 = reference("teamId", Table2, onDelete = ReferenceOption.NO_ACTION)
    }

    object Table2 : IntIdTable() {
        val table1 = optReference("teamId", Table1, onDelete = ReferenceOption.NO_ACTION)
    }

    @Test fun testCrossReference() {
        withTables(Table1, Table2) {
            val table2id = Table2.insertAndGetId{}
            val table1id = Table1.insertAndGetId {
                it[Table1.table2] = table2id
            }

            Table2.insertAndGetId {
                it[Table2.table1] = table1id
            }

            assertEquals(1, Table1.selectAll().count())
            assertEquals(2, Table2.selectAll().count())

            Table2.update {
                it[Table2.table1] = null
            }

            Table1.deleteAll()
            Table2.deleteAll()

            if (currentDialect !is SQLiteDialect) {
                exec(ForeignKeyConstraint.from(Table2.table1).dropStatement().single())
            }
        }
    }

    @Test fun testUUIDColumnType() {
        val Node = object: IntIdTable("node") {
            val uuid = uuid("uuid")
        }

        withTables(Node){
            val key: UUID = UUID.randomUUID()
            val id = Node.insertAndGetId { it[uuid] = key }
            assertNotNull(id)
            val uidById = Node.select { Node.id eq id }.singleOrNull()?.get(Node.uuid)
            assertEquals(key, uidById)
            val uidByKey = Node.select { Node.uuid eq key }.singleOrNull()?.get(Node.uuid)
            assertEquals(key, uidByKey)
        }
    }

    @Test fun testBooleanColumnType() {
        val BoolTable = object: Table("booleanTable") {
            val bool = bool("bool")
        }

        withTables(BoolTable){
            BoolTable.insert {
                it[bool] = true
            }
            val result = BoolTable.selectAll().toList()
            assertEquals(1, result.size)
            assertEquals(true, result.single()[BoolTable.bool])
        }
    }

    @Test fun testDeleteMissingTable() {
        val missingTable = Table("missingTable")
        withDb {
            SchemaUtils.drop(missingTable)
        }
    }

    @Test fun testCheckConstraint01() {
        val checkTable = object : Table("checkTable") {
            val positive = integer("positive").check { it greaterEq 0 }
            val negative = integer("negative").check("subZero") { it less 0 }
        }

        withTables(listOf(TestDB.MYSQL), checkTable) {
            checkTable.insert {
                it[positive] = 42
                it[negative] = -14
            }

            assertEquals(1, checkTable.selectAll().count())

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

    @Test fun testCheckConstraint02() {
        val checkTable = object : Table("multiCheckTable") {
            val positive = integer("positive")
            val negative = integer("negative")

            init {
                check("multi") { (negative less 0) and (positive greaterEq 0) }
            }
        }

        withTables(listOf(TestDB.MYSQL), checkTable) {
            checkTable.insert {
                it[positive] = 57
                it[negative] = -32
            }

            assertEquals(1, checkTable.selectAll().count())

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

    internal enum class Foo { Bar, Baz }

    class PGEnum<T:Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
        init {
            value = enumValue?.name
            type = enumTypeName
        }
    }

    object EnumTable : IntIdTable("EnumTable") {
        internal var enumColumn: Column<Foo> = enumeration("enumColumn", Foo::class)

        internal fun initEnumColumn(sql: String) {
            (columns as MutableList<Column<*>>).remove(enumColumn)
            enumColumn = customEnumeration("enumColumn", sql, { value ->
                when (currentDialect) {
                    is H2Dialect -> Foo.values()[value as Int]
                    else -> Foo.valueOf(value as String)
                }
            }, { value ->
                when (currentDialect) {
                    is PostgreSQLDialect -> PGEnum("FooEnum", value)
                    else -> value.name
                }
            })
        }
    }

    @Test fun testCustomEnumeration01() {
        val coveredWithTests = listOf(TestDB.H2, TestDB.MYSQL, TestDB.POSTGRESQL)
        withDb(TestDB.values().toList() - coveredWithTests) {
            val sqlType = when (currentDialect) {
                is H2Dialect, is MysqlDialect -> "ENUM('Bar', 'Baz')"
                is PostgreSQLDialect -> "FooEnum"
                else -> error("Unsupported case")
            }

            class EnumEntity(id: EntityID<Int>) : IntEntity(id) {
                var enum by EnumTable.enumColumn
            }

            val EnumClass = object : IntEntityClass<EnumEntity>(EnumTable, EnumEntity::class.java) {}

            try {
                if (currentDialect is PostgreSQLDialect) {
                    exec("CREATE TYPE FooEnum AS ENUM ('Bar', 'Baz');")
                }
                EnumTable.initEnumColumn(sqlType)
                SchemaUtils.create(EnumTable)
                EnumTable.insert {
                    it[enumColumn] = Foo.Bar
                }
                assertEquals(Foo.Bar,  EnumTable.selectAll().single()[EnumTable.enumColumn])

                val entity = EnumClass.new {
                    enum = Foo.Baz
                }
                assertEquals(Foo.Baz, entity.enum)
                entity.id.value // flush entity
                assertEquals(Foo.Baz, entity.enum)
                assertEquals(Foo.Baz, EnumClass.reload(entity)!!.enum)
            } finally {
                try {
                    SchemaUtils.drop(EnumTable)
                } catch (ignore: Exception) {}
            }
        }
    }

    // https://github.com/JetBrains/Exposed/issues/112
    @Test fun testDropTableFlushesCache() {
        withDb {
            class Keyword(id: EntityID<Int>) : IntEntity(id) {
                var bool by KeyWordTable.bool
            }
            val KeywordEntityClass = object : IntEntityClass<Keyword>(KeyWordTable, Keyword::class.java) {}

            SchemaUtils.create(KeyWordTable)

            val newKeyword = KeywordEntityClass.new { bool = true }

            SchemaUtils.drop(KeyWordTable)
        }
    }

    // https://github.com/JetBrains/Exposed/issues/522
    @Test fun testInnerJoinWithMultipleForeignKeys() {
        val Users = object : IntIdTable() {}

        val Subscriptions = object : LongIdTable() {
            val user = reference("user", Users)
            val adminBy = reference("adminBy", Users).nullable()
        }

        withTables(Subscriptions) {
            val query = Subscriptions.join(Users, JoinType.INNER, additionalConstraint = {Subscriptions.user eq Users.id}).selectAll()
            assertEquals(0, query.count())
        }
    }
}

private fun String.inProperCase(): String = TransactionManager.currentOrNull()?.let { tm ->
    (currentDialect as? VendorDialect)?.run {
        this@inProperCase.inProperCase
    }
} ?: this

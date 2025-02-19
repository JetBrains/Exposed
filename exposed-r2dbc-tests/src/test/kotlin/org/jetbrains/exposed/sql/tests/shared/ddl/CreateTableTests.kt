package org.jetbrains.exposed.sql.tests.shared.ddl

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.r2dbc.sql.exists
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.autoIncColumnType
import org.jetbrains.exposed.sql.name
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.Category
import org.jetbrains.exposed.sql.tests.shared.Item
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test
import java.util.*
import kotlin.test.assertFails

class CreateTableTests : R2dbcDatabaseTestsBase() {
    @Test
    fun createTableWithDuplicateColumn() = runTest {
        val assertionFailureMessage = "Can't create a table with multiple columns having the same name"

        withDb {
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableWithDuplicatedColumn)
            }
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableDuplicatedColumnRefereToIntIdTable)
            }
            assertFails(assertionFailureMessage) {
                SchemaUtils.create(TableDuplicatedColumnRefereToTable)
            }
        }
    }

    @Test
    fun testCreateIdTableWithPrimaryKeyByEntityID() = runTest {
        val testTable = object : IdTable<String>("test_table") {
            val column1 = varchar("column_1", 30)
            override val id = column1.entityId()

            override val primaryKey = PrimaryKey(id)
        }

        withDb {
            val singleColumnDescription = testTable.columns.single().descriptionDdl(false)

            assertTrue(singleColumnDescription.contains("PRIMARY KEY"))
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + testTable.tableName.inProperCase() + " (" +
                    singleColumnDescription +
                    ")",
                testTable.ddl
            )
        }
    }

    @Test
    fun testCreateIdTableWithPrimaryKeyByColumn() = runTest {
        val testTable = object : IdTable<String>("test_table") {
            val column1 = varchar("column_1", 30)
            override val id = column1.entityId()

            override val primaryKey = PrimaryKey(column1)
        }

        withDb {
            val singleColumnDescription = testTable.columns.single().descriptionDdl(false)

            assertTrue(singleColumnDescription.contains("PRIMARY KEY"))
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + testTable.tableName.inProperCase() + " (" +
                    singleColumnDescription +
                    ")",
                testTable.ddl
            )
        }
    }

    @Test
    fun testCreateIdTableWithNamedPrimaryKeyByColumn() = runTest {
        val pkConstraintName = "PK_Constraint_name"
        val testTable = object : IdTable<String>("test_table") {
            val column1 = varchar("column_1", 30)
            override val id = column1.entityId()

            override val primaryKey = PrimaryKey(column1, name = pkConstraintName)
        }

        withDb {
            val singleColumn = testTable.columns.single()

            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + testTable.tableName.inProperCase() + " (" +
                    "${singleColumn.descriptionDdl(false)}, " +
                    "CONSTRAINT $pkConstraintName PRIMARY KEY (${singleColumn.name.inProperCase()})" +
                    ")",
                testTable.ddl
            )
        }
    }

    @Test
    fun testCreateTableWithSingleColumnPrimaryKey() = runTest {
        val stringPKTable = object : Table("string_pk_table") {
            val column1 = varchar("column_1", 30)

            override val primaryKey = PrimaryKey(column1)
        }
        val intPKTable = object : Table("int_pk_table") {
            val column1 = integer("column_1")

            override val primaryKey = PrimaryKey(column1)
        }

        withDb { testDb ->
            val stringColumnDescription = stringPKTable.columns.single().descriptionDdl(false)
            val intColumnDescription = intPKTable.columns.single().descriptionDdl(false)

            assertTrue(stringColumnDescription.contains("PRIMARY KEY"))
            assertTrue(intColumnDescription.contains("PRIMARY KEY"))
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + stringPKTable.tableName.inProperCase() + " (" +
                    stringColumnDescription +
                    ")",
                stringPKTable.ddl
            )
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + intPKTable.tableName.inProperCase() + " (" +
                    intColumnDescription +
                    when (testDb) {
                        TestDB.ORACLE ->
                            ", CONSTRAINT chk_int_pk_table_signed_integer_column_1 CHECK (${"column_1".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})"
                        else -> ""
                    } +
                    ")",
                intPKTable.ddl
            )
        }
    }

    @Test
    fun primaryKeyCreateTableTest() = runTest {
        val account = object : Table("Account") {
            val id1 = integer("id1")
            val id2 = integer("id2")

            override val primaryKey = PrimaryKey(id1, id2)
        }
        withDb { testDb ->
            val id1ProperName = account.id1.name.inProperCase()
            val id2ProperName = account.id2.name.inProperCase()
            val tableName = account.tableName

            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${tableName.inProperCase()} (" +
                    "${account.columns.joinToString { it.descriptionDdl(false) }}, " +
                    "CONSTRAINT pk_$tableName PRIMARY KEY ($id1ProperName, $id2ProperName)" +
                    when (testDb) {
                        TestDB.ORACLE ->
                            ", CONSTRAINT chk_Account_signed_integer_id1 CHECK (${"id1".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})" +
                                ", CONSTRAINT chk_Account_signed_integer_id2 CHECK (${"id2".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})"
                        else -> ""
                    } +
                    ")",
                account.ddl
            )
        }
    }

    @Test
    fun primaryKeyWithConstraintNameCreateTableTest() = runTest {
        val pkConstraintName = "PKConstraintName"

        // Table with composite primary key
        withDb { testDb ->
            val id1ProperName = Person.id1.name.inProperCase()
            val id2ProperName = Person.id2.name.inProperCase()
            val tableName = Person.tableName

            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${tableName.inProperCase()} (" +
                    "${Person.columns.joinToString { it.descriptionDdl(false) }}, " +
                    "CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)" +
                    when (testDb) {
                        TestDB.ORACLE ->
                            ", CONSTRAINT chk_Person_signed_integer_id1 CHECK (${"id1".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})" +
                                ", CONSTRAINT chk_Person_signed_integer_id2 CHECK (${"id2".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})"
                        else -> ""
                    } +
                    ")",
                Person.ddl
            )
        }

        // Table with single column in primary key.
        val user = object : Table("User") {
            val user_name = varchar("user_name", 25)

            override val primaryKey = PrimaryKey(user_name, name = pkConstraintName)
        }
        withDb {
            val userNameProperName = user.user_name.name.inProperCase()
            val tableName = TransactionManager.current().identity(user)

            // Must generate primary key constraint, because the constraint name was defined.
            assertEquals(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "$tableName (" +
                    "${user.columns.joinToString { it.descriptionDdl(false) }}, " +
                    "CONSTRAINT $pkConstraintName PRIMARY KEY ($userNameProperName)" +
                    ")",
                user.ddl
            )
        }
    }

    @Test
    fun addCompositePrimaryKeyToTableTest() = runTest {
        withDb { testDb ->
            val tableName = Person.tableName
            val tableProperName = tableName.inProperCase()
            val id1ProperName = Person.id1.name.inProperCase()
            val ddlId1 = Person.id1.ddl
            val id2ProperName = Person.id2.name.inProperCase()
            val ddlId2 = Person.id2.ddl
            val pkConstraintName = Person.primaryKey.name

            assertEquals(1, ddlId1.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${Person.id1.descriptionDdl(false)}", ddlId1.first())

            when (testDb) {
                in TestDB.ALL_H2, TestDB.ORACLE -> {
                    assertEquals(2, ddlId2.size)
                    assertEquals("ALTER TABLE $tableProperName ADD $id2ProperName ${Person.id2.columnType.sqlType()}", ddlId2.first())
                    assertEquals("ALTER TABLE $tableProperName ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)", Person.id2.ddl.last())
                }
                else -> {
                    assertEquals(1, ddlId2.size)
                    assertEquals(
                        "ALTER TABLE $tableProperName ADD ${Person.id2.descriptionDdl(false)}, " +
                            "ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)",
                        ddlId2.first()
                    )
                }
            }
        }
    }

    @Test
    fun addOneColumnPrimaryKeyToTableTest() = runTest {
        withTables(Book) { testDb ->
            val tableProperName = Book.tableName.inProperCase()
            val pkConstraintName = Book.primaryKey.name
            val id1ProperName = Book.id.name.inProperCase()
            val ddlId1 = Book.id.ddl

            when (testDb) {
                in TestDB.ALL_H2, TestDB.ORACLE -> assertEqualCollections(
                    listOf(
                        "ALTER TABLE $tableProperName ADD ${Book.id.descriptionDdl(false)}",
                        "ALTER TABLE $tableProperName ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName)"
                    ),
                    ddlId1
                )
                else -> assertEquals(
                    "ALTER TABLE $tableProperName ADD ${Book.id.descriptionDdl(false)}, ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName)",
                    ddlId1.first()
                )
            }
        }
    }

    object Book : Table("Book") {
        val id = integer("id").autoIncrement()

        override val primaryKey = PrimaryKey(id, name = "PKConstraintName")
    }

    object Person : Table("Person") {
        val id1 = integer("id1")
        val id2 = integer("id2")

        override val primaryKey = PrimaryKey(id1, id2, name = "PKConstraintName")
    }

    object TableWithDuplicatedColumn : Table("myTable") {
        val id1 = integer("id")
        val id2 = integer("id")
    }

    object IDTable : IntIdTable("IntIdTable")

    object TableDuplicatedColumnRefereToIntIdTable : IntIdTable("myTable") {
        val reference = reference("id", IDTable)
    }

    object TableDuplicatedColumnRefereToTable : Table("myTable") {
        val reference = reference("id", TableWithDuplicatedColumn.id1)
    }

    @Test
    fun createTableWithExplicitForeignKeyName1() = runTest {
        val fkName = "MyForeignKey1"
        val parent = object : LongIdTable("parent1") {}
        val child = object : LongIdTable("child1") {
            val parentId = reference(
                name = "parent_id",
                foreign = parent,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
                fkName = fkName
            )
        }
        withDb { testDb ->
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.sequence?.createStatement()?.single(),
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.parentId)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.id)})" +
                    if (testDb == TestDB.ORACLE) {
                        ", CONSTRAINT chk_child1_signed_long_id CHECK (${this.identity(parent.id)} BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE})"
                    } else {
                        ""
                    } +
                    ")"
            )
            assertEqualCollections(child.ddl, expected)
        }
    }

    @Test
    fun createTableWithQuotes() = runTest {
        val parent = object : LongIdTable("\"Parent\"") {}
        val child = object : LongIdTable("\"Child\"") {
            val parentId = reference(
                name = "parent_id",
                foreign = parent,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
            )
        }
        withDb { testDb ->
            val expected = "CREATE TABLE " + addIfNotExistsIfSupported() + "${this.identity(child)} (" +
                "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                " CONSTRAINT ${"fk_Child_parent_id__id".inProperCase()}" +
                " FOREIGN KEY (${this.identity(child.parentId)})" +
                " REFERENCES ${this.identity(parent)}(${this.identity(parent.id)})" +
                if (testDb == TestDB.ORACLE) {
                    ", CONSTRAINT chk_Child_signed_long_id CHECK (${this.identity(parent.id)} BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE})"
                } else {
                    ""
                } +
                ")"
            assertEquals(child.ddl.last(), expected)
        }
    }

    @Test
    fun createTableWithSingleQuotes() = runTest {
        val parent = object : LongIdTable("'Parent2'") {}
        val child = object : LongIdTable("'Child2'") {
            val parentId = reference(
                name = "parent_id",
                foreign = parent,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
            )
        }
        withDb { testDb ->
            val expected = "CREATE TABLE " + addIfNotExistsIfSupported() + "${this.identity(child)} (" +
                "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                " CONSTRAINT ${"fk_Child2_parent_id__id".inProperCase()}" +
                " FOREIGN KEY (${this.identity(child.parentId)})" +
                " REFERENCES ${this.identity(parent)}(${this.identity(parent.id)})" +
                if (testDb == TestDB.ORACLE) {
                    ", CONSTRAINT chk_Child2_signed_long_id CHECK (${this.identity(parent.id)} BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE})"
                } else {
                    ""
                } +
                ")"
            assertEquals(child.ddl.last(), expected)
        }
    }

    @Test
    fun createTableWithExplicitForeignKeyName2() = runTest {
        val fkName = "MyForeignKey2"
        val parent = object : LongIdTable("parent2") {
            val uniqueId = uuid("uniqueId").clientDefault { UUID.randomUUID() }.uniqueIndex()
        }
        val child = object : LongIdTable("child2") {
            val parentId = reference(
                name = "parent_id",
                refColumn = parent.uniqueId,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
                fkName = fkName
            )
        }
        withDb { testDb ->
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.sequence?.createStatement()?.single(),
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.parentId)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.uniqueId)})" +
                    if (testDb == TestDB.ORACLE) {
                        ", CONSTRAINT chk_child2_signed_long_id CHECK (${this.identity(parent.id)} BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE})"
                    } else {
                        ""
                    } +
                    ")"
            )
            assertEqualCollections(child.ddl, expected)
        }
    }

    @Test
    fun createTableWithExplicitForeignKeyName3() = runTest {
        val fkName = "MyForeignKey3"
        val parent = object : LongIdTable("parent3") {}
        val child = object : LongIdTable("child3") {
            val parentId = optReference(
                name = "parent_id",
                foreign = parent,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
                fkName = fkName
            )
        }
        withDb { testDb ->
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.sequence?.createStatement()?.single(),
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.parentId)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.id)})" +
                    if (testDb == TestDB.ORACLE) {
                        ", CONSTRAINT chk_child3_signed_long_id CHECK (${this.identity(parent.id)} BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE})"
                    } else {
                        ""
                    } +
                    ")"
            )
            assertEqualCollections(child.ddl, expected)
        }
    }

    @Test
    fun createTableWithExplicitForeignKeyName4() = runTest {
        val fkName = "MyForeignKey4"
        val parent = object : LongIdTable() {
            override val tableName get() = "parent4"
            val uniqueId = uuid("uniqueId").clientDefault { UUID.randomUUID() }.uniqueIndex()
        }
        val child = object : LongIdTable("child4") {
            val parentId = optReference(
                name = "parent_id",
                refColumn = parent.uniqueId,
                onUpdate = ReferenceOption.NO_ACTION,
                onDelete = ReferenceOption.NO_ACTION,
                fkName = fkName
            )
        }
        withDb { testDb ->
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.sequence?.createStatement()?.single(),
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.parentId)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.uniqueId)})" +
                    if (testDb == TestDB.ORACLE) {
                        ", CONSTRAINT chk_child4_signed_long_id CHECK (${this.identity(parent.id)} BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE})"
                    } else {
                        ""
                    } +
                    ")"
            )
            assertEqualCollections(child.ddl, expected)
        }
    }

    @Test
    fun createTableWithExplicitCompositeForeignKeyName1() = runTest {
        val fkName = "MyForeignKey1"
        val parent = object : Table("parent1") {
            val idA = integer("id_a")
            val idB = integer("id_b")
            override val primaryKey = PrimaryKey(idA, idB)
        }
        val child = object : Table("child1") {
            val idA = integer("id_a")
            val idB = integer("id_b")

            init {
                foreignKey(
                    idA, idB,
                    target = parent.primaryKey,
                    onUpdate = ReferenceOption.CASCADE,
                    onDelete = ReferenceOption.CASCADE,
                    name = fkName
                )
            }
        }
        withDb { testDb ->
            val t = TransactionManager.current()
            val updateCascadePart = if (testDb != TestDB.ORACLE) " ON UPDATE CASCADE" else ""
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.sequence?.createStatement()?.single(),
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.idA)}, ${t.identity(child.idB)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.idA)}, ${t.identity(parent.idB)})" +
                    " ON DELETE CASCADE$updateCascadePart" +
                    when (testDb) {
                        TestDB.ORACLE ->
                            ", CONSTRAINT chk_child1_signed_integer_id_a CHECK (${"id_a".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})" +
                                ", CONSTRAINT chk_child1_signed_integer_id_b CHECK (${"id_b".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})"
                        else -> ""
                    } +
                    ")"
            )
            assertEqualCollections(child.ddl, expected)
        }
    }

    @Test
    fun createTableWithExplicitCompositeForeignKeyName2() = runTest {
        val fkName = "MyForeignKey2"
        val parent = object : Table("parent2") {
            val idA = integer("id_a")
            val idB = integer("id_b")

            init {
                uniqueIndex(idA, idB)
            }
        }
        val child = object : Table("child2") {
            val idA = integer("id_a")
            val idB = integer("id_b")

            init {
                foreignKey(
                    idA to parent.idA, idB to parent.idB,
                    onUpdate = ReferenceOption.NO_ACTION,
                    onDelete = ReferenceOption.NO_ACTION,
                    name = fkName
                )
            }
        }
        withDb { testDb ->
            val t = TransactionManager.current()
            val expected = listOfNotNull(
                child.autoIncColumn?.autoIncColumnType?.sequence?.createStatement()?.single(),
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${t.identity(child)} (" +
                    "${child.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${t.db.identifierManager.cutIfNecessaryAndQuote(fkName).inProperCase()}" +
                    " FOREIGN KEY (${t.identity(child.idA)}, ${t.identity(child.idB)})" +
                    " REFERENCES ${t.identity(parent)}(${t.identity(parent.idA)}, ${t.identity(parent.idB)})" +
                    when (testDb) {
                        TestDB.ORACLE ->
                            ", CONSTRAINT chk_child2_signed_integer_id_a CHECK (${"id_a".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})" +
                                ", CONSTRAINT chk_child2_signed_integer_id_b CHECK (${"id_b".inProperCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE})"
                        else -> ""
                    } +
                    ")"
            )
            assertEqualCollections(child.ddl, expected)
        }
    }

    @Test
    fun createTableWithOnDeleteSetDefault() = runTest {
        withDb(excludeSettings = TestDB.ALL_MYSQL + TestDB.ALL_MARIADB + listOf(TestDB.ORACLE)) { testDb ->
            val expected = listOf(
                "CREATE TABLE " + addIfNotExistsIfSupported() + "${this.identity(Item)} (" +
                    "${Item.columns.joinToString { it.descriptionDdl(false) }}," +
                    " CONSTRAINT ${"fk_Item_categoryId__id".inProperCase()}" +
                    " FOREIGN KEY (${this.identity(Item.categoryId)})" +
                    " REFERENCES ${this.identity(Category)}(${this.identity(Category.id)})" +
                    " ON DELETE SET DEFAULT" +
                    ")"
            )

            assertEqualCollections(Item.ddl, expected)
        }
    }

    object OneTable : IntIdTable("one")
    object OneOneTable : IntIdTable("one.one")

    @Test
    fun `test create table with same name in different schemas`() = runTest {
        val one = prepareSchemaForTest("one")
        withDb { testDb ->
            assertEquals(false, OneTable.exists())
            assertEquals(false, OneOneTable.exists())
            try {
                SchemaUtils.create(OneTable)
                assertEquals(true, OneTable.exists())
                assertEquals(false, OneOneTable.exists())

                val schemaPrefixedName = testDb.getDefaultSchemaPrefixedTableName(OneTable.tableName)
                assertTrue(SchemaUtils.listTables().any { it.equals(schemaPrefixedName, ignoreCase = true) })

                SchemaUtils.createSchema(one)
                SchemaUtils.create(OneOneTable)
                assertEquals(true, OneTable.exists())
                assertEquals(true, OneOneTable.exists())

                assertTrue(SchemaUtils.listTablesInAllSchemas().any { it.equals(OneOneTable.tableName, ignoreCase = true) })
            } finally {
                SchemaUtils.drop(OneTable, OneOneTable)
                val cascade = testDb != TestDB.SQLSERVER
                SchemaUtils.dropSchema(one, cascade = cascade)
            }
        }
    }

    private fun TestDB.getDefaultSchemaPrefixedTableName(tableName: String): String = when (currentDialectTest) {
        is SQLServerDialect -> "dbo.$tableName"
        is OracleDialect -> "${this.user}.$tableName"
        is MysqlDialect -> "${this.db!!.name}.$tableName"
        is SQLiteDialect -> tableName
        else -> "public.$tableName"
    }

    @Test
    fun testListTablesInAllSchemas() = runTest {
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSchema) {
                val one = prepareSchemaForTest("one")

                try {
                    SchemaUtils.createSchema(one)
                    // table "one.one" is created in new schema by db because of name
                    // even though current schema has not been set to the new one above
                    SchemaUtils.create(OneOneTable)

                    // so new table will not appear in list of tables in current schema
                    assertFalse(SchemaUtils.listTables().any { it.equals(OneOneTable.tableName, ignoreCase = true) })
                    // but new table appears in list of tables from all schema
                    assertTrue(SchemaUtils.listTablesInAllSchemas().any { it.equals(OneOneTable.tableName, ignoreCase = true) })
                    assertTrue(OneOneTable.exists())
                } finally {
                    SchemaUtils.drop(OneOneTable)
                    val cascade = testDb != TestDB.SQLSERVER
                    SchemaUtils.dropSchema(one, cascade = cascade)
                }
            }
        }
    }
}

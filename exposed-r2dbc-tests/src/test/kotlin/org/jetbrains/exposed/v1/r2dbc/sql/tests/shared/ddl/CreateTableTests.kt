package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.autoIncColumnType
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.exists
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.name
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.Category
import org.jetbrains.exposed.v1.r2dbc.tests.shared.Item
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFails

class CreateTableTests : R2dbcDatabaseTestsBase() {
    // https://github.com/JetBrains/Exposed/issues/709
    @Test
    fun createTableWithDuplicateColumn() {
        val assertionFailureMessage = "Can't create a table with multiple columns having the same name"

        withDb {
            assertFails(assertionFailureMessage) {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(TableWithDuplicatedColumn)
            }
            assertFails(assertionFailureMessage) {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(TableDuplicatedColumnRefereToIntIdTable)
            }
            assertFails(assertionFailureMessage) {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(TableDuplicatedColumnRefereToTable)
            }
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testCreateIdTableWithPrimaryKeyByEntityID() {
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

    @OptIn(InternalApi::class)
    @Test
    fun testCreateIdTableWithPrimaryKeyByColumn() {
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

    @OptIn(InternalApi::class)
    @Test
    fun testCreateIdTableWithNamedPrimaryKeyByColumn() {
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

    @OptIn(InternalApi::class)
    @Test
    fun testCreateTableWithSingleColumnPrimaryKey() {
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

    @OptIn(InternalApi::class)
    @Test
    fun primaryKeyCreateTableTest() {
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

    @OptIn(InternalApi::class)
    @Test
    fun primaryKeyWithConstraintNameCreateTableTest() {
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
            val tableName = org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current().identity(user)

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

    @OptIn(InternalApi::class)
    @Test
    fun addCompositePrimaryKeyToTableTest() {
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
                in TestDB.ALL_H2_V2, TestDB.ORACLE -> {
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

    @OptIn(InternalApi::class)
    @Test
    fun addOneColumnPrimaryKeyToTableTest() {
        withTables(Book) { testDb ->
            val tableProperName = Book.tableName.inProperCase()
            val pkConstraintName = Book.primaryKey.name
            val id1ProperName = Book.id.name.inProperCase()
            val ddlId1 = Book.id.ddl

            when (testDb) {
                in TestDB.ALL_H2_V2, TestDB.ORACLE -> assertEqualCollections(
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithExplicitForeignKeyName1() {
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
            val t = org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current()
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithQuotes() {
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithSingleQuotes() {
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithExplicitForeignKeyName2() {
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
            val t = org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current()
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithExplicitForeignKeyName3() {
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
            val t = org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current()
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithExplicitForeignKeyName4() {
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
            val t = org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current()
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithExplicitCompositeForeignKeyName1() {
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
            val t = org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current()
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithExplicitCompositeForeignKeyName2() {
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
            val t = org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current()
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

    @OptIn(InternalApi::class)
    @Test
    fun createTableWithOnDeleteSetDefault() {
        withDb(excludeSettings = TestDB.ALL_MYSQL + TestDB.MARIADB + listOf(TestDB.ORACLE)) { testDb ->
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
    fun `test create table with same name in different schemas`() {
        val one = prepareSchemaForTest("one")
        withDb { testDb ->
            assertEquals(false, OneTable.exists())
            assertEquals(false, OneOneTable.exists())
            try {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(OneTable)
                assertEquals(true, OneTable.exists())
                assertEquals(false, OneOneTable.exists())

                val schemaPrefixedName = testDb.getDefaultSchemaPrefixedTableName(OneTable.tableName)
                assertTrue(org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listTables().any { it.equals(schemaPrefixedName, ignoreCase = true) })

                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createSchema(one)
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(OneOneTable)
                assertEquals(true, OneTable.exists())
                assertEquals(true, OneOneTable.exists())

                assertTrue(org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listTablesInAllSchemas().any { it.equals(OneOneTable.tableName, ignoreCase = true) })
            } finally {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(OneTable, OneOneTable)
                val cascade = testDb != TestDB.SQLSERVER
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropSchema(one, cascade = cascade)
            }
        }
    }

    @Test
    fun testListTablesInCurrentSchema() {
        withDb { testDb ->
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(OneTable)

            val schemaPrefixedName = testDb.getDefaultSchemaPrefixedTableName(OneTable.tableName)
            assertTrue(org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listTables().any { it.equals(schemaPrefixedName, ignoreCase = true) })
        }

        withDb { testDb ->
            // ensures that db connection has not been lost by calling listTables()
            assertTrue(OneTable.exists())

            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(OneTable)
        }
    }

    private fun TestDB.getDefaultSchemaPrefixedTableName(tableName: String): String = when (org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest) {
        is SQLServerDialect -> "dbo.$tableName"
        is OracleDialect -> "${this.user}.$tableName"
        is MysqlDialect -> "${this.db!!.name}.$tableName"
        else -> "public.$tableName"
    }

    @Test
    fun testListTablesInAllSchemas() {
        withDb { testDb ->
            if (currentDialectTest.supportsCreateSchema) {
                val one = prepareSchemaForTest("one")

                try {
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createSchema(one)
                    // table "one.one" is created in new schema by db because of name
                    // even though current schema has not been set to the new one above
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(OneOneTable)

                    // so new table will not appear in list of tables in current schema
                    assertFalse(org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listTables().any { it.equals(OneOneTable.tableName, ignoreCase = true) })
                    // but new table appears in list of tables from all schema
                    assertTrue(org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listTablesInAllSchemas().any { it.equals(OneOneTable.tableName, ignoreCase = true) })
                    assertTrue(OneOneTable.exists())
                } finally {
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(OneOneTable)
                    val cascade = testDb != TestDB.SQLSERVER
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropSchema(one, cascade = cascade)
                }
            }
        }
    }

    @Test
    fun `create table with quoted name with camel case`() {
        val testTable = object : IntIdTable("quotedTable") {
            val int = integer("intColumn")
        }

        withDb {
            try {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(testTable)
                assertTrue(testTable.exists())
                testTable.insert { it[int] = 10 }
                assertEquals(10, testTable.selectAll().singleOrNull()?.get(testTable.int))
            } finally {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(testTable)
            }
        }
    }

    /**
     * Note on Oracle exclusion in this test:
     * Oracle names are not case-sensitive. They can be made case-sensitive by using quotes around them. The Oracle JDBC
     * driver converts the entire SQL INSERT statement to upper case before extracting the table name from it. This
     * happens regardless of whether there is a dot in the name. Even when a name is quoted, the driver converts
     * it to upper case. Therefore, the INSERT statement fails when it contains a quoted table name because it attempts
     * to insert into a table that does not exist (“SOMENAMESPACE.SOMETABLE” is not found) . It does not fail when the
     * table name is not quoted because the case would not matter in that scenario.
     */
    @Test
    fun `create table with dot in name without creating schema beforehand`() {
        withDb(excludeSettings = listOf(TestDB.ORACLE)) {
            val q = db.identifierManager.quoteString
            val tableName = "${q}SomeNamespace.SomeTable$q"

            val tester = object : IntIdTable(tableName) {
                val text_col = text("text_col")
            }

            try {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(tester)
                assertTrue(tester.exists())

                val id = tester.insertAndGetId { it[text_col] = "Inserted text" }
                tester.update({ tester.id eq id }) { it[text_col] = "Updated text" }
                tester.deleteWhere { tester.id eq id }
            } finally {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(tester)
            }
        }
    }
}

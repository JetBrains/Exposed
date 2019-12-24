package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test

import kotlin.test.assertFails

class CreateTableTests : DatabaseTestsBase() {
    // https://github.com/JetBrains/Exposed/issues/709
    @Test
    fun createTableWithDuplicateColumn() {
        val assertionFailureMessage = "Can't create a table with multiple columns having the same name"

        withDb() {
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
    fun primaryKeyCreateTableTest() {
        val account = object : Table("Account") {
            val id1 = integer("id1")
            val id2 = integer("id2")

            override val primaryKey = PrimaryKey(id1, id2)
        }
        withDb() {
            val id1ProperName = account.id1.name.inProperCase()
            val id2ProperName = account.id2.name.inProperCase()
            val tableName = account.tableName

            assertEquals(
                    "CREATE TABLE " + addIfNotExistsIfSupported() + "${tableName.inProperCase()} (" +
                            "${account.columns.joinToString { it.descriptionDdl() }}, " +
                            "CONSTRAINT pk_$tableName PRIMARY KEY ($id1ProperName, $id2ProperName)" +
                            ")",
                    account.ddl)
        }
    }

    @Test
    fun primaryKeyWithConstraintNameCreateTableTest() {
        val pkConstraintName = "PKConstraintName"

        // Table with composite primary key
        withDb() {
            val id1ProperName = Person.id1.name.inProperCase()
            val id2ProperName = Person.id2.name.inProperCase()
            val tableName = Person.tableName

            assertEquals(
                    "CREATE TABLE " + addIfNotExistsIfSupported() + "${tableName.inProperCase()} (" +
                            "${Person.columns.joinToString { it.descriptionDdl() }}, " +
                            "CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)" +
                            ")",
                    Person.ddl)
        }

        //Table with single column in primary key.
        val user = object : Table("User") {
            val user_name = varchar("user_name", 25)

            override val primaryKey = PrimaryKey(user_name, name = pkConstraintName)
        }
        withDb() {
            val userNameProperName = user.user_name.name.inProperCase()
            val tableName = TransactionManager.current().identity(user)

            // Must generate primary key constraint, because the constraint name was defined.
            assertEquals(
                    "CREATE TABLE " + addIfNotExistsIfSupported() + "$tableName (" +
                            "${user.columns.joinToString { it.descriptionDdl() }}, " +
                            "CONSTRAINT $pkConstraintName PRIMARY KEY ($userNameProperName)" +
                            ")",
                    user.ddl)
        }
    }

    @Test
    fun addCompositePrimaryKeyToTableH2Test() {
        withDb(TestDB.H2) {
            val tableName = Person.tableName
            val tableProperName = tableName.inProperCase()
            val id1ProperName = Person.id1.name.inProperCase()
            val ddlId1 = Person.id1.ddl
            val id2ProperName = Person.id2.name.inProperCase()
            val ddlId2 = Person.id2.ddl
            val pkConstraintName = Person.primaryKey.name

            assertEquals(1, ddlId1.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${Person.id1.descriptionDdl()}", ddlId1.first())

            assertEquals(2, ddlId2.size)
            assertEquals("ALTER TABLE $tableProperName ADD $id2ProperName ${Person.id2.columnType.sqlType()}", ddlId2.first())
            assertEquals("ALTER TABLE $tableProperName ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)", Person.id2.ddl.last())
        }
    }

    @Test
    fun addCompositePrimaryKeyToTableNotH2Test() {
        withTables(excludeSettings = listOf(TestDB.H2, TestDB.H2_MYSQL), tables = *arrayOf(Person)) {
            val tableName = Person.tableName
            val tableProperName = tableName.inProperCase()
            val id1ProperName = Person.id1.name.inProperCase()
            val ddlId1 = Person.id1.ddl
            val id2ProperName = Person.id2.name.inProperCase()
            val ddlId2 = Person.id2.ddl
            val pkConstraintName = Person.primaryKey.name

            assertEquals(1, ddlId1.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${Person.id1.descriptionDdl()}", ddlId1.first())

            assertEquals(1, ddlId2.size)
            assertEquals("ALTER TABLE $tableProperName ADD ${Person.id2.descriptionDdl()}, ADD CONSTRAINT $pkConstraintName PRIMARY KEY ($id1ProperName, $id2ProperName)", ddlId2.first())
        }
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
}

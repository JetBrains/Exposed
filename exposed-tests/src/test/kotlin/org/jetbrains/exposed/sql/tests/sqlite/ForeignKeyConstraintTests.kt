package org.jetbrains.exposed.sql.tests.sqlite

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.Category
import org.jetbrains.exposed.sql.tests.shared.DEFAULT_CATEGORY_ID
import org.jetbrains.exposed.sql.tests.shared.Item
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test

class ForeignKeyConstraintTests : DatabaseTestsBase() {

    @Test
    fun `test ON DELETE SET DEFAULT for databases that support it without SQLite`() {
        withDb(excludeSettings = TestDB.ALL_MARIADB + TestDB.ALL_MYSQL + listOf(TestDB.SQLITE, TestDB.ORACLE)) {
            testOnDeleteSetDefault()
        }
    }

    @Test
    fun `test ON DELETE SET DEFAULT for SQLite`() {
        Assume.assumeTrue(TestDB.SQLITE in TestDB.enabledDialects())

        transaction(
            Database.connect(
                "jdbc:sqlite:file:test?mode=memory&cache=shared&foreign_keys=on",
                user = "root",
                driver = "org.sqlite.JDBC"
            )
        ) {
            testOnDeleteSetDefault()
        }
    }

    private fun Transaction.testOnDeleteSetDefault() {
        SchemaUtils.create(Category, Item)

        Category.insert {
            it[id] = DEFAULT_CATEGORY_ID
            it[name] = "Default"
        }

        val saladsId = 1
        Category.insert {
            it[id] = saladsId
            it[name] = "Salads"
        }

        val tabboulehId = 0
        Item.insert {
            it[id] = tabboulehId
            it[name] = "Tabbouleh"
            it[categoryId] = saladsId
        }

        assertEquals(
            saladsId,
            Item.selectAll().where { Item.id eq tabboulehId }.single()[Item.categoryId]
        )

        Category.deleteWhere { id eq saladsId }

        assertEquals(
            DEFAULT_CATEGORY_ID,
            Item.selectAll().where { Item.id eq tabboulehId }.single()[Item.categoryId]
        )

        SchemaUtils.drop(Category, Item)
    }

    @Test
    fun `test ON DELETE RESTRICT for databases that support it without SQLite`() {
        withDb(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER)) {
            testRestrict(isTestingOnDelete = true)
        }
    }

    @Test
    fun `test ON DELETE RESTRICT for SQLite`() {
        Assume.assumeTrue(TestDB.SQLITE in TestDB.enabledDialects())

        transaction(
            Database.connect(
                "jdbc:sqlite:file:test?mode=memory&cache=shared&foreign_keys=on",
                user = "root",
                driver = "org.sqlite.JDBC"
            )
        ) {
            testRestrict(isTestingOnDelete = true)
        }
    }

    @Test
    fun `test ON UPDATE RESTRICT for databases that support it without SQLite`() {
        withDb(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)) {
            testRestrict(isTestingOnDelete = false)
        }
    }

    @Test
    fun `test ON UPDATE RESTRICT for SQLite`() {
        Assume.assumeTrue(TestDB.SQLITE in TestDB.enabledDialects())

        transaction(
            Database.connect(
                "jdbc:sqlite:file:test?mode=memory&cache=shared&foreign_keys=on",
                user = "root",
                driver = "org.sqlite.JDBC"
            )
        ) {
            testRestrict(isTestingOnDelete = false)
        }
    }

    private fun testRestrict(isTestingOnDelete: Boolean) {
        val country = object : Table("Country") {
            val id = integer("id")
            val name = varchar(name = "name", length = 20)

            override val primaryKey = PrimaryKey(id)
        }

        val city = object : Table("City") {
            val id = integer("id")
            val name = varchar(name = "name", length = 20)
            val countryId = integer("countryId")
                .references(
                    country.id,
                    onDelete = if (isTestingOnDelete) ReferenceOption.RESTRICT else null,
                    onUpdate = if (isTestingOnDelete) null else ReferenceOption.RESTRICT,
                )

            override val primaryKey = PrimaryKey(id)
        }

        SchemaUtils.drop(country, city)
        SchemaUtils.create(country, city)

        val lebanonId = 0
        country.insert {
            it[id] = lebanonId
            it[name] = "Lebanon"
        }

        val beirutId = 0
        city.insert {
            it[id] = beirutId
            it[name] = "Beirut"
            it[countryId] = 0
        }

        if (isTestingOnDelete) {
            expectException<ExposedSQLException> {
                country.deleteWhere { id eq lebanonId }
            }
        } else {
            expectException<ExposedSQLException> {
                country.update({ country.id eq lebanonId }) {
                    it[id] = 1
                }
            }
        }
    }

    @Test
    fun testUpdateAndDeleteRulesReadCorrectlyWhenNotSpecifiedInChildTable() {
        val category = object : Table("Category") {
            val id = integer("id")

            override val primaryKey = PrimaryKey(id)
        }

        val item = object : Table("Item") {
            val id = integer("id")
            val categoryId = integer("categoryId").references(category.id)

            override val primaryKey = PrimaryKey(id)
        }

        withTables(category, item) { testDb ->
            if (currentDialectTest.supportsOnUpdate) {
                val constraints = connection.metadata {
                    tableConstraints(listOf(item))
                }
                constraints.values.forEach { list ->
                    list.forEach {
                        // According to the documentation: "NO ACTION: A keyword from standard SQL. For InnoDB, this is equivalent to RESTRICT;"
                        // https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html
                        if (testDb == TestDB.MYSQL_V5) {
                            assertEquals(ReferenceOption.NO_ACTION, it.updateRule)
                            assertEquals(ReferenceOption.NO_ACTION, it.deleteRule)
                        } else {
                            assertEquals(currentDialectTest.defaultReferenceOption, it.updateRule)
                            assertEquals(currentDialectTest.defaultReferenceOption, it.deleteRule)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testUpdateAndDeleteRulesReadCorrectlyWhenSpecifiedInChildTable() {
        val category = object : Table("Category") {
            val id = integer("id")

            override val primaryKey = PrimaryKey(id)
        }

        val item = object : Table("Item") {
            val id = integer("id")
            val categoryId = integer("categoryId")
                .references(
                    category.id,
                    onUpdate = ReferenceOption.CASCADE,
                    onDelete = ReferenceOption.CASCADE
                )

            override val primaryKey = PrimaryKey(id)
        }

        withTables(category, item) {
            if (currentDialectTest.supportsOnUpdate) {
                val constraints = connection.metadata {
                    tableConstraints(listOf(item))
                }
                constraints.values.forEach { list ->
                    list.forEach {
                        assertEquals(ReferenceOption.CASCADE, it.updateRule)
                        assertEquals(ReferenceOption.CASCADE, it.deleteRule)
                    }
                }
            }
        }
    }

    @Test
    fun testTableWithDotInName() {
        withDb {
            val q = db.identifierManager.quoteString
            val parentTableName = "${q}SOMENAMESPACE.SOMEPARENTTABLE$q"
            val childTableName = "${q}SOMENAMESPACE.SOMECHILDTABLE$q"

            val parentTester = object : IntIdTable(parentTableName) {
                val text_col = text("parent_text_col")
            }
            val childTester = object : IntIdTable(childTableName) {
                val text_col = text("child_text_col")
                val int_col = reference("child_int_col", parentTester.id)
            }

            try {
                SchemaUtils.create(parentTester)
                assertTrue(parentTester.exists())
                SchemaUtils.create(childTester)
                assertTrue(childTester.exists())

                val parentId = parentTester.insertAndGetId {
                    it[text_col] = "Parent text"
                }
                val childId = childTester.insertAndGetId {
                    it[text_col] = "Child text"
                    it[childTester.int_col] = parentId
                }

                parentTester.update({ parentTester.id eq parentId }) { it[text_col] = "Updated parent text" }

                childTester.update({ childTester.id eq childId }) { it[text_col] = "Updated child text" }
                childTester.deleteWhere { childTester.id eq childId }
            } finally {
                SchemaUtils.drop(childTester)
                SchemaUtils.drop(parentTester)
            }
        }
    }
}

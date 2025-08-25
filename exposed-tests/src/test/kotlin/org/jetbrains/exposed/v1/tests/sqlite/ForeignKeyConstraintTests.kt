package org.jetbrains.exposed.v1.tests.sqlite
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.Category
import org.jetbrains.exposed.v1.tests.shared.DEFAULT_CATEGORY_ID
import org.jetbrains.exposed.v1.tests.shared.Item
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.expectException
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

    private fun JdbcTransaction.testOnDeleteSetDefault() {
        org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(Category, Item)

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

        org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(Category, Item)
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

        withTables(category, item) {
            if (currentDialectTest.supportsOnUpdate) {
                val constraints = connection.metadata {
                    tableConstraints(listOf(item))
                }
                constraints.values.forEach { list ->
                    list.forEach {
                        assertEquals(currentDialectTest.defaultReferenceOption, it.updateRule)
                        assertEquals(currentDialectTest.defaultReferenceOption, it.deleteRule)
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
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(parentTester)
                assertTrue(parentTester.exists())
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(childTester)
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
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(childTester)
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(parentTester)
            }
        }
    }

    @Test
    fun testColumnConstraintsWithFKColumnsThatNeedQuoting() {
        val parent = object : LongIdTable("parent") {
            val scale = integer("scale").uniqueIndex()
        }
        val child = object : LongIdTable("child") {
            val scale = reference("scale", parent.scale)
        }

        // EXPOSED-711 https://youtrack.jetbrains.com/issue/EXPOSED-711/Oracle-tableConstraints-columnContraints-dont-return-foreign-keys
        withTables(excludeSettings = listOf(TestDB.ORACLE), child, parent) {
            val constraints = currentDialectMetadataTest.columnConstraints(child)
            // columnConstraints() only return entry for table that has column with FK
            assertEquals(1, constraints.keys.size)
            assertEquals(child.scale.foreignKey?.fkName, constraints.entries.single().value.single().fkName)
        }
    }
}

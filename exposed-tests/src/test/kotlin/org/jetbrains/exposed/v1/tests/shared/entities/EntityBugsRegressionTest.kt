@file: Suppress("MatchingDeclarationName", "Filename", "ClassNaming")

package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class `Table id not in Record Test issue 1341` : DatabaseTestsBase() {

    object NamesTable : IdTable<Int>("names_table") {
        val first = varchar("first", 50)

        val second = varchar("second", 50)

        override val id = integer("id").autoIncrement().entityId()

        override val primaryKey = PrimaryKey(id)
    }

    object AccountsTable : IdTable<Int>("accounts_table") {
        val name = reference("name", NamesTable)
        override val id: Column<EntityID<Int>> = integer("id").autoIncrement().entityId()
        override val primaryKey = PrimaryKey(id)
    }

    class Names(id: EntityID<Int>) : IntEntity(id) {
        var first: String by NamesTable.first
        var second: String by NamesTable.second
        companion object : IntEntityClass<Names>(NamesTable)
    }

    class Accounts(id: EntityID<Int>) : IntEntity(id) {
        var name: Names by Names referencedOn AccountsTable.name

        companion object : EntityClass<Int, Accounts>(AccountsTable) {
            fun new(accountName: Pair<String, String>): Accounts = new {
                this.name = Names.new {
                    first = accountName.first
                    second = accountName.second
                }
            }
        }
    }

    @Test
    fun testRegression() {
        withTables(NamesTable, AccountsTable) {
            val account = Accounts.new("first" to "second")
            assertEquals("first", account.name.first)
            assertEquals("second", account.name.second)
        }
    }
}

class `Text id loosed on insert issue 1379` : DatabaseTestsBase() {
    abstract class TextEntity(id: EntityID<String>) : Entity<String>(id)

    abstract class TextEntityClass<out E : TextEntity>(table: IdTable<String>, entityType: Class<E>? = null) : EntityClass<String, E>(table, entityType)

    open class TextIdTable(name: String = "", columnName: String = "id") : IdTable<String>(name) {
        final override val id: Column<EntityID<String>> = text(columnName).entityId()
        final override val primaryKey = PrimaryKey(id)
    }

    class Obj1(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<Obj1>(Table1)
        var a by Table1.a
    }

    class Obj2(id: EntityID<String>) : TextEntity(id) {
        companion object : TextEntityClass<Obj2>(Table2)
        var a by Table2.a
        var ref by Obj1 referencedOn Table2.ref
    }

    object Table2 : TextIdTable() {
        val a = text("a")
        val ref = reference("ref", Table1)
    }

    object Table1 : LongIdTable() {
        val a = text("a")
    }

    @Test
    fun testRegression() {
        val runTests = TestDB.entries - TestDB.POSTGRESQL
        withTables(runTests, Table1, Table2) {
            val obj1 = Obj1.new {
                a = "hello world!"
            }

            Obj2.new("test") {
                a = "bye world!"
                ref = obj1
            }
        }
    }
}

class EntityCacheNotUpdatedOnCommitIssue1380 : DatabaseTestsBase() {
    object TestTable : IntIdTable() {
        val value = integer("value")
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        var value by TestTable.value

        companion object : IntEntityClass<TestEntity>(TestTable)
    }

    @Test fun testRegression() {
        withTables(TestTable) {
            val entity1 = TestEntity.new { value = 1 }

            assertNotNull(TestEntity.findById(entity1.id))
            TestEntity.findById(entity1.id)?.delete()
            commit()
            assertNull(TestEntity.findById(entity1.id))
        }
    }
}

class AccessToPrimaryKeyFailsWithClassCastExceptionYT409 : DatabaseTestsBase() {

    @Test
    fun testCustomEntityIdColumnAccess() {
        val tester = object : IdTable<String>() {

            val value = varchar("value", 128)

            override val primaryKey: PrimaryKey = PrimaryKey(value)
            override val id: Column<EntityID<String>> = value.entityId()
        }

        withTables(tester) {
            tester.insert {
                it[tester.value] = "test-value"
            }
            val entry = tester.selectAll().first()
            assertEquals("test-value", entry[tester.value])
            assertEquals("test-value", entry[tester.id].value)
        }
    }
}

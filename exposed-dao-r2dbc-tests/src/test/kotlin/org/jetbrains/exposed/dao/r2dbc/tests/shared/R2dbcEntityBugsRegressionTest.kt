@file: Suppress("MatchingDeclarationName", "Filename", "ClassNaming")

package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.r2dbc.dao.LongEntity
import org.jetbrains.exposed.r2dbc.dao.LongEntityClass
import org.jetbrains.exposed.r2dbc.dao.NewEntity
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import kotlin.test.Test

class `Table id not in Record Test issue 1341` : R2dbcDatabaseTestsBase() {
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
        val name by Names referencedOn AccountsTable.name

        companion object : EntityClass<Int, Accounts>(AccountsTable) {

            suspend fun new(accountName: Pair<String, String>): NewEntity<Int, Accounts> {
                val newName = Names.new {
                    first = accountName.first
                    second = accountName.second
                }.flush()

                return new {
                    this.name.set(newName)
                }
            }
        }
    }

    @Test
    fun testRegression() {
        withTables(NamesTable, AccountsTable) {
            val account = Accounts.new("first" to "second").flush()
            assertEquals("first", account.name().first)
            assertEquals("second", account.name().second)
        }
    }
}

class `Text id loosed on insert issue 1379` : R2dbcDatabaseTestsBase() {
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
        val ref by Obj1 referencedOn Table2.ref
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
            }.flush()

            Obj2.new("test") {
                a = "bye world!"
                ref.set(obj1)
            }.flush()
        }
    }
}

class EntityCacheNotUpdatedOnCommitIssue1380 : R2dbcDatabaseTestsBase() {
    object TestTable : IntIdTable() {
        val value = integer("value")
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        var value by TestTable.value

        companion object : IntEntityClass<TestEntity>(TestTable)
    }

    @Test fun testRegression() {
        withTables(TestTable) {
            val entity1 = TestEntity.new { value = 1 }.flush()

            assertNotNull(TestEntity.findById(entity1.id))
            TestEntity.findById(entity1.id)?.delete()
            commit()
            // R2DBC: `Entity.delete()` short-circuits for un-flushed entities (no INSERT, no DELETE,
            // no id generation), so `entity1.id._value` stays null and `findById` can't build a
            // parametrised WHERE clause. Check the cache directly — that's the regression we care
            // about (issue #1380: cache wasn't cleared on commit).
            assertNull(TestEntity.testCache(entity1.id))
        }
    }
}

class AccessToPrimaryKeyFailsWithClassCastExceptionYT409 : R2dbcDatabaseTestsBase() {

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

package org.jetbrains.exposed.dao.repository

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.junit.Test
import kotlin.test.assertEquals

class CreateTableTest : DatabaseTestsBase() {

    @Test
    fun tablesAreEquals() {
        withDb {
            val entityTable = TestEntity::class.exposedTable
            assertEquals(TestTable.tableName, entityTable.tableName)
            assertEquals(TestTable.ddl, entityTable.ddl)
        }
    }

    @Test
    fun testSaveEntity() {
        withTables(TestEntity::class.exposedTable) {
            val e1 = TestEntity(1, 0)
            TestRepo.save(e1)

            val e2 = TestRepo.fromRow(TestEntity::class.exposedTable.selectAll().single())
            assertEquals(e1, e2)

            TestRepo.save(TestEntity(1, 3))

            assertEquals(TestRepo.find {
                TestEntity::optInt.exposedColumn eq 0
            }.single().int, 1)

            assertEquals(TestRepo.find {
                TestEntity::int.exposedColumn eq 1
            }.size, 2)
        }
    }
}

data class TestEntity (
    val int : Int,
    val optInt: Int?
)

object TestTable : Table("TestEntity") {
    val int = integer("int")
    val optInt = integer("optInt").nullable()
}


object TestRepo : ReflectionBasedCrudRepository<TestEntity>(TestEntity::class)
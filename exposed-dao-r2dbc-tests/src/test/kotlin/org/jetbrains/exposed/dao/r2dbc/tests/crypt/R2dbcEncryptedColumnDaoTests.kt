package org.jetbrains.exposed.dao.r2dbc.tests.crypt

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.crypt.Algorithms
import org.jetbrains.exposed.v1.crypt.encryptedBinary
import org.jetbrains.exposed.v1.crypt.encryptedVarchar
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.assertNotNull
import kotlin.test.Test

class R2dbcEncryptedColumnDaoTests : R2dbcDatabaseTestsBase() {
    object TestTable : IntIdTable() {
        val varchar = encryptedVarchar("varchar", 100, Algorithms.AES_256_PBE_GCM("passwd", "12345678"))
        val binary = encryptedBinary("binary", 100, Algorithms.AES_256_PBE_GCM("passwd", "12345678"))
    }

    class ETest(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<ETest>(TestTable)

        var varchar by TestTable.varchar
        var binary by TestTable.binary
    }

    @Test
    fun testEncryptedColumnsWithCachedEntities() {
        val varcharValue = "varchar"
        val binaryValue = "binary".toByteArray()

        fun R2dbcTransaction.assertNotNullWithCorrectFields(actualEntity: ETest?) {
            assertNotNull(actualEntity)
            assertEquals(varcharValue, actualEntity.varchar)
            assertEquals(binaryValue.contentToString(), actualEntity.binary.contentToString())
        }

        withTables(TestTable) {
            val entity = ETest.new {
                varchar = varcharValue
                binary = binaryValue
            }

            // confirm new entity has been cached
            assertNotNull(entityCache.find(ETest, entity.id))

            // findById() should get cached entity without calling wrapRows()
            val cachedEntity1 = ETest.findById(entity.id)
            assertNotNullWithCorrectFields(cachedEntity1)

            // but find() should skip cache & call wrapRows()
            val foundEntity1 = ETest.find { TestTable.id eq entity.id }.singleOrNull()
            assertNotNullWithCorrectFields(foundEntity1)

            // DSL result passed to wrapRow() also skips the cache
            TestTable.selectAll().first().let {
                val foundEntity2 = ETest.wrapRow(it)
                assertNotNullWithCorrectFields(foundEntity2)
            }
        }
    }

    @Test
    fun testEncryptedColumnsWithDao() {
        withTables(TestTable) {
            val varcharValue = "varchar"
            val binaryValue = "binary".toByteArray()

            val entity = ETest.new {
                varchar = varcharValue
                binary = binaryValue
            }
            assertEquals(varcharValue, entity.varchar)
            assertEquals(binaryValue, entity.binary)

            TestTable.selectAll().first().let {
                assertEquals(varcharValue, it[TestTable.varchar])
                assertEquals(String(binaryValue), String(it[TestTable.binary]))
            }
        }
    }
}

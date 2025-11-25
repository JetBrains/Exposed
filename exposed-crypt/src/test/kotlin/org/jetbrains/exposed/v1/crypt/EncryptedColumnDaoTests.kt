package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Test

class EncryptedColumnDaoTests : DatabaseTestsBase() {
    object TestTable : IntIdTable() {
        val varchar = encryptedVarchar("varchar", 100, Algorithms.AES_256_PBE_GCM("passwd", "12345678"))
        val binary = encryptedBinary("binary", 100, Algorithms.AES_256_PBE_GCM("passwd", "12345678"))
    }

    class ETest(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ETest>(TestTable)

        var varchar by TestTable.varchar
        var binary by TestTable.binary
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

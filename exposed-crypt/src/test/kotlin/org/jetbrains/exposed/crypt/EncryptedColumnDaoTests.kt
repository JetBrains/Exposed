package org.jetbrains.exposed.crypt

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

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

package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.binaryLiteral
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.junit.Test

class BinaryColumnTypeTests : DatabaseTestsBase() {
    @Test
    fun testBinaryColumn() {
        val defaultValue = "default-value".toByteArray()
        val tester = object : IntIdTable("testBinaryColumn") {
            val bin = binary("bin", 256).default(defaultValue)
        }

        withTables(tester) {
            assertEqualLists(emptyList(), SchemaUtils.statementsRequiredToActualizeScheme(tester))

            val id1 = tester.insertAndGetId { }
            val row1 = tester.selectAll().where { tester.id eq id1 }.first()
            val binValue1 = row1[tester.bin]
            assertEqualLists(defaultValue.toList(), binValue1.toList())

            val literalValue = "literal-value".toByteArray()
            val id2 = tester.insertAndGetId { it[tester.bin] = binaryLiteral(literalValue) }
            val row2 = tester.selectAll().where { tester.id eq id2 }.first()
            val binValue2 = row2[tester.bin]
            assertEqualLists(literalValue.toList(), binValue2.toList())

            val nonLiteralValue = "non-literal-value".toByteArray()
            val id3 = tester.insertAndGetId { it[tester.bin] = nonLiteralValue }
            val row3 = tester.selectAll().where { tester.id eq id3 }.first()
            val binValue3 = row3[tester.bin]
            assertEqualLists(nonLiteralValue.toList(), binValue3.toList())
        }
    }
}

package org.jetbrains.exposed.v1.plugin.core.tables

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ColumnMappingTest {
    @Test
    fun testKeyPartsOfColumnMapping() {
        val unqualified = ColumnMapping("EVENTS.CREATED_AT", "timestamp(\"CREATED_AT\")")
        assertNull(unqualified.schemaPart)
        assertEquals("EVENTS", unqualified.tablePart)
        assertEquals("CREATED_AT", unqualified.columnPart)

        val qualified = ColumnMapping(" sales . orders . id ", "long(\"ID\")")
        assertEquals("sales", qualified.schemaPart)
        assertEquals("orders", qualified.tablePart)
        assertEquals("id", qualified.columnPart)

        val quoted = ColumnMapping("\"users\".ID", "integer(\"ID\")")
        assertEquals("\"users\"", quoted.tablePart)
        assertEquals("ID", quoted.columnPart)
    }

    @Test
    fun testMalformedKeysAndBlankCodeAreRejected() {
        assertContains(
            assertFailsWith<IllegalArgumentException> { ColumnMapping("EVENTS", "x").keyParts }.message.orEmpty(),
            "TABLE.COLUMN"
        )
        assertContains(
            assertFailsWith<IllegalArgumentException> { ColumnMapping("A.B.C.D", "x").keyParts }.message.orEmpty(),
            "TABLE.COLUMN"
        )
        assertContains(
            assertFailsWith<IllegalArgumentException> { ColumnMapping("EVENTS..ID", "x").keyParts }.message.orEmpty(),
            "TABLE.COLUMN"
        )
        assertFailsWith<IllegalArgumentException> { ColumnMapping(" ", "x") }
        assertFailsWith<IllegalArgumentException> { ColumnMapping("EVENTS.ID", " ") }
    }
}

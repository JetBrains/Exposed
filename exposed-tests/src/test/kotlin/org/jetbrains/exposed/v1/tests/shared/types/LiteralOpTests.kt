package org.jetbrains.exposed.v1.tests.shared.types

import org.jetbrains.exposed.v1.core.BasicBinaryColumnType
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.ByteColumnType
import org.jetbrains.exposed.v1.core.CharacterColumnType
import org.jetbrains.exposed.v1.core.DecimalColumnType
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.FloatColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.ShortColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.UByteColumnType
import org.jetbrains.exposed.v1.core.UIntegerColumnType
import org.jetbrains.exposed.v1.core.ULongColumnType
import org.jetbrains.exposed.v1.core.UShortColumnType
import org.jetbrains.exposed.v1.core.UuidColumnType
import org.jetbrains.exposed.v1.core.asLiteral
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.core.literal
import org.jetbrains.exposed.v1.tests.NOT_APPLICABLE_TO_R2DBC
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.Uuid

@Tag(NOT_APPLICABLE_TO_R2DBC)
class LiteralOpTests {
    @Test
    fun testLiteralResolvesSupportedScalarTypes() {
        assertIs<BooleanColumnType>(literal(true).columnType)
        assertIs<ByteColumnType>(literal(1.toByte()).columnType)
        assertIs<UByteColumnType>(literal(1.toUByte()).columnType)
        assertIs<ShortColumnType>(literal(1.toShort()).columnType)
        assertIs<UShortColumnType>(literal(1.toUShort()).columnType)
        assertIs<IntegerColumnType>(literal(1).columnType)
        assertIs<UIntegerColumnType>(literal(1U).columnType)
        assertIs<LongColumnType>(literal(1L).columnType)
        assertIs<ULongColumnType>(literal(1UL).columnType)
        assertIs<FloatColumnType>(literal(1.0F).columnType)
        assertIs<DoubleColumnType>(literal(1.0).columnType)
        assertIs<TextColumnType>(literal("one").columnType)
        assertIs<CharacterColumnType>(literal('1').columnType)
        assertIs<BasicBinaryColumnType>(literal(byteArrayOf(1)).columnType)
        assertIs<UuidColumnType>(literal(Uuid.parse("123e4567-e89b-12d3-a456-426614174000")).columnType)
        assertIs<UUIDColumnType>(literal(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")).columnType)
    }

    @Test
    fun testLiteralPreservesDecimalPrecisionAndScale() {
        val decimal = literal(BigDecimal("1234.560"))
        val columnType = assertIs<DecimalColumnType>(decimal.columnType)

        assertEquals(7, columnType.precision)
        assertEquals(3, columnType.scale)
    }

    @Test
    fun testLiteralRejectsUnsupportedType() {
        val exception = assertFailsWith<IllegalArgumentException> {
            literal(intArrayOf(1, 2))
        }

        assertContains(exception.message.orEmpty(), "kotlin.IntArray")
        assertContains(exception.message.orEmpty(), "explicit Expression")
    }

    @Test
    fun testAsLiteralPreservesBinaryValueAndColumnType() {
        val bytes = byteArrayOf(0x89.toByte(), 0x00, 0xFF.toByte())
        val column = Table("unused").binary("data")
        val result = column.asLiteral(bytes)

        assertIs<BasicBinaryColumnType>(result.columnType)
        assertContentEquals(bytes, result.value)
    }
}

package org.jetbrains.exposed.v1.plugin.core.tables

import org.jetbrains.exposed.v1.plugin.core.tables.mapping.DefaultValue
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.defaultValueFor
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.toColumnDefinition
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.H2Syntax
import org.junit.jupiter.api.Test
import java.sql.Types
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultValuesTest {
    // Compares what the generated Kotlin code should be, the SQL type of the column,
    // and the default string as the database metadata reports it

    @Test
    fun testNumericAndBooleanLiterals() {
        assertLiteral("42", "INT", "42")
        assertLiteral("-1", "INT", "-1")
        assertLiteral("7", "SMALLINT", "7")
        assertLiteral("9000000000L", "BIGINT", "9000000000")
        assertLiteral("1.5f", "FLOAT", "1.5")
        assertLiteral("2.25", "DOUBLE PRECISION", "2.25")
        assertLiteral("3.0", "DOUBLE PRECISION", "3")
        assertLiteral("BigDecimal(\"12.50\")", "NUMERIC(10, 2)", "12.50")
        assertLiteral("true", "BOOLEAN", "TRUE")
        assertLiteral("false", "BOOLEAN", "FALSE")
        assertUnsupported("INT", "'42'")
        assertUnsupported("BOOLEAN", "1")
    }

    @Test
    fun testStringCharacterAndUuidLiterals() {
        assertLiteral("\"hello\"", "VARCHAR(20)", "'hello'")
        assertLiteral("\"it's\"", "VARCHAR(20)", "'it''s'")
        assertLiteral("\"\"", "VARCHAR(5)", "''")
        assertLiteral("\"abc\"", "CHAR(3)", "'abc'")
        assertLiteral("'a'", "CHAR", "'a'")
        assertUnsupported("CHAR", "'ab'")
        // an unquoted value in a string column is an identifier or a function, not a string
        assertUnsupported("VARCHAR(20)", "CURRENT_USER")
        assertLiteral(
            "Uuid.parse(\"123e4567-e89b-12d3-a456-426614174000\")",
            "UUID",
            "'123e4567-e89b-12d3-a456-426614174000'"
        )
        assertLiteral(
            "UUID.fromString(\"123e4567-e89b-12d3-a456-426614174000\")",
            "UUID",
            "'123e4567-e89b-12d3-a456-426614174000'",
            uuidColumnType = UuidColumnType.JAVA
        )
        assertUnsupported("UUID", "RANDOM_UUID()")
    }

    @Test
    fun testTemporalLiteralsAndCurrentTimeFunctions() {
        assertLiteral("LocalDate(2024, 1, 31)", "DATE", "DATE '2024-01-31'")
        assertLiteral("LocalTime(10, 30, 0)", "TIME", "TIME '10:30:00'")
        assertLiteral("LocalTime(10, 30, 0, 500000000)", "TIME", "TIME '10:30:00.5'")
        assertLiteral("LocalDateTime(2024, 1, 31, 10, 0, 0)", "TIMESTAMP", "TIMESTAMP '2024-01-31 10:00:00'")
        assertLiteral(
            "LocalDateTime(2024, 1, 31, 10, 0, 0, 123456789)",
            "TIMESTAMP",
            "TIMESTAMP '2024-01-31 10:00:00.123456789'"
        )
        assertLiteral(
            "LocalDate.of(2024, 1, 31)",
            "DATE",
            "DATE '2024-01-31'",
            dateTimeColumnType = DateTimeColumnType.JAVA_TIME
        )
        assertLiteral(
            "LocalDateTime.of(2024, 1, 31, 10, 0, 0)",
            "TIMESTAMP",
            "TIMESTAMP '2024-01-31 10:00:00'",
            dateTimeColumnType = DateTimeColumnType.JAVA_TIME
        )

        assertUnsupported("DATE", "DATE '2024-01-31'", dateTimeColumnType = DateTimeColumnType.JODA_TIME)

        assertExpression("CurrentDateTime", "TIMESTAMP", "CURRENT_TIMESTAMP")
        assertExpression("CurrentDate", "DATE", "CURRENT_DATE")
        assertExpression("CurrentTimestampWithTimeZone", "TIMESTAMP(9) WITH TIME ZONE", "CURRENT_TIMESTAMP")
        assertExpression(
            "CurrentDateTime",
            "TIMESTAMP",
            "current_timestamp",
            dateTimeColumnType = DateTimeColumnType.JAVA_TIME
        )

        assertUnsupported("TIME", "CURRENT_TIME")
        assertUnsupported("DATE", "CURRENT_TIMESTAMP")
        assertUnsupported(
            "TIMESTAMP(9) WITH TIME ZONE",
            "CURRENT_TIMESTAMP",
            dateTimeColumnType = DateTimeColumnType.JODA_TIME
        )
    }

    @Test
    fun testEnumerationValues() {
        assertLiteral("Col.Baz", "ENUM('Bar', 'Baz')", "'Baz'")
        assertUnsupported("ENUM('Bar', 'Baz')", "'Qux'")
    }

    // TODO Try to make these types of defaults work
    @Test
    fun testBinaryArrayAndJsonLiteralsAreUnsupported() {
        assertUnsupported("VARBINARY(4)", "X'0102'")
        assertUnsupported("INT ARRAY", "ARRAY [1, 2]")
        assertUnsupported("JSON", "JSON '{}'")
    }

    private fun assertLiteral(
        expectedGenerated: String,
        sqlType: String,
        metadataReported: String,
        uuidColumnType: UuidColumnType = UuidColumnType.KOTLIN,
        dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
    ) {
        val default = defaultFor(sqlType, metadataReported, uuidColumnType, dateTimeColumnType)
        val literal = assertIs<DefaultValue.Literal>(default, "Expected a literal for $metadataReported on $sqlType, got $default")
        assertTrue(
            literal.code.toString().endsWith(expectedGenerated),
            "Expected code ending with '$expectedGenerated', got: ${literal.code}"
        )
    }

    private fun assertExpression(
        expectedGenerated: String,
        sqlType: String,
        metadataReported: String,
        dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
    ) {
        val default = defaultFor(sqlType, metadataReported, dateTimeColumnType = dateTimeColumnType)
        val expression = assertIs<DefaultValue.Expression>(default, "Expected an expression for $metadataReported on $sqlType, got $default")
        assertTrue(
            expression.code.toString().endsWith(expectedGenerated),
            "Expected '$expectedGenerated', got: ${expression.code}"
        )
    }

    private fun assertUnsupported(
        sqlType: String,
        metadataReported: String,
        dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
    ) {
        val default = defaultFor(sqlType, metadataReported, dateTimeColumnType = dateTimeColumnType)
        assertEquals(
            DefaultValue.Unsupported(metadataReported),
            default,
            "Expected $metadataReported on $sqlType to be unsupported"
        )
    }

    private fun defaultFor(
        sqlType: String,
        metadataReported: String,
        uuidColumnType: UuidColumnType = UuidColumnType.KOTLIN,
        dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
    ): DefaultValue {
        val column = column(Types.OTHER, sqlType, size = null, scale = null)
        val definition = assertNotNull(
            column.toColumnDefinition(H2Syntax, uuidColumnType, dateTimeColumnType),
            "Expected $sqlType to be mapped"
        )
        return definition.defaultValueFor(metadataReported, dateTimeColumnType, H2Syntax)
    }
}

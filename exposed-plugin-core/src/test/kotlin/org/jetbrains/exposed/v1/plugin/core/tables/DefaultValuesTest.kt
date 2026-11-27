package org.jetbrains.exposed.v1.plugin.core.tables

import org.jetbrains.exposed.v1.plugin.core.tables.mapping.DefaultValue
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.defaultValueFor
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.toColumnDefinition
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.DialectSyntax
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.H2Syntax
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.MariaDbSyntax
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.MySqlSyntax
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.SequenceReference
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.SqlServerSchemaSupport
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.SqlServerSyntax
import org.junit.jupiter.api.Test
import java.sql.Types
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertUnsupported("BOOLEAN", "'true'")
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

        assertEquals(listOf("Bar", "Baz"), MySqlSyntax.enumerationValues("enum('Bar','Baz')"))
        assertEquals(listOf("it's", "a b"), MySqlSyntax.enumerationValues("enum('it''s','a b')"))
        assertNull(MySqlSyntax.enumerationValues("VARCHAR(10)"))
    }

    // TODO Try to make these types of defaults work
    @Test
    fun testBinaryArrayAndJsonLiteralsAreUnsupported() {
        assertUnsupported("VARBINARY(4)", "X'0102'")
        assertUnsupported("INT ARRAY", "ARRAY [1, 2]")
        assertUnsupported("JSON", "JSON '{}'")
    }

    @Test
    fun testBareDateTimeStringsAreReadAsLiteralsOfTheColumnType() {
        // a database without typed literals (like SQLite) reports the bare string
        assertLiteral("LocalDate(2024, 1, 31)", "DATE", "'2024-01-31'")
        assertLiteral("LocalTime(10, 30, 0)", "TIME", "'10:30:00'")
        assertLiteral("LocalTime(10, 30, 0, 500000000)", "TIME", "'10:30:00.5'")
        assertLiteral("LocalDateTime(2024, 1, 31, 10, 0, 0)", "TIMESTAMP", "'2024-01-31 10:00:00'")
        assertLiteral("LocalDate.of(2024, 1, 31)", "DATE", "'2024-01-31'", dateTimeColumnType = DateTimeColumnType.JAVA_TIME)

        // only the exact shape of the column's own type is read; anything else is left to the database
        assertUnsupported("DATE", "'tomorrow'")
        assertUnsupported("DATE", "'2024-01-31 10:00:00'")
        assertUnsupported("TIMESTAMP", "'2024-01-31T10:00:00'")
        assertUnsupported("TIMESTAMP", "'2024-01-31'")

        // a tag of another type than the column's never matches
        assertUnsupported("DATE", "TIMESTAMP '2024-01-31 10:00:00'")
        assertUnsupported("DATE", "'2024-01-31'", dateTimeColumnType = DateTimeColumnType.JODA_TIME)
    }

    @Test
    fun testBooleanDefaultsReportedAsNumbers() {
        assertLiteral("true", "BOOLEAN", "1")
        assertLiteral("false", "BOOLEAN", "0")
        assertUnsupported("BOOLEAN", "2")
    }

    @Test
    fun testUnsignedDefaults() {
        assertLiteral("7u", "INT UNSIGNED", "7", syntax = MySqlSyntax)
        assertLiteral("7uL", "BIGINT UNSIGNED", "7", syntax = MySqlSyntax)
        val definition = assertNotNull(column(Types.INTEGER, "INT UNSIGNED").toColumnDefinition(MySqlSyntax))
        assertIs<DefaultValue.Unsupported>(definition.defaultValueFor("-1", DateTimeColumnType.KOTLIN_DATETIME, MySqlSyntax))
    }

    @Test
    fun testDefaultsAsReportedByMariaDB() {
        // the current date and time functions are reported under the server's own names
        assertEquals("CURRENT_TIMESTAMP", MariaDbSyntax.normalizeDefault("current_timestamp()"))
        assertEquals("CURRENT_TIMESTAMP", MariaDbSyntax.normalizeDefault("current_timestamp(6)"))
        assertEquals("CURRENT_DATE", MariaDbSyntax.normalizeDefault("curdate()"))

        // a string is reported in quotes, so it is never taken for a function
        assertEquals("'current_timestamp()'", MariaDbSyntax.normalizeDefault("'current_timestamp()'"))
        assertEquals("uuid()", MariaDbSyntax.normalizeDefault("uuid()"))

        val onTimestamp = defaultFor("TIMESTAMP WITH LOCAL TIME ZONE", "current_timestamp(6)", syntax = MariaDbSyntax)
        assertTrue(assertIs<DefaultValue.Expression>(onTimestamp).code.toString().endsWith("CurrentTimestamp"))
        val onDatetime = defaultFor("DATETIME(6)", "current_timestamp(6)", syntax = MariaDbSyntax)
        assertTrue(assertIs<DefaultValue.Expression>(onDatetime).code.toString().endsWith("CurrentDateTime"))
        val onDate = defaultFor("DATE", "curdate()", syntax = MariaDbSyntax)
        assertTrue(assertIs<DefaultValue.Expression>(onDate).code.toString().endsWith("CurrentDate"))

        // a literal on a TIMESTAMP column is not a point in time without the time zone of the server
        assertIs<DefaultValue.Unsupported>(defaultFor("TIMESTAMP WITH LOCAL TIME ZONE", "'2024-01-31 10:00:00'", syntax = MariaDbSyntax))
        assertIs<DefaultValue.Unsupported>(defaultFor("VARCHAR(36)", "uuid()", syntax = MariaDbSyntax))
        assertIs<DefaultValue.Unsupported>(defaultFor("INT", "(1 + 2)", syntax = MariaDbSyntax))
        assertLiteral("\"it's\"", "VARCHAR(50)", "'it''s'", syntax = MariaDbSyntax)
        assertLiteral("\"NULL\"", "VARCHAR(50)", "'NULL'", syntax = MariaDbSyntax)
    }

    @Test
    fun testSequencesAndGeneratedNamesAsReportedByMariaDB() {
        assertEquals(SequenceReference("shop", "order_seq"), MariaDbSyntax.sequenceInDefault("nextval(`shop`.`order_seq`)"))
        assertEquals(SequenceReference("shop", "odd`name"), MariaDbSyntax.sequenceInDefault("nextval(`shop`.`odd``name`)"))
        assertNull(MariaDbSyntax.sequenceInDefault("'nextval(`shop`.`order_seq`)'"))
        assertNull(MariaDbSyntax.sequenceInDefault("uuid()"))

        assertTrue(MariaDbSyntax.isGeneratedConstraintName("PRIMARY", "orders", listOf("id")))

        // an unnamed foreign key is numbered within its table, or carries the name an older server gave it
        assertTrue(MariaDbSyntax.isGeneratedConstraintName("1", "orders", listOf("user_id")))
        assertTrue(MariaDbSyntax.isGeneratedConstraintName("12", "orders", listOf("user_id")))
        assertTrue(MariaDbSyntax.isGeneratedConstraintName("orders_ibfk_2", "orders", listOf("user_id")))

        // an unnamed UNIQUE constraint is named after its first column
        assertTrue(MariaDbSyntax.isGeneratedConstraintName("code", "orders", listOf("code")))
        assertTrue(MariaDbSyntax.isGeneratedConstraintName("code_2", "orders", listOf("code", "region")))
        assertFalse(MariaDbSyntax.isGeneratedConstraintName("fk_orders_user", "orders", listOf("user_id")))
        assertFalse(MariaDbSyntax.isGeneratedConstraintName("uq_orders_code", "orders", listOf("code")))

        assertEquals(listOf("Bar", "it's"), MariaDbSyntax.enumerationValues("enum('Bar','it''s')"))
    }

    @Test
    fun testDefaultsAsStoredBySQLServer() {
        // a default is stored wrapped in parentheses, twice for a number
        assertEquals("42", SqlServerSchemaSupport.normalizeDefault("((42))", isDateColumn = false))
        assertEquals("-7", SqlServerSchemaSupport.normalizeDefault("((-7))", isDateColumn = false))
        assertEquals("12.5", SqlServerSchemaSupport.normalizeDefault("((12.5))", isDateColumn = false))
        assertEquals("5000000000", SqlServerSchemaSupport.normalizeDefault("((5000000000.))", isDateColumn = false))
        assertEquals("'text'", SqlServerSchemaSupport.normalizeDefault("('text')", isDateColumn = false))
        assertEquals("'it''s (here)'", SqlServerSchemaSupport.normalizeDefault("('it''s (here)')", isDateColumn = false))
        assertEquals("'unicode'", SqlServerSchemaSupport.normalizeDefault("(N'unicode')", isDateColumn = false))
        assertEquals("'NULL'", SqlServerSchemaSupport.normalizeDefault("('NULL')", isDateColumn = false))
        assertNull(SqlServerSchemaSupport.normalizeDefault("(NULL)", isDateColumn = false))

        // CURRENT_TIMESTAMP is stored as getdate(), which is also what Exposed writes for CurrentDate on a DATE column
        assertEquals("CURRENT_TIMESTAMP", SqlServerSchemaSupport.normalizeDefault("(getdate())", isDateColumn = false))
        assertEquals("CURRENT_DATE", SqlServerSchemaSupport.normalizeDefault("(getdate())", isDateColumn = true))
        assertEquals("'getdate()'", SqlServerSchemaSupport.normalizeDefault("('getdate()')", isDateColumn = false))

        assertEquals("sysdatetime()", SqlServerSchemaSupport.normalizeDefault("(sysdatetime())", isDateColumn = false))
        assertEquals("(1)+(2)", SqlServerSchemaSupport.normalizeDefault("((1)+(2))", isDateColumn = false))
        assertEquals("NEXT VALUE FOR [dbo].[seq]", SqlServerSchemaSupport.normalizeDefault("(NEXT VALUE FOR [dbo].[seq])", isDateColumn = false))

        assertLiteral("5000000000L", "BIGINT", "5000000000", syntax = SqlServerSyntax)
        assertIs<DefaultValue.Unsupported>(defaultFor("TIMESTAMP", "sysdatetime()", syntax = SqlServerSyntax))
        assertIs<DefaultValue.Unsupported>(defaultFor("INT", "(1)+(2)", syntax = SqlServerSyntax))
    }

    @Test
    fun testSequencesAndGeneratedNamesAsReportedBySQLServer() {
        assertEquals(SequenceReference("dbo", "order_seq"), SqlServerSyntax.sequenceInDefault("NEXT VALUE FOR [dbo].[order_seq]"))
        assertEquals(SequenceReference(null, "order_seq"), SqlServerSyntax.sequenceInDefault("NEXT VALUE FOR [order_seq]"))
        assertEquals(SequenceReference("sales", "odd]name"), SqlServerSyntax.sequenceInDefault("NEXT VALUE FOR [sales].[odd]]name]"))
        assertEquals(SequenceReference("dbo", "order_seq"), SqlServerSyntax.sequenceInDefault("next value for dbo.order_seq"))
        assertNull(SqlServerSyntax.sequenceInDefault("'NEXT VALUE FOR [dbo].[order_seq]'"))
        assertNull(SqlServerSyntax.sequenceInDefault("newid()"))

        assertTrue(SqlServerSyntax.isGeneratedConstraintName("PK__orders__3213E83F4D1B48C9", "orders", listOf("id")))
        assertTrue(SqlServerSyntax.isGeneratedConstraintName("UQ__orders__357D4CF9EDB2FF52", "orders", listOf("code")))
        assertTrue(SqlServerSyntax.isGeneratedConstraintName("FK__orders__user_i__5070F446", "orders", listOf("user_id")))
        assertFalse(SqlServerSyntax.isGeneratedConstraintName("pk_orders_custom", "orders", listOf("id")))
        assertFalse(SqlServerSyntax.isGeneratedConstraintName("FK_orders_users", "orders", listOf("user_id")))
        assertFalse(SqlServerSyntax.isGeneratedConstraintName("UQ__orders__code", "orders", listOf("code")))
    }

    private fun assertLiteral(
        expectedGenerated: String,
        sqlType: String,
        metadataReported: String,
        uuidColumnType: UuidColumnType = UuidColumnType.KOTLIN,
        dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
        syntax: DialectSyntax = H2Syntax,
    ) {
        val default = defaultFor(sqlType, metadataReported, uuidColumnType, dateTimeColumnType, syntax)
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
        syntax: DialectSyntax = H2Syntax,
    ): DefaultValue {
        val column = column(Types.OTHER, sqlType, size = null, scale = null)
        val definition = assertNotNull(
            column.toColumnDefinition(H2Syntax, uuidColumnType, dateTimeColumnType),
            "Expected $sqlType to be mapped"
        )
        return definition.defaultValueFor(metadataReported, dateTimeColumnType, syntax)
    }
}

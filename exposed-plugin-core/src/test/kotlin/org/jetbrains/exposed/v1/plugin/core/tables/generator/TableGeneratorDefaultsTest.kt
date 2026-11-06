package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableGeneratorDefaultsTest : TableGeneratorTestBase() {
    @Test
    fun testLiteralAndCurrentTimeDefaultsAreGenerated() {
        execute(
            """
            CREATE TABLE SETTINGS (
                ID INT NOT NULL PRIMARY KEY,
                RETRIES INT DEFAULT 3 NOT NULL,
                OFFSET_MS BIGINT DEFAULT -500 NOT NULL,
                RATIO DOUBLE PRECISION DEFAULT 0.75 NOT NULL,
                PRICE DECIMAL(10, 2) DEFAULT 12.50 NOT NULL,
                ACTIVE BOOLEAN DEFAULT TRUE NOT NULL,
                LABEL VARCHAR(20) DEFAULT 'it''s' NOT NULL,
                NOTE VARCHAR(10) DEFAULT '' NULL,
                CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                STARTS_ON DATE DEFAULT CURRENT_DATE NOT NULL,
                ENDS_ON DATE DEFAULT DATE '2024-12-31' NULL,
                TOKEN UUID DEFAULT '123e4567-e89b-12d3-a456-426614174000' NOT NULL,
                STATUS ENUM('Bar', 'Baz') DEFAULT 'Baz' NOT NULL,
                PLAIN INT NULL,
                EXPLICIT_NULL INT DEFAULT NULL NULL
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        assertContains(source, "val retries: Column<Int> = integer(\"RETRIES\").default(3)")
        assertContains(source, "val offsetMs: Column<Long> = long(\"OFFSET_MS\").default(-500L)")
        assertContains(source, "val ratio: Column<Double> = double(\"RATIO\").default(0.75)")
        assertContains(source, "val price: Column<BigDecimal> = decimal(\"PRICE\", 10, 2).default(BigDecimal(\"12.50\"))")
        assertContains(source, "val active: Column<Boolean> = bool(\"ACTIVE\").default(true)")
        assertContains(source, "val label: Column<String> = varchar(\"LABEL\", 20).default(\"it's\")")
        assertContains(source, "val note: Column<String?> = varchar(\"NOTE\", 10).default(\"\").nullable()")
        assertContains(source, "val createdAt: Column<LocalDateTime> = datetime(\"CREATED_AT\").defaultExpression(CurrentDateTime)")
        assertContains(source, "val startsOn: Column<LocalDate> = date(\"STARTS_ON\").defaultExpression(CurrentDate)")
        assertContains(source, "val endsOn: Column<LocalDate?> = date(\"ENDS_ON\").default(LocalDate(2024, 12, 31)).nullable()")
        assertContains(source, "val token: Column<Uuid> = uuid(\"TOKEN\").default(Uuid.parse(\"123e4567-e89b-12d3-a456-426614174000\"))")
        assertContains(
            source,
            "customEnumeration(\"STATUS\", \"ENUM('Bar', 'Baz')\", { Status.valueOf(it as String) }, { it.name }).default(Status.Baz)"
        )
        assertContains(source, "val plain: Column<Int?> = integer(\"PLAIN\").nullable()")
        assertContains(source, "val explicitNull: Column<Int?> = integer(\"EXPLICIT_NULL\").nullable()")
        assertContains(source, "import java.math.BigDecimal")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.CurrentDate")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.CurrentDateTime")
        assertFalse(source.contains("databaseGenerated"), "Every default here is expressible: $source")
        assertFalse(messages.any { it.contains("cannot express") }, "No unsupported default expected: $messages")
    }

    @Test
    fun testUnsupportedDefaultsMarkedAsDatabaseGenerated() {
        execute(
            """
            CREATE TABLE TOKENS (
                ID INT NOT NULL PRIMARY KEY,
                TOKEN_VALUE UUID DEFAULT RANDOM_UUID() NOT NULL,
                SEEN_AT TIME DEFAULT CURRENT_TIME NULL,
                RAW VARBINARY(4) DEFAULT X'0102' NOT NULL,
                TAGS INT ARRAY DEFAULT ARRAY[1, 2] NULL
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        assertContains(source, "val tokenValue: Column<Uuid> = uuid(\"TOKEN_VALUE\").databaseGenerated()")
        assertContains(source, "val seenAt: Column<LocalTime?> = time(\"SEEN_AT\").databaseGenerated().nullable()")
        assertContains(source, "val raw: Column<ByteArray> = binary(\"RAW\", 4).databaseGenerated()")
        assertContains(source, "val tags: Column<List<Int>?> = array(\"TAGS\", IntegerColumnType()).databaseGenerated().nullable()")
        assertWarning("Column 'TOKEN_VALUE' of table 'TOKENS' has the default RANDOM_UUID(), which the generator cannot express")
        assertTrue(messages.any { it.contains("has the default CURRENT_TIME,") }, "Expected a warning for CURRENT_TIME: $messages")
        assertTrue(messages.any { it.contains("has the default ARRAY [1, 2],") }, "Expected a warning for the array default: $messages")
    }

    @Test
    fun testMappedColumnsGetNoDefaultButAWarning() {
        execute("CREATE TABLE SETTINGS (CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)")

        val source = generatedSource(
            generate(columnMappings = listOf(ColumnMapping("SETTINGS.CREATED_AT", "timestamp(\"CREATED_AT\")"))).single()
        )

        assertContains(source, "val createdAt = timestamp(\"CREATED_AT\")")
        assertFalse(source.contains("default"), "Defaults are never appended to mapped code: $source")
        assertFalse(source.contains("databaseGenerated"), "A mapped column is not marked generated for its default: $source")
        assertWarning("Column 'CREATED_AT' of table 'SETTINGS' has the default CURRENT_TIMESTAMP, but its code is mapped")
    }

    @Test
    fun testMappedColumnsWithDefaultsAreGenerated() {
        execute("CREATE TABLE SETTINGS (CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)")

        val source = generatedSource(
            generate(
                columnMappings = listOf(
                    ColumnMapping("SETTINGS.CREATED_AT", "timestamp(\"CREATED_AT\").defaultExpression(CurrentDateTime)")
                )
            ).single()
        )

        assertContains(source, "val createdAt = timestamp(\"CREATED_AT\").defaultExpression(CurrentDateTime)")
        // modifier is not reported since the generator does not append its own
    }
}

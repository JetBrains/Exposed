package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.datetime.KotlinLocalDateColumnType
import org.jetbrains.exposed.v1.javatime.JavaLocalDateColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.TableConfig
import org.jetbrains.exposed.v1.plugin.core.tables.TableGenerator
import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalApi::class)
class TableGeneratorTest : TableGeneratorTestBase() {
    @Test
    fun testMissingPackageNameFails() {
        val exception = assertFailsWith<IllegalArgumentException> { generate(tablesPackage = " ") }

        assertEquals("Package name for generated Exposed table definitions must be set", exception.message)
    }

    @Test
    fun testMissingDatabasePropertiesFail() {
        val generator = TableGenerator(
            config = TableConfig(
                tablesPackage = "com.example.tables",
                fileDirectory = outputDirectory,
                databaseUrl = databaseUrl
            ),
            logger = logger,
        )

        val exception = assertFailsWith<IllegalArgumentException> { generator.generate() }

        assertEquals("Database properties (url, user, password) must be provided", exception.message)
    }

    @Test
    fun testSingleFileGeneration() {
        execute(
            "CREATE TABLE ZEBRAS (ID INT NOT NULL)",
            "CREATE TABLE APPLES (ID INT NOT NULL)",
        )

        val generated = generate(fullFileName = "Schema.kt")

        assertEquals(listOf(path("Schema")), generated)
        val source = generatedSource(generated.single())
        assertContains(source, "object Apples : Table(\"APPLES\")")
        assertContains(source, "object Zebras : Table(\"ZEBRAS\")")
        assertContains(messages, "# Exposed Tables Generated 2 table objects in 1 file(s):")
    }

    @Test
    fun testSingleFileNameWithPathSeparatorFails() {
        val exception = assertFailsWith<IllegalArgumentException> { generate(fullFileName = "../Schema.kt") }

        assertContains(exception.message.orEmpty(), "must not contain path separators")
    }

    @Test
    fun testOneFilePerTableSortedByTableName() {
        execute(
            "CREATE TABLE ZEBRAS (ID INT NOT NULL)",
            "CREATE TABLE APPLES (ID INT NOT NULL)",
        )

        val generated = generate()

        assertEquals(listOf(path("Apples"), path("Zebras")), generated)
        assertContains(messages, "# Exposed Tables Generated 2 table objects in 2 file(s):")
        assertContains(messages, "  * ${path("Apples")}")
        assertContains(messages, "  * ${path("Zebras")}")
    }

    @Test
    fun testObjectNameSuffixInSingleFile() {
        execute("CREATE TABLE USERS (ID INT NOT NULL)")

        val generated = generate(fullFileName = "Schema.kt", objectNameSuffix = "Table")

        assertEquals(listOf(path("Schema")), generated)
        assertContains(generatedSource(generated.single()), "object UsersTable : Table(\"USERS\") {")
    }

    @Test
    fun testObjectNameSuffix() {
        execute(
            "CREATE TABLE USERS (ID INT NOT NULL)",
            "CREATE TABLE \"reserved\" (\"object\" INT NOT NULL)",
        )

        val generated = generate(objectNameSuffix = "Table")

        // the suffix applies to object and file names only, never to the table name literal
        assertEquals(listOf(path("ReservedTable"), path("UsersTable")), generated)
        assertContains(generatedSource(generated[0]), "object ReservedTable : Table(\"\\\"reserved\\\"\") {")
        assertContains(generatedSource(generated[1]), "object UsersTable : Table(\"USERS\") {")
    }

    @Test
    fun testJavaUuidColumnType() {
        execute("CREATE TABLE TOKENS (ID UUID NOT NULL PRIMARY KEY, OWNER_ID UUID NULL)")

        val source = generatedSource(generate(uuidColumnType = UuidColumnType.JAVA).single())

        assertContains(source, "val id: Column<UUID> = javaUUID(\"ID\")")
        assertContains(source, "val ownerId: Column<UUID?> = javaUUID(\"OWNER_ID\").nullable()")
        assertContains(source, "import java.util.UUID")
        assertContains(source, "import org.jetbrains.exposed.v1.core.java.javaUUID")
        assertFalse(source.contains("@file:OptIn"), "No opt-in expected for java.util.UUID columns: $source")
        assertFalse(source.contains("kotlin.uuid"), "No kotlin.uuid references expected: $source")
    }

    @Test
    fun testBasicColumnTypesAreMapped() {
        execute(
            """
            CREATE TABLE TYPES_TABLE (
                TINY TINYINT NOT NULL,
                SMALL SMALLINT NOT NULL,
                NORMAL INT NOT NULL,
                BIG BIGINT NOT NULL,
                EXACT DECIMAL(10, 2) NOT NULL,
                WHOLE NUMERIC(5) NOT NULL,
                SINGLE REAL NOT NULL,
                APPROX FLOAT NOT NULL,
                DBL DOUBLE PRECISION NOT NULL,
                FLAG BOOLEAN NOT NULL,
                FIXED CHAR(3) NOT NULL,
                LETTER CHAR NOT NULL,
                SHORT_TEXT VARCHAR(255) NOT NULL,
                LONG_TEXT VARCHAR NOT NULL,
                LARGE_TEXT CLOB NOT NULL,
                RAW VARBINARY(16) NOT NULL,
                RAW_UNBOUNDED VARBINARY NOT NULL,
                LARGE_RAW BLOB NOT NULL,
                UNIQUE_ID UUID NOT NULL
            )
            """.trimIndent()
        )

        val generated = generate()

        assertEquals(listOf(path("TypesTable")), generated)
        val source = generatedSource(generated.single())
        assertContains(source, "object TypesTable : Table(\"TYPES_TABLE\") {")
        assertContains(source, "val tiny: Column<Byte> = byte(\"TINY\")")
        assertContains(source, "val small: Column<Short> = short(\"SMALL\")")
        assertContains(source, "val normal: Column<Int> = integer(\"NORMAL\")")
        assertContains(source, "val big: Column<Long> = long(\"BIG\")")
        assertContains(source, "val exact: Column<BigDecimal> = decimal(\"EXACT\", 10, 2)")
        assertContains(source, "val whole: Column<BigDecimal> = decimal(\"WHOLE\", 5, 0)")
        assertContains(source, "val single: Column<Float> = float(\"SINGLE\")")
        assertContains(source, "val approx: Column<Float> = float(\"APPROX\")")
        assertContains(source, "val dbl: Column<Double> = double(\"DBL\")")
        assertContains(source, "val flag: Column<Boolean> = bool(\"FLAG\")")
        assertContains(source, "val fixed: Column<String> = char(\"FIXED\", 3)")
        assertContains(source, "val letter: Column<Char> = char(\"LETTER\")")
        assertContains(source, "val shortText: Column<String> = varchar(\"SHORT_TEXT\", 255)")
        assertContains(source, "val longText: Column<String> = text(\"LONG_TEXT\")")
        assertContains(source, "val largeText: Column<String> = text(\"LARGE_TEXT\")")
        assertContains(source, "val raw: Column<ByteArray> = binary(\"RAW\", 16)")
        assertContains(source, "val rawUnbounded: Column<ByteArray> = binary(\"RAW_UNBOUNDED\")")
        assertContains(source, "val largeRaw: Column<ExposedBlob> = blob(\"LARGE_RAW\")")
        assertContains(source, "val uniqueId: Column<Uuid> = uuid(\"UNIQUE_ID\")")

        assertContains(source, "package com.example.tables")
        assertContains(source, "@file:OptIn(ExperimentalUuidApi::class)")
        assertContains(source, "import java.math.BigDecimal")
        assertContains(source, "import kotlin.uuid.Uuid")
        assertContains(source, "import org.jetbrains.exposed.v1.core.Column")
        assertContains(source, "import org.jetbrains.exposed.v1.core.Table")
        assertContains(source, "import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob")
        assertFalse(source.contains("public "), "Generated code should not contain explicit public modifiers: $source")
        assertFalse(source.contains("import kotlin.String"), "Kotlin default imports should be implicit: $source")
        assertFalse(Regex("import [a-z]+ ").containsMatchIn(source), "Table member functions must not be imported: $source")
    }

    @Test
    fun testNullableAutoIncrementAndPrimaryKeyColumns() {
        execute(
            """
            CREATE TABLE USERS (
                ID INT AUTO_INCREMENT PRIMARY KEY,
                EMAIL VARCHAR(320) NOT NULL,
                AGE INT NULL
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        assertContains(source, "val id: Column<Int> = integer(\"ID\").autoIncrement()")
        assertContains(source, "val email: Column<String> = varchar(\"EMAIL\", 320)")
        assertContains(source, "val age: Column<Int?> = integer(\"AGE\").nullable()")
        assertContains(source, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        assertFalse(source.contains("import PrimaryKey"), "PrimaryKey must resolve through Table: $source")
        assertFalse(source.contains("@file:OptIn"), "No opt-in expected without Uuid columns: $source")
        assertFalse(source.contains("init {"), "No initializer block expected without composite indices: $source")
    }

    @Test
    fun testSequenceBackedColumnsUseNamedAutoIncrement() {
        execute(
            "CREATE SEQUENCE ORDERS_SEQ",
            "CREATE SEQUENCE \"items_seq\"",
            "CREATE SEQUENCE CODES_SEQ",
            "CREATE SEQUENCE SERIAL_SEQ START WITH 100 INCREMENT BY 5 MINVALUE 100 MAXVALUE 100000 CYCLE CACHE 10",
            "CREATE SEQUENCE TICKET_SEQ AS INTEGER",
            """
            CREATE TABLE ORDERS (
                ID BIGINT DEFAULT NEXT VALUE FOR ORDERS_SEQ NOT NULL PRIMARY KEY,
                ITEM_NO INT DEFAULT NEXT VALUE FOR "items_seq" NOT NULL,
                CODE SMALLINT DEFAULT NEXT VALUE FOR CODES_SEQ NOT NULL,
                SERIAL_NO BIGINT DEFAULT NEXT VALUE FOR SERIAL_SEQ NOT NULL,
                TICKET_NO INT DEFAULT NEXT VALUE FOR TICKET_SEQ NOT NULL,
                NOTE VARCHAR(10) NULL
            )
            """.trimIndent(),
        )

        val source = generatedSource(generate().single())

        // a sequence default is not an identity column, so it is generated as a named auto-increment
        assertContains(source, "val id: Column<Long> = long(\"ID\").autoIncrement(\"ORDERS_SEQ\")")
        // a quoted lower-case sequence name is pre-quoted like any other identifier
        assertContains(source, "val itemNo: Column<Int> = integer(\"ITEM_NO\").autoIncrement(\"\\\"items_seq\\\"\")")
        // sequence-backed auto-increment for other integer types is not supported
        assertContains(source, "val code: Column<Short> = short(\"CODE\")")
        // a sequence whose parameters autoIncrement(name) would not reproduce is passed as a Sequence with exactly those parameters
        assertContains(
            source,
            "val serialNo: Column<Long> = long(\"SERIAL_NO\").autoIncrement(Sequence(\"SERIAL_SEQ\", startWith = 100, incrementBy = 5, " +
                "minValue = 100, maxValue = 100000, cycle = true, cache = 10))"
        )
        assertContains(source, "val ticketNo: Column<Int> = integer(\"TICKET_NO\").autoIncrement(Sequence(\"TICKET_SEQ\", maxValue = 2147483647))")
        assertContains(source, "import org.jetbrains.exposed.v1.core.Sequence")
        assertContains(source, "val note: Column<String?> = varchar(\"NOTE\", 10).nullable()")
        val expectedWarning = "Warning: Column 'CODE' of table 'ORDERS' takes its default value from sequence 'CODES_SEQ'"
        assertTrue(messages.any { it.startsWith(expectedWarning) }, "Expected a warning: $messages")
        assertFalse(messages.any { it.contains("is not used as the default value of any generated column") }, "Unexpected warning: $messages")
    }

    @Test
    fun testGeneratedColumnsAreMarkedDatabaseGenerated() {
        execute(
            """
            CREATE TABLE LINE_ITEMS (
                ID INT NOT NULL PRIMARY KEY,
                PRICE DECIMAL(10, 2) NOT NULL,
                QTY INT NOT NULL,
                TOTAL DECIMAL(12, 2) GENERATED ALWAYS AS (PRICE * QTY),
                ACTIVE BOOLEAN DEFAULT TRUE NOT NULL,
                CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                SEQ_NO BIGINT GENERATED BY DEFAULT AS IDENTITY
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        // a computed column is marked as database-generated and documents its expression
        assertContains(source, "* Generated by the database as: \"PRICE\" * \"QTY\" */")
        assertContains(source, "val total: Column<BigDecimal?> = decimal(\"TOTAL\", 12, 2).databaseGenerated().nullable()")
        assertWarning("Column 'TOTAL' of table 'LINE_ITEMS' is generated by the database as (\"PRICE\" * \"QTY\")")

        // columns with a plain default (literal or expression) and identity columns are not generated columns
        assertContains(source, "val active: Column<Boolean> = bool(\"ACTIVE\")")
        assertContains(source, "val seqNo: Column<Long> = long(\"SEQ_NO\").autoIncrement()")
        assertEquals(
            1,
            source.windowed("databaseGenerated".length).count { it == "databaseGenerated" },
            "Only TOTAL is generated: $source"
        )
    }

    @Test
    fun testTemporalColumnsDefaultToKotlinDatetime() {
        execute(
            """
            CREATE TABLE EVENTS (
                ID INT NOT NULL PRIMARY KEY,
                EVENT_DAY DATE NOT NULL,
                AT_TIME TIME NOT NULL,
                CREATED_AT TIMESTAMP NOT NULL,
                UPDATED_AT TIMESTAMP(9) NULL,
                PUBLISHED_AT TIMESTAMP WITH TIME ZONE NOT NULL
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        assertContains(source, "val eventDay: Column<LocalDate> = date(\"EVENT_DAY\")")
        assertContains(source, "val atTime: Column<LocalTime> = time(\"AT_TIME\")")
        assertContains(source, "val createdAt: Column<LocalDateTime> = datetime(\"CREATED_AT\")")
        assertContains(source, "val updatedAt: Column<LocalDateTime?> = datetime(\"UPDATED_AT\").nullable()")
        assertContains(source, "val publishedAt: Column<OffsetDateTime> = timestampWithTimeZone(\"PUBLISHED_AT\")")
        assertContains(source, "import kotlinx.datetime.LocalDate")
        assertContains(source, "import kotlinx.datetime.LocalDateTime")
        assertContains(source, "import kotlinx.datetime.LocalTime")
        assertContains(source, "import java.time.OffsetDateTime")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.date")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.datetime")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.time")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone")
        assertFalse(source.contains("ExperimentalTime"), "datetime() needs no kotlin.time opt-in: $source")
        assertLogged("No Exposed date-time module was found on the classpath. date-time columns are generated for exposed-kotlin-datetime")
    }

    @Test
    fun testTemporalColumnsUseConfiguredModule() {
        execute("CREATE TABLE EVENTS (EVENT_DAY DATE NOT NULL, CREATED_AT TIMESTAMP NOT NULL, PUBLISHED_AT TIMESTAMP WITH TIME ZONE NULL)")

        val javaSource = generatedSource(generate(dateTimeColumnType = DateTimeColumnType.JAVA_TIME).single())
        assertContains(javaSource, "val eventDay: Column<LocalDate> = date(\"EVENT_DAY\")")
        assertContains(javaSource, "val createdAt: Column<LocalDateTime> = datetime(\"CREATED_AT\")")
        assertContains(javaSource, "val publishedAt: Column<OffsetDateTime?> = timestampWithTimeZone(\"PUBLISHED_AT\").nullable()")
        assertContains(javaSource, "import java.time.LocalDate")
        assertContains(javaSource, "import org.jetbrains.exposed.v1.javatime.date")
        assertFalse(javaSource.contains("kotlinx.datetime"), "No kotlinx.datetime references expected: $javaSource")
        assertFalse(messages.any { it.contains("date/time module") }, "No detection message expected for an explicit choice: $messages")
        // an explicitly chosen module that is not on the classpath is reported like any other module the output depends on
        assertWarning("The generated definitions use column functions from exposed-java-time, which was not found on the classpath")

        clearGeneratedFiles()
        val jodaSource = generatedSource(generate(dateTimeColumnType = DateTimeColumnType.JODA_TIME).single())
        assertContains(jodaSource, "val eventDay: Column<DateTime> = date(\"EVENT_DAY\")")
        assertContains(jodaSource, "val createdAt: Column<DateTime> = datetime(\"CREATED_AT\")")
        assertContains(jodaSource, "val publishedAt: Column<DateTime?> = timestampWithTimeZone(\"PUBLISHED_AT\").nullable()")
        assertContains(jodaSource, "import org.joda.time.DateTime")
        assertContains(jodaSource, "import org.jetbrains.exposed.v1.jodatime.date")
    }

    @Test
    fun testDateTimeModuleIsDetectedOnTheClasspath() {
        execute("CREATE TABLE EVENTS (EVENT_DAY DATE NOT NULL)")
        val javaTimeModule = JavaLocalDateColumnType::class.java.protectionDomain.codeSource.location
        val kotlinDatetimeModule = KotlinLocalDateColumnType::class.java.protectionDomain.codeSource.location

        // a single module on the classpath is used and not reported as missing
        val javaSource = generatedSource(generate(classpathUrls = listOf(javaTimeModule)).single())
        assertContains(javaSource, "import org.jetbrains.exposed.v1.javatime.date")
        assertContains(messages, "Detected exposed-java-time on the classpath. date-time columns are generated for it")
        assertFalse(
            messages.any { it.contains("not found on the classpath") },
            "A detected module must not be reported as missing: $messages"
        )

        // several modules: the first in declaration order (Kotlin) wins, with a warning
        messages.clear()
        clearGeneratedFiles()
        val bothSource = generatedSource(generate(classpathUrls = listOf(javaTimeModule, kotlinDatetimeModule)).single())
        assertContains(bothSource, "import org.jetbrains.exposed.v1.datetime.date")
        assertWarning("Several Exposed date-time modules were found on the classpath")

        // an explicit choice is never overridden by detection
        messages.clear()
        clearGeneratedFiles()
        val explicitSource = generatedSource(
            generate(dateTimeColumnType = DateTimeColumnType.JODA_TIME, classpathUrls = listOf(javaTimeModule)).single()
        )
        assertContains(explicitSource, "import org.jetbrains.exposed.v1.jodatime.date")
        assertFalse(
            messages.any { it.contains("date-time module") },
            "No detection message expected for an explicit choice: $messages"
        )
    }

    @Test
    fun testUnreferencedSequencesAreReported() {
        execute(
            "CREATE SEQUENCE USED_SEQ",
            "CREATE SEQUENCE ORPHAN_SEQ",
            "CREATE TABLE ORDERS (ID BIGINT DEFAULT NEXT VALUE FOR USED_SEQ NOT NULL PRIMARY KEY)",
        )

        generate()

        assertWarning("Sequence 'ORPHAN_SEQ' is not used as the default value of any generated column")
        assertFalse(messages.any { it.startsWith("Warning: Sequence 'USED_SEQ'") }, "Unexpected warning: $messages")
    }

    @Test
    fun testUnsupportedColumnTypesAreSkippedWithWarning() {
        execute(
            """
            CREATE TABLE EVENTS (
                ID INT NOT NULL,
                CREATED_AT TIME WITH TIME ZONE NOT NULL,
                ELAPSED INTERVAL DAY
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        assertContains(source, "val id: Column<Int> = integer(\"ID\")")
        assertFalse(source.contains("createdAt"), "Unsupported column should not be generated: $source")
        assertFalse(source.contains("elapsed"), "Unsupported column should not be generated: $source")
        assertContains(source, "TODO: Column 'CREATED_AT' of table 'EVENTS' has unsupported type")
        assertContains(source, "TODO: Column 'ELAPSED' of table 'EVENTS' has unsupported type")
        assertWarning("Column 'CREATED_AT' of table 'EVENTS'")
        assertWarning("Column 'ELAPSED' of table 'EVENTS'")
    }

    @Test
    fun testKeywordAndQuotedIdentifiersAreEscapedAndQuoted() {
        execute(
            """
            CREATE TABLE "reserved" (
                "object" INT NOT NULL,
                "fun" VARCHAR(10) NULL,
                "CamelCase" INT NULL,
                PLAIN INT NULL
            )
            """.trimIndent(),
            "CREATE INDEX \"reserved_plain\" ON \"reserved\" (PLAIN)",
        )

        val source = generatedSource(generate().single())

        // identifiers not in H2's upper-case folding were created quoted and must be generated pre-quoted
        assertContains(source, "object Reserved : Table(\"\\\"reserved\\\"\") {")
        assertContains(source, "val `fun`: Column<String?> = varchar(\"\\\"fun\\\"\", 10).nullable()")
        // SQL keywords are the exception: Exposed always quotes them, preserving their case, so they stay bare
        assertContains(source, "val `object`: Column<Int> = integer(\"object\")")
        assertContains(source, "val camelCase: Column<Int?> = integer(\"\\\"CamelCase\\\"\").nullable()")
        // identifiers already in H2's default case stay unquoted
        assertContains(source, "val plain: Column<Int?> = integer(\"PLAIN\").index(\"\\\"reserved_plain\\\"\").nullable()")
    }
}

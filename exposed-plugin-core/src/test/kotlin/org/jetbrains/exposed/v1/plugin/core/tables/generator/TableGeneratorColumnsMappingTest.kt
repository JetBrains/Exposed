package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TableGeneratorColumnsMappingTest : TableGeneratorTestBase() {
    @Test
    fun testMappedCodeReplacesUnsupportedAndLossyColumns() {
        execute(
            """
            CREATE TABLE EVENTS (
                ID INT NOT NULL PRIMARY KEY,
                CREATED_AT TIMESTAMP NOT NULL,
                SPAN INTERVAL DAY NULL,
                PAYLOAD JSON NOT NULL,
                CODE VARCHAR(16) NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX EVENTS_CODE ON EVENTS (CODE)",
        )

        val source = generatedSource(
            generate(
                columnMappings = listOf(
                    // a lossy mapping: the import of a date/time function the generator knows is inferred
                    ColumnMapping("EVENTS.CREATED_AT", "timestamp(\"CREATED_AT\")"),
                    // an unsupported type with an explicit import: the modifier is still appended
                    ColumnMapping(
                        "events.span",
                        "registerColumn(\"SPAN\", DayIntervalColumnType())",
                        listOf("com.example.db.DayIntervalColumnType")
                    ),
                    // a typed JSON column with explicit imports
                    ColumnMapping(
                        "EVENTS.PAYLOAD",
                        "json<Payload>(\"PAYLOAD\", Json.Default)",
                        listOf(
                            "org.jetbrains.exposed.v1.json.json",
                            "kotlinx.serialization.json.Json",
                            "com.example.Payload"
                        ),
                    ),
                    // a core Table member needs no import
                    ColumnMapping(
                        "EVENTS.CODE",
                        "enumerationByName<EventCode>(\"CODE\", 16)",
                        listOf("com.example.EventCode")
                    ),
                )
            ).single()
        )

        assertContains(source, "val id: Column<Int> = integer(\"ID\")")
        assertContains(source, "val createdAt = timestamp(\"CREATED_AT\")")
        assertContains(source, "val span = registerColumn(\"SPAN\", DayIntervalColumnType()).nullable()")
        assertContains(source, "val payload = json<Payload>(\"PAYLOAD\", Json.Default)")
        assertContains(source, "val code = enumerationByName<EventCode>(\"CODE\", 16).index()")
        assertContains(source, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        assertContains(source, "import com.example.EventCode")
        assertContains(source, "import com.example.Payload")
        assertContains(source, "import com.example.db.DayIntervalColumnType")
        assertContains(source, "import kotlinx.serialization.json.Json")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.timestamp")
        assertContains(source, "import org.jetbrains.exposed.v1.json.json")
        // the mapping is explained on the property and in the log, and the column is not reported as unsupported
        assertContains(
            source,
            "* Mapped by the columnMappings configuration (key 'events.span') in place of the database type 'INTERVAL DAY'"
        )
        assertLogged("Column 'SPAN' of table 'EVENTS': Mapped by the columnMappings configuration (key 'events.span')")
        assertFalse(source.contains("TODO"), "A mapped column must not be reported as unsupported: $source")
        assertFalse(source.contains("INFERRED_BY_KOTLIN"), "The inferred-type placeholder must be removed: $source")
        assertFalse(messages.any { it.contains("unsupported type") }, "No unsupported-type warning expected: $messages")
    }

    @Test
    fun testInferredImportFollowsTheDateTimeModule() {
        execute("CREATE TABLE EVENTS (CREATED_AT TIMESTAMP NOT NULL)")

        val javaSource = generatedSource(
            generate(
                dateTimeColumnType = DateTimeColumnType.JAVA_TIME,
                columnMappings = listOf(ColumnMapping("EVENTS.CREATED_AT", "timestamp(\"CREATED_AT\")")),
            ).single()
        )

        assertContains(javaSource, "import org.jetbrains.exposed.v1.javatime.timestamp")
        assertFalse(
            javaSource.contains("org.jetbrains.exposed.v1.datetime"),
            "The inferred import must follow the configured module: $javaSource"
        )
    }

    @Test
    fun testMappedColumnsKeepDatabaseDerivedModifiersAndWarnAboutModifiersInTheCode() {
        execute(
            "CREATE SEQUENCE EVENTS_SEQ",
            """
            CREATE TABLE EVENTS (
                ID INT AUTO_INCREMENT PRIMARY KEY,
                SEQ_NO BIGINT DEFAULT NEXT VALUE FOR EVENTS_SEQ NOT NULL,
                TOTAL DECIMAL(12, 2) GENERATED ALWAYS AS (ID * 2),
                NOTE VARCHAR(10) NULL
            )
            """.trimIndent(),
        )

        val source = generatedSource(
            generate(
                columnMappings = listOf(
                    ColumnMapping("EVENTS.ID", "uinteger(\"ID\")"),
                    ColumnMapping("EVENTS.SEQ_NO", "ulong(\"SEQ_NO\")"),
                    ColumnMapping("EVENTS.TOTAL", "double(\"TOTAL\")"),
                    ColumnMapping("EVENTS.NOTE", "text(\"NOTE\").nullable()"),
                )
            ).single()
        )

        // identity, generation, and nullability are always appended to the mapped code
        assertContains(source, "val id = uinteger(\"ID\").autoIncrement()")
        assertContains(source, "val total = double(\"TOTAL\").databaseGenerated().nullable()")
        // a sequence default cannot be reflected on mapped code, so it is reported instead
        assertContains(source, "val seqNo = ulong(\"SEQ_NO\")")
        assertWarning("Column 'SEQ_NO' of table 'EVENTS' takes its default value from sequence 'EVENTS_SEQ', but its code is mapped")
        // a modifier in the mapped code is pasted as-is and reported, since the generator appends its own
        assertContains(source, "val note = text(\"NOTE\").nullable().nullable()")
        assertWarning("The mapping of column 'NOTE' of table 'EVENTS' contains a column modifier")
    }

    @Test
    fun testMappingKeysMatchFoldedQuotedAndSchemaQualifiedNames() {
        execute(
            "CREATE SCHEMA SALES",
            "CREATE TABLE USERS (ID INT NOT NULL)",
            "CREATE TABLE \"accounts\" (\"id\" INT NOT NULL)",
            "CREATE TABLE SALES.ORDERS (ID INT NOT NULL)",
        )

        val generated = generate(
            schemas = listOf("PUBLIC", "SALES"),
            columnMappings = listOf(
                // lower case is folded to the database's default case
                ColumnMapping("users.id", "long(\"ID\")"),
                // a quoted part matches a name that is not in the default case
                ColumnMapping("\"accounts\".\"id\"", "short(\"\\\"id\\\"\")"),
                // a table outside the current schema needs the schema part
                ColumnMapping("sales.orders.id", "long(\"ID\")"),
            ),
        )

        assertEquals(listOf(path("Accounts"), path("Users"), path("SalesOrders")), generated)
        assertContains(generatedSource(generated[0]), "val id = short(\"\\\"id\\\"\")")
        assertContains(generatedSource(generated[1]), "val id = long(\"ID\")")
        assertContains(generatedSource(generated[2]), "val id = long(\"ID\")")
    }

    @Test
    fun testMappingForAnUnknownTableFailsBeforeAnyColumnIsRead() {
        execute("CREATE TABLE USERS (ID INT NOT NULL, PAYLOAD INTERVAL DAY NULL)")

        val exception = assertFailsWith<IllegalStateException> {
            generate(columnMappings = listOf(ColumnMapping("ACCOUNTS.ID", "long(\"ID\")")))
        }

        assertContains(exception.message.orEmpty(), "a column mapping names a table that was not found")
        assertContains(exception.message.orEmpty(), "'ACCOUNTS.ID'")
        assertFalse(File(outputDirectory, path("Users")).exists(), "Nothing may be written when a mapping is unmatched")
        assertFalse(
            messages.any { it.contains("unsupported type") },
            "Columns must not be read when a mapping names an unknown table: $messages"
        )
    }

    @Test
    fun testMappingForAnUnknownColumnFailsWithoutWritingAnything() {
        execute("CREATE TABLE USERS (ID INT NOT NULL)")

        val exception = assertFailsWith<IllegalStateException> {
            generate(columnMappings = listOf(ColumnMapping("USERS.EMAIL", "varchar(\"EMAIL\", 320)")))
        }

        assertContains(exception.message.orEmpty(), "a column mapping names a column that was not found")
        assertContains(exception.message.orEmpty(), "'USERS.EMAIL'")
        assertFalse(File(outputDirectory, path("Users")).exists(), "Nothing may be written when a mapping is unmatched")
    }

    @Test
    fun testMalformedKeyAndConflictingImportsFail() {
        execute("CREATE TABLE USERS (ID INT NOT NULL, NAME VARCHAR(10) NOT NULL)")

        val malformed = assertFailsWith<IllegalArgumentException> {
            generate(columnMappings = listOf(ColumnMapping("ID", "long(\"ID\")")))
        }
        assertContains(malformed.message.orEmpty(), "must be of the form TABLE.COLUMN or SCHEMA.TABLE.COLUMN")

        val conflicting = assertFailsWith<IllegalStateException> {
            generate(
                columnMappings = listOf(
                    ColumnMapping("USERS.ID", "registerColumn(\"ID\", Id())", listOf("com.example.a.Id")),
                    ColumnMapping("USERS.NAME", "registerColumn(\"NAME\", Id())", listOf("com.example.b.Id")),
                )
            )
        }
        assertContains(
            conflicting.message.orEmpty(),
            "import the same name from different packages: com.example.a.Id, com.example.b.Id"
        )
        assertFalse(File(outputDirectory, path("Users")).exists(), "Nothing may be written when imports conflict")
    }
}

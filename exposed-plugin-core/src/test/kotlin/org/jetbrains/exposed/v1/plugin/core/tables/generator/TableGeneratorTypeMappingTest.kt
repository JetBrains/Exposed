package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.json.JsonColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TableGeneratorTypeMappingTest : TableGeneratorTestBase() {
    @Test
    fun testArrayColumnsUseExplicitElementColumnTypes() {
        execute(
            """
            CREATE TABLE TAGGED (
                ID INT NOT NULL PRIMARY KEY,
                IDS INT ARRAY[10] NOT NULL,
                TAGS VARCHAR(32) ARRAY NOT NULL,
                NOTES VARCHAR ARRAY NULL,
                PRICES DECIMAL(10, 2) ARRAY NOT NULL,
                DAYS DATE ARRAY NOT NULL,
                KEYS UUID ARRAY NOT NULL,
                PAYLOADS JSON ARRAY NULL
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        assertContains(source, "val ids: Column<List<Int>> = array(\"IDS\", IntegerColumnType(), 10)")
        assertContains(source, "val tags: Column<List<String>> = array(\"TAGS\", VarCharColumnType(32))")
        assertContains(source, "val notes: Column<List<String>?> = array(\"NOTES\", TextColumnType()).nullable()")
        assertContains(source, "val prices: Column<List<BigDecimal>> = array(\"PRICES\", DecimalColumnType(10, 2))")
        assertContains(source, "val days: Column<List<LocalDate>> = array(\"DAYS\", KotlinLocalDateColumnType())")
        assertContains(source, "val keys: Column<List<Uuid>> = array(\"KEYS\", UuidColumnType())")
        assertContains(source, "import org.jetbrains.exposed.v1.core.DecimalColumnType")
        assertContains(source, "import org.jetbrains.exposed.v1.core.IntegerColumnType")
        assertContains(source, "import org.jetbrains.exposed.v1.core.TextColumnType")
        assertContains(source, "import org.jetbrains.exposed.v1.core.UuidColumnType")
        assertContains(source, "import org.jetbrains.exposed.v1.core.VarCharColumnType")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.KotlinLocalDateColumnType")
        assertContains(source, "import kotlinx.datetime.LocalDate")
        // TODO confirm that the element's opt-in carries over to the array
        assertContains(source, "@file:OptIn(ExperimentalUuidApi::class)")
        assertFalse(source.contains("import kotlin.collections.List"), "Kotlin default imports should be implicit: $source")
        assertFalse(source.contains("array<"), "The reified array form must not be generated: $source")
        // an array of an unsupported element type is skipped like any unsupported column
        assertFalse(source.contains("payloads"), "Unsupported element type should not be generated: $source")
        assertContains(source, "TODO: Column 'PAYLOADS' of table 'TAGGED' has unsupported type")
    }

    @Test
    fun testEnumerationColumnsGenerateNestedEnumClasses() {
        execute(
            """
            CREATE TABLE ORDERS (
                ID INT NOT NULL PRIMARY KEY,
                STATUS ENUM('Bar', 'Baz') NOT NULL,
                MOOD ENUM('a b', 'c''d', 'in') NULL,
                BROKEN ENUM('x.y') NULL
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        assertContains(
            source,
            "val status: Column<Status> = customEnumeration(\"STATUS\", \"ENUM('Bar', 'Baz')\", { Status.valueOf(it as String) }, { it.name })"
        )
        assertContains(
            source,
            "val mood: Column<Mood?> = customEnumeration(\"MOOD\", \"ENUM('a b', 'c''d', 'in')\", { Mood.valueOf(it as String) }, { it.name }).nullable()"
        )
        assertContains(source, "internal enum class Status { Bar, Baz, }")
        assertContains(source, "internal enum class Mood { `a b`, `c'd`, `in`, }")
        assertContains(source, "* Generated for the customEnumeration property [status] from the column's definition ENUM('Bar', 'Baz')")
        assertContains(source, "* Generated as customEnumeration() with the nested enum class Status")
        assertLogged("Column 'STATUS' of table 'ORDERS': Generated as customEnumeration() with the nested enum class Status")
        assertFalse(
            source.substringBefore("object Orders").contains("import org.jetbrains.exposed.v1.core.vendors"),
            "Nothing beyond core needs importing for enumerations: ${source.substringBefore("object Orders")}"
        )
        // a value that cannot be a JVM identifier makes the column unsupported
        assertFalse(source.contains("broken"), "Unsupported enumeration should not be generated: $source")
        assertContains(source, "TODO: Column 'BROKEN' of table 'ORDERS' has unsupported type")
    }

    @Test
    fun testJsonColumnsDefaultToStringAndReportTheMapping() {
        execute(
            """
            CREATE TABLE DOCUMENTS (
                ID INT NOT NULL PRIMARY KEY,
                BODY JSON NOT NULL,
                EXTRA JSON NULL
            )
            """.trimIndent()
        )

        // without exposed-json on the classpath, the generated code is still produced, with a warning
        val source = generatedSource(generate().single())

        assertContains(source, "val body: Column<String> = json<String>(\"BODY\", { it }, { it })")
        assertContains(source, "val extra: Column<String?> = json<String>(\"EXTRA\", { it }, { it }).nullable()")
        assertContains(source, "import org.jetbrains.exposed.v1.json.json")
        assertFalse(source.contains("kotlinx.serialization"), "Identity serializers must not pull in a serialization framework: $source")
        assertContains(source, "* Generated as json<String>() with identity serializers")
        assertContains(source, "a column created with jsonb() identically, so it is always generated as json() unless configured otherwise")
        assertLogged("Column 'BODY' of table 'DOCUMENTS': Generated as json<String>() with identity serializers")
        assertWarning("The generated definitions use column functions from exposed-json, which was not found on the classpath")

        // with exposed-json on the classpath, no warning is logged
        messages.clear()
        clearGeneratedFiles()
        val jsonModule = JsonColumnType::class.java.protectionDomain.codeSource.location
        generate(classpathUrls = listOf(jsonModule))
        assertFalse(messages.any { it.contains("exposed-json") && it.contains("not found") }, "No warning expected: $messages")
    }
}

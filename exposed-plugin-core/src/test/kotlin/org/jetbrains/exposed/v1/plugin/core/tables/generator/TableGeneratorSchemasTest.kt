package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableGeneratorSchemasTest : TableGeneratorTestBase() {
    @Test
    fun testOnlyTheCurrentSchemaIsSearchedByDefault() {
        execute(
            "CREATE SCHEMA SALES",
            "CREATE TABLE USERS (ID INT NOT NULL)",
            "CREATE TABLE SALES.ORDERS (ID INT NOT NULL)",
        )

        val generated = generate()

        assertEquals(listOf(path("Users")), generated)
        assertContains(generatedSource(generated.single()), "object Users : Table(\"USERS\") {")
    }

    @Test
    fun testSchemasAreMatchedByGlobAndPrefixedOutsideTheCurrentSchema() {
        execute(
            "CREATE SCHEMA SALES",
            "CREATE SCHEMA SALES_ARCHIVE",
            "CREATE SCHEMA SALESX",
            "CREATE SCHEMA \"lc_sales\"",
            "CREATE SCHEMA \"lower\"",
            "CREATE TABLE USERS (ID INT NOT NULL)",
            "CREATE TABLE SALES.ORDERS (ID INT NOT NULL, USER_ID INT NOT NULL)",
            "CREATE TABLE SALES_ARCHIVE.ORDERS (ID INT NOT NULL)",
            "CREATE TABLE SALESX.ORDERS (ID INT NOT NULL)",
            "CREATE TABLE \"lc_sales\".ITEMS (ID INT NOT NULL)",
            "CREATE TABLE \"lower\".ITEMS (ID INT NOT NULL)",
        )

        val literal = generate(schemas = listOf("sales_archive"))
        assertEquals(listOf(path("SalesArchiveOrders")), literal)
        assertContains(generatedSource(literal.single()), "object SalesArchiveOrders : Table(\"SALES_ARCHIVE.ORDERS\") {")

        // a glob matches several schemas; the current schema is not included unless listed
        clearGeneratedFiles()
        val glob = generate(schemas = listOf("SALES*"))
        assertEquals(listOf(path("SalesOrders"), path("SalesArchiveOrders"), path("SalesxOrders")), glob)
        assertContains(generatedSource(glob[0]), "object SalesOrders : Table(\"SALES.ORDERS\") {")
        assertContains(generatedSource(glob[0]), "val userId: Column<Int> = integer(\"USER_ID\")")

        // '?' matches a single character
        clearGeneratedFiles()
        assertEquals(listOf(path("SalesxOrders")), generate(schemas = listOf("SALES?")))

        // listing the current schema keeps its tables unprefixed
        clearGeneratedFiles()
        val explicit = generate(schemas = listOf("PUBLIC", "\"lc_sales\"", "\"lower\""))
        assertEquals(listOf(path("Users"), path("LcSalesItems"), path("LowerItems")), explicit)
        assertContains(generatedSource(explicit[0]), "object Users : Table(\"USERS\") {")
        assertContains(generatedSource(explicit[1]), "object LcSalesItems : Table(\"\\\"lc_sales\\\".ITEMS\") {")
        // a schema named after an SQL keyword stays bare, like keyword columns
        assertContains(generatedSource(explicit[2]), "object LowerItems : Table(\"lower.ITEMS\") {")
    }

    @Test
    fun testAllSchemasExcludeSystemSchemas() {
        execute(
            "CREATE SCHEMA SALES",
            "CREATE TABLE USERS (ID INT NOT NULL)",
            "CREATE TABLE SALES.ORDERS (ID INT NOT NULL)",
        )

        val generated = generate(schemas = listOf("*"))

        // INFORMATION_SCHEMA on H2 for example must never be generated
        assertEquals(listOf(path("Users"), path("SalesOrders")), generated)
        assertContains(messages, "# Exposed Tables Generated 2 table objects in 2 file(s):")
    }

    @Test
    fun testUnmatchedSchemaPatternIsIgnoredWithWarning() {
        execute("CREATE TABLE USERS (ID INT NOT NULL)")

        val generated = generate(schemas = listOf("MISSING*", "PUBLIC"))

        assertEquals(listOf(path("Users")), generated)
        assertContains(messages, "Warning: No schema matches 'MISSING*'; it was ignored")
    }

    @Test
    fun testObjectNameSuffixFollowsSchemaPrefix() {
        execute(
            "CREATE SCHEMA SALES",
            "CREATE TABLE SALES.ORDERS (ID INT NOT NULL)",
        )

        val generated = generate(schemas = listOf("SALES"), objectNameSuffix = "Table")

        assertEquals(listOf(path("SalesOrdersTable")), generated)
        assertContains(generatedSource(generated.single()), "object SalesOrdersTable : Table(\"SALES.ORDERS\") {")
    }

    @Test
    fun testEmptySchemaGeneratesNothing() {
        val generated = generate()

        assertTrue(generated.isEmpty())
        assertContains(messages, "# Exposed Tables Generated 0 table objects in 0 file(s):")
    }
}

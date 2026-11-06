package org.jetbrains.exposed.v1.plugin.core.tables.collision

import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TableGeneratorCollisionTest : TableGeneratorTestBase() {
    @Test
    fun testTablesWithTheSameObjectNameFailBeforeAnyColumnIsRead() {
        execute(
            "CREATE TABLE USERS (ID INT NOT NULL)",
            "CREATE TABLE \"users\" (ELAPSED INTERVAL DAY)",
            "CREATE TABLE CITIES (ID INT NOT NULL)",
        )

        val exception = assertFailsWith<IllegalStateException> { generate() }

        val message = exception.message.orEmpty()
        assertContains(message, "the generated code would not compile")
        assertContains(message, "would all be generated as object 'Users'")
        assertFalse(File(outputDirectory, path("Cities")).exists(), "Nothing should be written when planning fails")
        // the unsupported INTERVAL column would have been reported if the columns been read & mapped
        assertFalse(messages.any { it.contains("unsupported type") }, "Columns must not be read when planning fails: $messages")
    }

    @Test
    fun testExistingFileDeclaringTheObjectFailsWithoutWritingAnything() {
        execute("CREATE TABLE USERS (ID INT NOT NULL)", "CREATE TABLE CITIES (ID INT NOT NULL)")
        val existing = writeSource(
            "Users.kt",
            """
            package com.example.tables

            import org.jetbrains.exposed.v1.core.Table

            object Users : Table("USERS") {
                val id = integer("ID")
            }
            """.trimIndent()
        )
        val original = existing.readText()

        val exception = assertFailsWith<IllegalStateException> { generate() }

        assertContains(exception.message.orEmpty(), "generateTables never overwrites existing files")
        assertContains(exception.message.orEmpty(), "File '${existing.path}' already declares 'Users'. Move or rename the file")
        assertEquals(original, existing.readText(), "The existing file must be left untouched")
        assertFalse(File(outputDirectory, path("Cities")).exists(), "Nothing should be written when planning fails")
    }

    @Test
    fun testExistingFileWithoutTheObjectFailsWithoutWritingAnything() {
        execute("CREATE TABLE USERS (ID INT NOT NULL)")
        // a commented-out declaration does not count as declaring the object
        val existing = writeSource(
            "Users.kt",
            """
            package com.example.tables

            // object Users : Table("USERS")
            /* object Users */
            data class User(val id: Int)
            """.trimIndent()
        )

        val exception = assertFailsWith<IllegalStateException> { generate() }

        assertContains(
            exception.message.orEmpty(),
            "File '${existing.path}' already exists but does not declare 'Users'. Move or rename the file"
        )
    }

    @Test
    fun testExistingSingleFileFails() {
        execute("CREATE TABLE USERS (ID INT NOT NULL)", "CREATE TABLE CITIES (ID INT NOT NULL)")
        val existing = writeSource("Schema.kt", "package com.example.tables\n\nobject Cities\n")

        val exception = assertFailsWith<IllegalStateException> { generate(fullFileName = "Schema.kt") }

        assertContains(exception.message.orEmpty(), "File '${existing.path}' already declares 'Cities'. Move or rename the file")
    }

    @Test
    fun testTableObjectDeclaredInAnotherFileIsReportedAsWarning() {
        execute("CREATE TABLE USERS (ID INT NOT NULL)", "CREATE TABLE CITIES (ID INT NOT NULL)")
        writeSource(
            "Legacy.kt",
            """
            package com.example.tables

            import org.jetbrains.exposed.v1.core.Table
            import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

            /* object Cities : Table("CITIES") */
            internal object Users : IntIdTable("users")

            object Archive :
                Table("ARCHIVE") {
                val id = integer("ID")
            }

            data class City(val id: Int)
            """.trimIndent()
        )

        val generated = generate()

        assertEquals(listOf(path("Cities"), path("Users")), generated)
        assertWarning("Object 'Users' extending Table is already declared in 'Legacy.kt'")
        assertFalse(messages.any { it.contains("'Cities'") }, "A commented-out declaration must be ignored: $messages")
        assertFalse(messages.any { it.contains("'Archive'") }, "Only names a generated object would redeclare are reported: $messages")

        // a suffix keeps the generated objects clear of the existing one, so no warning is logged
        messages.clear()
        val suffixed = generate(objectNameSuffix = "Table")
        assertEquals(listOf(path("CitiesTable"), path("UsersTable")), suffixed)
        assertFalse(messages.any { it.contains("already declared") }, "No warning expected with a suffix: $messages")
    }

    private fun writeSource(fileName: String, content: String): File = File(
        outputDirectory,
        "com${File.separator}example${File.separator}tables${File.separator}$fileName"
    ).apply {
        parentFile.mkdirs()
        writeText(content)
    }
}

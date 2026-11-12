package org.jetbrains.exposed.v1.plugin.core.migration

import org.jetbrains.exposed.v1.core.InternalApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for [EXPOSED-1137](https://youtrack.jetbrains.com/issue/EXPOSED-1137): a `fullFileName`
 * that resolves to a sibling directory sharing only a string prefix with `fileDirectory` (e.g.
 * `migrations` vs `migrations-backup`) must be rejected, and no file may be written outside
 * [MigrationConfig.fileDirectory].
 */
@OptIn(InternalApi::class)
class MigrationPathSecurityRegressionTest {
    @field:TempDir
    lateinit var root: File

    private fun generator(fullFileName: String): MigrationGenerator = MigrationGenerator(
        config = MigrationConfig(
            tablesPackage = "nonexistent.tables.package",
            tablesPackages = listOf("nonexistent.tables.package"),
            classpathUrls = emptyList(),
            fileDirectory = root.resolve("migrations"),
            fullFileName = fullFileName
        ),
        logger = MigrationLogger.stdout()
    )

    @Test
    fun `fullFileName resolving to a sibling directory with a shared path prefix is rejected`() {
        val sibling = root.resolve("migrations-backup")

        val error = runCatching { generator("../migrations-backup/outside.sql").generate() }.exceptionOrNull()

        assertNotNull(error)
        assertTrue { error is IOException }
        assertFalse { sibling.exists() }
    }

    @Test
    fun `fullFileName resolving to a nested sibling directory is rejected`() {
        val sibling = root.resolve("migrations-backup")

        val error = runCatching { generator("../migrations-backup/nested/outside.sql").generate() }.exceptionOrNull()

        assertNotNull(error)
        assertTrue { error is IOException }
        assertFalse { sibling.resolve("nested").exists() }
    }

    @Test
    fun `fullFileName resolving inside fileDirectory is still accepted`() {
        val error = runCatching { generator("V20261104000000__create_users.sql").generate() }.exceptionOrNull()

        // validation must not reject in-directory names; generate() may still fail afterwards for other
        // reasons (e.g. no database configured), so only an IOException from path validation is a failure
        if (error != null) {
            assertFalse { error is IOException }
        }
        assertFalse { root.resolve("migrations-backup").exists() }
    }
}

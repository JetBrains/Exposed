package org.jetbrains.exposed.v1.maven.plugin.integrations.helpers

import com.github.mustachejava.DefaultMustacheFactory
import org.jetbrains.exposed.v1.plugin.core.migration.VersionFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.PathWalkOption
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.fail

@DslMarker
annotation class TestMavenProjectDsl

@TestMavenProjectDsl
data class Versions(
    var kotlinVersion: String = "2.4.10",
    var exposedVersion: String = "1.4.0",
)

@TestMavenProjectDsl
data class SourceCodeOptions(
    var packageName: String = "com.example.tables",
)

@TestMavenProjectDsl
data class ExposedMigrationsConfig(
    var tablesPackage: String = "com.example.tables",
    var fileDirectory: String? = null,
    var filePrefix: String? = null,
    var fileVersionFormat: VersionFormat? = null,
    var fileSeparator: String? = null,
    var useUpperCaseDescription: String? = null,
    var fileExtension: String? = null,
    var fullFileName: String? = null,
    var databaseUrl: String? = null,
    var databaseUser: String? = null,
    var databasePassword: String? = null,
    var testContainersImageName: String? = null,
    var existingMigrations: ExistingMigrations = ExistingMigrations(),
) {

    fun withExistingMigrations(block: ExistingMigrations.() -> Unit) {
        existingMigrations.block()
    }
}

@TestMavenProjectDsl
data class ExistingMigrations(
    var dialect: String = "h2",
    var cities: Boolean = false,
    var users: Boolean = false,
) {
    fun allMigrations() {
        cities = true
        users = true
    }
}

data class Migration(val name: String, val content: String) {
    context(_: VerificationScope)
    fun contains(other: Migration) = assertContains(other.content, content)
}

sealed class MavenGoal(val goal: String) {
    data object GenerateMigrations : MavenGoal("generate-migrations")
}

sealed interface MavenProcessResult {
    data class Success(val output: String) : MavenProcessResult
    data class Failure(val output: String, val exitCode: Int) : MavenProcessResult

    data class Interrupted(val exception: InterruptedException) : MavenProcessResult

    fun assertSuccess() = when (this) {
        is Success -> this
        else -> fail("Expected success, but got $this")
    }

    fun assertFailure() = when (this) {
        is Failure -> this
        else -> fail("Expected failure, but got $this")
    }
}

class VerificationScope
class ConfigurationScope

@TestMavenProjectDsl
class TestMavenProject private constructor(
    private var tmpDirLocation: String,
    private var cleanup: Boolean = true,
    private val versions: Versions = Versions(),
    private val sourceCodeOptions: SourceCodeOptions = SourceCodeOptions(),
    private val exposedMigrations: ExposedMigrationsConfig = ExposedMigrationsConfig(),
) : AutoCloseable {

    /**
     * DSL for creating a test Maven project and running Maven goals on it.
     * Example Usage:
     * ```kotlin
     * @Test
     * fun `test generate basic migrations`() = TestMavenProject {
     *     configure { // configure values in pom
     *         migrationsConfig {
     *             databaseUrl = "jdbc:h2:mem:test"
     *             databaseUser = "sa"
     *             databasePassword = "sa"
     *         }
     *     }
     *
     *     verify {
     *         // execute the goal and verify the result
     *         val result = executeGoal(MavenGoal.GenerateMigrations)
     *         assertIs<MavenProcessResult.Success>(result)
     *         assertEquals(2, migrations.size)
     *     }
     * }
     * ```
     */
    companion object {
        operator fun invoke(tempDirLocation: String? = null, block: TestMavenProject.() -> Unit = {}) {
            TestMavenProject(tempDirLocation ?: randomProjectDirName()).apply {
                use {
                    block()
                }
            }
        }
    }

    lateinit var tmpDir: Path
        private set
    lateinit var sourceSetDir: Path
        private set
    lateinit var migrationsDir: Path
        private set
    lateinit var sourceCodePackage: Path
        private set

    context(_: VerificationScope)
    val migrations
        get() = migrationsDir.walk(PathWalkOption.BREADTH_FIRST)
            .map { Migration(it.name, it.readText()) }
            .toList()


    override fun close() {
        if (cleanup) {
            tmpDir.toFile().deleteRecursively()
        }
    }

    context(_: ConfigurationScope)
    fun migrationsConfig(block: ExposedMigrationsConfig.() -> Unit) {
        exposedMigrations.block()
    }

    context(_: ConfigurationScope)
    fun versions(block: Versions.() -> Unit) {
        versions.block()
    }

    fun configure(block: context(ConfigurationScope) TestMavenProject.() -> Unit) {
        context(ConfigurationScope()) {
            block()
        }
    }

    fun verify(block: context(VerificationScope) TestMavenProject.() -> Unit) {
        generate()
        context(VerificationScope()) {
            block()
        }
    }

    context(_: VerificationScope)
    fun executeGoal(goal: MavenGoal): MavenProcessResult {
        val mavenExecutable = resolveMavenExecutable()
            ?: error(
                """
                        Maven executable was not found.

                        Tried:
                        - MAVEN_EXECUTABLE
                        - mvn from PATH
                        - MAVEN_HOME/bin/mvn
                        - M2_HOME/bin/mvn
                        - mvn resolved from the login shell

                        Current PATH:
                        ${System.getenv("PATH").orEmpty()}

                        To fix this, either install Maven and make `mvn` available on PATH,
                        or set MAVEN_EXECUTABLE to the absolute path of the Maven executable                """.trimIndent(),
            )
        try {
            val process = ProcessBuilder()
                .directory(tmpDir.toAbsolutePath().toFile())
                .redirectErrorStream(true)
                .command(mavenExecutable.toAbsolutePath().toString(), "exposed:${goal.goal}")
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            return if (exitCode == 0) {
                MavenProcessResult.Success(output)
            } else {
                MavenProcessResult.Failure(output, exitCode)
            }
        } catch (e: InterruptedException) {
            return MavenProcessResult.Interrupted(e)
        }
    }

    private fun generate(): Path {
        tmpDir = Files.createTempDirectory(tmpDirLocation)
        val mavenProjectGenerator = MavenProjectGenerator(
            tmpDir = tmpDir,
            versions = versions,
            sourceCodeOptions = sourceCodeOptions,
            exposedMigrations = exposedMigrations,
        )
        mavenProjectGenerator.generate()

        sourceSetDir = mavenProjectGenerator.sourceSetDir
        migrationsDir = mavenProjectGenerator.migrationsDir
        sourceCodePackage = mavenProjectGenerator.sourceCodePackage

        return tmpDir
    }
}

private class MavenProjectGenerator(
    private val tmpDir: Path,
    private val versions: Versions,
    private val sourceCodeOptions: SourceCodeOptions,
    private val exposedMigrations: ExposedMigrationsConfig,
) {
    private val mustacheFactory = DefaultMustacheFactory("template")

    companion object {
        private const val PROJECT_DIR_PREFIX = $$"${project.basedir}/"
    }

    lateinit var sourceSetDir: Path
        private set
    lateinit var migrationsDir: Path
        private set
    lateinit var sourceCodePackage: Path
        private set

    fun generate() {
        writePom()
        sourceSetDir = mkSourceSet()
        migrationsDir = mkMigrationsDir()
        copyMigrationFiles()
        sourceCodePackage = mkPackages()
        writeSourceFiles()
    }

    private fun writePom() {
        val mustache = mustacheFactory.compile("pom.xml")
        Files.newBufferedWriter(tmpDir.resolve("pom.xml")).use { pomWriter ->
            val context = mapOf(
                "versions" to versions,
                "exposedMigrations" to exposedMigrations,
            )
            mustache.execute(pomWriter, context)
        }
    }

    private fun mkSourceSet(): Path {
        return Files.createDirectories(
            tmpDir
                .resolve("src")
                .resolve("main")
                .resolve("kotlin")
        )
    }

    private fun mkMigrationsDir(): Path {
        val fileDirectory = exposedMigrations.fileDirectory
        return if (fileDirectory == null) {
            Files.createDirectories(
                tmpDir
                    .resolve("src")
                    .resolve("main")
                    .resolve("resources")
                    .resolve("db")
                    .resolve("migration")
            )
        } else if (fileDirectory.startsWith(PROJECT_DIR_PREFIX)) {
            val migrationsDir = fileDirectory
                .removePrefix(PROJECT_DIR_PREFIX)
                .split('/')
                .fold(tmpDir) { path, s -> path.resolve(s) }
            Files.createDirectories(migrationsDir)
        } else {
            Files.createDirectories(Paths.get(fileDirectory))
        }
    }

    private fun copyMigrationFiles() {
        val migrationsFilesUrl = this::class.java.classLoader.getResource("template/migrations/${exposedMigrations.existingMigrations.dialect}")
        requireNotNull(migrationsFilesUrl) {
            "Expected migrations resources to be found in src/test/resources/template/migrations/h2"
        }
        File(migrationsFilesUrl.toURI()).walkTopDown()
            .filter { it.isFile }
            .filter {
                with(exposedMigrations.existingMigrations) {
                    cities && "cities" in it.name.lowercase() || users && "users" in it.name.lowercase()
                }
            }
            .toList()
            .forEach {
                Files.copy(it.toPath(), migrationsDir.resolve(it.name))
            }
    }

    private fun mkPackages(): Path {
        val sourceCodePackage = sourceCodeOptions.packageName
            .split('.')
            .fold(sourceSetDir) { path, s -> path.resolve(s) }
        return Files.createDirectories(sourceCodePackage)
    }

    private fun writeSourceFiles() {
        val sourceFilesUrl = this::class.java.classLoader.getResource("template/sourceFiles")

        requireNotNull(sourceFilesUrl) {
            "Expected sourceFiles resources to be found in src/test/resources/template/sourceFiles"
        }

        File(sourceFilesUrl.toURI()).walkTopDown().filter { it.isFile }.toList().forEach {
            val mustache = mustacheFactory.compile("sourceFiles/${it.name}")
            Files.newBufferedWriter(sourceCodePackage.resolve(it.name)).use { fileWriter ->
                mustache.execute(fileWriter, mapOf("packageName" to sourceCodeOptions.packageName))
            }
        }
    }
}

private fun resolveMavenExecutable(): Path? {
    System.getenv("MAVEN_EXECUTABLE")
        ?.takeIf { it.isNotBlank() }
        ?.let { Paths.get(it) }
        ?.takeIf { Files.isExecutable(it) }
        ?.let { return it }

    val executableName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "mvn.cmd"
    } else {
        "mvn"
    }

    System.getenv("PATH")
        .orEmpty()
        .split(File.pathSeparator)
        .asSequence()
        .filter { it.isNotBlank() }
        .map { Paths.get(it).resolve(executableName) }
        .firstOrNull { Files.isExecutable(it) }
        ?.let { return it }

    listOf("MAVEN_HOME", "M2_HOME")
        .asSequence()
        .mapNotNull { System.getenv(it) }
        .filter { it.isNotBlank() }
        .map { Paths.get(it).resolve("bin").resolve(executableName) }
        .firstOrNull { Files.isExecutable(it) }
        ?.let { return it }

    resolveMavenExecutableFromLoginShell()
        ?.let { return it }

    return null
}

private fun resolveMavenExecutableFromLoginShell(): Path? {
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        return null
    }

    val shells = sequenceOf(
        System.getenv("SHELL"),
        "/bin/zsh",
        "/bin/bash",
        "/usr/bin/bash",
    )
        .filterNotNull()
        .distinct()
        .filter { Files.isExecutable(Paths.get(it)) }

    return shells.firstNotNullOfOrNull { shell ->
        runCatching {
            val process = ProcessBuilder(shell, "-lc", "command -v mvn")
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly()
                return@runCatching null
            }

            process.inputStream
                .bufferedReader()
                .use { it.readText() }
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.let { Paths.get(it) }
                ?.takeIf { Files.isExecutable(it) }
        }.getOrNull()
    }
}

private fun randomProjectDirName() = "exposed-maven-plugin-test-${Random.nextInt(10000)}"

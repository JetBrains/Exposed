package org.jetbrains.exposed.v1.maven.plugin.integrations.helpers

import com.github.mustachejava.DefaultMustacheFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.PathWalkOption
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.assertContains
import kotlin.test.assertEquals

data class Versions(
    var kotlinVersion: String = "2.4.10",
    var exposedVersion: String = "1.3.1",
)

data class SourceCodeOptions(
    var packageName: String = "com.example.tables",
)

data class ExposedMigrationsConfig(
    var tablesPackage: String = "com.example.tables",
    var fileDirectory: String? = null,
    var filePrefix: String? = null,
    var fileVersionFormat: String? = null,
    var fileSeparator: String? = null,
    var useUpperCaseDescription: Boolean = true,
    var fileExtension: String? = null,
    var fullFileName: String? = null,
    var databaseUrl: String? = null,
    var databaseUser: String? = null,
    var databasePassword: String? = null,
    var testContainersImage: String? = null,
)

@JvmInline
value class Migration(val content: String) {
    companion object {
        fun fromResources(path: String): Migration {
            val content = this::class.java.getResource(path)?.readText()
            requireNotNull(content) { "Migration file not found in resources at $path" }
            return Migration(content)
        }
    }

    fun assertEquals(other: Migration) = assertEquals(other.content, content)
    fun contains(other: Migration) = assertContains(other.content, content)
}

sealed class MavenGoal(val goal: String) {
    data object GenerateMigrations : MavenGoal("generate-migrations")
}

sealed interface MavenProcessResult {
    data class Success(val output: String) : MavenProcessResult
    data class Failure(val output: String, val exitCode: Int) : MavenProcessResult
    data class Interrupted(val exception: InterruptedException) : MavenProcessResult
    data class MavenExecutableNotFound(val output: String) : MavenProcessResult
}

class TestMavenProject private constructor(
    private var tmpDirLocation: String,
    private var cleanup: Boolean = true,
    private val versions: Versions = Versions(),
    private val sourceCodeOptions: SourceCodeOptions = SourceCodeOptions(),
    private val exposedMigrations: ExposedMigrationsConfig = ExposedMigrationsConfig(),
) : AutoCloseable {
    lateinit var tmpDir: Path
        private set
    lateinit var sourceSetDir: Path
        private set
    lateinit var migrationsDir: Path
        private set
    lateinit var sourceCodePackage: Path
        private set

    val migrations
        get() = migrationsDir.walk(PathWalkOption.BREADTH_FIRST)
            .map { it.readText() }
            .map { Migration(it) }
            .toList()

    companion object {
        operator fun invoke(tempDirLocation: String, block: TestMavenProject.() -> Unit = {}) {
            TestMavenProject(tempDirLocation).apply {
                use {
                    block()
                }
            }
        }
    }

    fun generate(): Path {
        tmpDir = Files.createDirectories(Paths.get(tmpDirLocation))
        val mavenProjectGenerator = MavenProjectGenerator(
            configuration = this,
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

    override fun close() {
        if (cleanup) {
            tmpDir.toFile().deleteRecursively()
        }
    }

    fun migrationsConfig(block: ExposedMigrationsConfig.() -> Unit) {
        exposedMigrations.block()
    }

    fun sourceCode(block: SourceCodeOptions.() -> Unit) {
        sourceCodeOptions.block()
    }

    fun versions(block: Versions.() -> Unit) {
        versions.block()
    }

    fun executeGoal(goal: MavenGoal): MavenProcessResult {
        val mavenExecutable = resolveMavenExecutable()
            ?: return MavenProcessResult.MavenExecutableNotFound(
                output = """
                        Maven executable was not found.

                        The integration test needs Maven to run the generated test project.
                        Tried:
                        - MAVEN_EXECUTABLE
                        - mvn from PATH
                        - MAVEN_HOME/bin/mvn
                        - M2_HOME/bin/mvn

                        Current PATH:
                        ${System.getenv("PATH").orEmpty()}

                        To fix this, either install Maven and make `mvn` available on PATH,
                        or set MAVEN_EXECUTABLE to the absolute path of the Maven executable.
                """.trimIndent(),
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

        return null
    }
}

private class MavenProjectGenerator(
    private val configuration: TestMavenProject,
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

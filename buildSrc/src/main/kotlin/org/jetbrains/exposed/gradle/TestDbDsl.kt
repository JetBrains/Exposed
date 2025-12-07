package org.jetbrains.exposed.gradle

import com.avast.gradle.dockercompose.ComposeExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.time.Duration

const val HEALTH_TIMEOUT: Long = 60

class TestDb(val name: String) {
    internal val dialects = mutableListOf<String>()
    var port: Int? = null
    var container: String = name
    var withContainer: Boolean = true
    var colima: Boolean = false

    internal val dependencies = mutableListOf<String>()

    internal fun ignoresSpringTests(dialect: String): Boolean {
        return name != "h2_v2" || dialect != "H2_V2"
    }

    inner class DependencyBlock {
        fun dependency(dependencyNotation: String) {
            dependencies.add(dependencyNotation)
        }

        fun dependency(dependencyNotation: Provider<MinimalExternalModuleDependency>) {
            // translate dependency to string
            dependency(dependencyNotation.get().toString())
        }
    }

    fun dependencies(block: DependencyBlock.() -> Unit) {
        DependencyBlock().apply(block)
    }

    fun dialects(vararg dialects: String) {
        this.dialects.addAll(dialects)
    }
}

fun Project.testDb(name: String, block: TestDb.() -> Unit) {
    val db = TestDb(name).apply(block)
    if (db.withContainer) {
        configureCompose(db)
    }

    val dbTask = createDbTestTask(db)
    tasks.named<Test>("test") {
        delegatedTo(dbTask)
    }
}

private fun Project.createDbTestTask(db: TestDb): TaskProvider<Test> {
    return if (db.dialects.size == 1) {
        createDbTestTaskByDialect(db, "test_${db.name.lowercase()}", db.dialects.first())
    } else {
        val dialectTasks = db.dialects.map { dialect ->
            createDbTestTaskByDialect(db, formatDatabaseWithDialectTaskName(db.name.lowercase(), dialect.lowercase()), dialect)
        }

        tasks.register<Test>("test_all_${db.name.lowercase()}") {
            description = "Runs tests using ${db.name} database"
            group = "verification"

            delegatedTo(
                tasks = dialectTasks.toTypedArray()
            )
        }
    }
}

private fun formatDatabaseWithDialectTaskName(db: String, dialect: String): String {
    return listOfNotNull(
        "test",
        db,
        // It's the dialect name without prefixed db name
        dialect.replaceFirst(db, "").trim('_').takeIf { it.isNotEmpty() }
    )
        .filter { it.isNotEmpty() }
        .joinToString(separator = "_")
}

private fun Project.createDbTestTaskByDialect(db: TestDb, taskName: String, dialect: String): TaskProvider<Test> {
    return tasks.register<Test>(taskName) {
        description = "Runs tests using ${db.name} database with $dialect dialect"
        group = "verification"
        systemProperties["exposed.test.name"] = db.name
        systemProperties["exposed.test.container"] = if (db.withContainer) db.container else "none"
        systemProperties["exposed.test.dialects"] = dialect
        outputs.cacheIf { false }

        if (db.ignoresSpringTests(dialect)) {
            filter {
                // exclude all test classes in Spring modules:
                // spring-transaction, exposed-spring-boot-starter, spring-reactive-transaction, exposed-spring-boot-starter-r2dbc
                exclude(
                    "org/jetbrains/exposed/v1/spring/*",
                    "org/jetbrains/exposed/v1/jdbc-template/*",
                    "org/jetbrains/exposed/v1/database-client/*",
                )
                isFailOnNoMatchingTests = false
            }
        }

        val driverConfiguration = configurations.create("${db.name}DriverConfiguration_$dialect")
        dependencies {
            db.dependencies.forEach {
                driverConfiguration(it)
            }
        }

        classpath += files(driverConfiguration.resolve())

        if (db.withContainer) {
            dependsOn(rootProject.tasks.getByName("${db.container}ComposeUp"))
        }
    }
}

private fun Project.configureCompose(db: TestDb) {
    if (rootProject.tasks.findByPath("${db.container}ComposeUp") != null) return

    rootProject.extensions.configure<ComposeExtension>("dockerCompose") {
        nested(db.container).apply {
            environment.put("SERVICES_HOST", "127.0.0.1")
            environment.put("COMPOSE_CONVERT_WINDOWS_PATHS", true)

            val isArm = System.getProperty("os.arch") == "aarch64"
            if (isArm && db.colima) {
                val home = System.getProperty("user.home")
                val dockerHost = "unix://$home/.colima/default/docker.sock"
                environment.put("DOCKER_HOST", dockerHost)
            }

            useComposeFiles.set(listOf("buildScripts/docker/docker-compose-${db.container}.yml"))
            removeVolumes.set(true)
            stopContainers.set(false)

            waitForHealthyStateTimeout.set(Duration.ofMinutes(HEALTH_TIMEOUT))
        }
    }

    val startDb = rootProject.tasks.getByName("${db.container}ComposeUp")
    val stopDb = rootProject.tasks.getByName("${db.container}ComposeDownForced")

    val startCompose = rootProject.tasks.findByName("startCompose") ?: rootProject.tasks.register("startCompose").get()
    val stopCompose = rootProject.tasks.findByName("stopCompose") ?: rootProject.tasks.register("stopCompose").get()

    startCompose.dependsOn(startDb)
    stopCompose.dependsOn(stopDb)
}

/**
 * Delegates the execution of tests to other tasks.
 *
 * @param tasks The tasks to delegate the test execution to.
 * @return The modified Test object.
 */
fun Test.delegatedTo(vararg tasks: TaskProvider<out AbstractTestTask>): Test {
    // don't run tests directly, delegate to other tasks
    filter {
        setExcludePatterns("*")
        isFailOnNoMatchingTests = false
    }
    finalizedBy(tasks)
    // Pass --tests CLI option value into delegates
    doFirst {
        val testsFilter = (filter as DefaultTestFilter).commandLineIncludePatterns.toList()
        tasks.forEach {
            it.configure { setTestNameIncludePatterns(testsFilter) }
        }
    }
    return this
}

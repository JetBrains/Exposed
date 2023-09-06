package org.jetbrains.exposed.gradle

import com.avast.gradle.dockercompose.ComposeExtension
import org.gradle.api.Project
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.configurationcache.extensions.capitalized
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

    inner class DependencyBlock {
        fun dependency(dependencyNotation: String) {
            dependencies.add(dependencyNotation)
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

    val testTask = tasks.register<Test>("test${db.name.capitalized()}") {
        description = "Runs tests using ${db.name} database"
        group = "verification"
        systemProperties["exposed.test.name"] = db.name
        systemProperties["exposed.test.container"] = if (db.withContainer) db.container else "none"
        systemProperties["exposed.test.dialects"] = db.dialects.joinToString(",") { it.toUpperCase() }
        outputs.cacheIf { false }
        ignoreFailures = true

        if (!db.withContainer) return@register
        dependsOn(rootProject.tasks.getByName("${db.container}ComposeUp"))
        finalizedBy(rootProject.tasks.getByName("${db.container}ComposeDown"))
    }

    dependencies {
        db.dependencies.forEach {
            add("testRuntimeOnly", it)
        }
    }

    tasks.named<Test>("test") {
        delegatedTo(testTask)
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

    val startCompose = rootProject.tasks.findByName("startCompose") ?: rootProject.tasks.create("startCompose")
    val stopCompose = rootProject.tasks.findByName("stopCompose") ?: rootProject.tasks.create("stopCompose")

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

package org.jetbrains.exposed.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import java.io.File
import java.time.Duration

fun Project.setupDialectTest(dialect: String) {
    applyPluginSafely("com.avast.gradle.docker-compose")
    val dialectTest = tasks.create("exposedDialectTestWithDocker", Test::class) {
        group = "verification"
        systemProperties["exposed.test.dialects"] = dialect

        doFirst {
            _dockerCompose {
                val containerInfo = servicesInfos[dialect]!!
                systemProperty("exposed.test.$dialect.host", containerInfo.host)
                systemProperty("exposed.test.oracle.port", containerInfo.ports[1521] ?: -1)
                systemProperty("exposed.test.sqlserver.port", containerInfo.ports[1433] ?: -1)
                systemProperty("exposed.test.mariadb.port", containerInfo.ports[3306] ?: -1)
            }
        }
    }

    _dockerCompose.isRequiredBy(dialectTest)

    _dockerCompose {
        useComposeFiles = listOf(File(project.rootProject.projectDir, "buildScripts/docker/docker-compose-$dialect.yml").absolutePath)
        captureContainersOutput = true
        removeVolumes = true
        environment["COMPOSE_CONVERT_WINDOWS_PATHS"] = true
        waitForHealthyStateTimeout = Duration.ofMinutes(60)
    }

}

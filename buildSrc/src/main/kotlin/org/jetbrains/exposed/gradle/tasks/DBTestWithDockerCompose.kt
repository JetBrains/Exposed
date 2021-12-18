package org.jetbrains.exposed.gradle.tasks

import com.avast.gradle.dockercompose.ComposeSettings
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.process.JavaForkOptions
import org.gradle.process.ProcessForkOptions
import org.jetbrains.exposed.gradle._dockerCompose
import java.io.File
import java.time.Duration
import javax.inject.Inject

open class DBTestWithDockerCompose(dialect: String, @get:Input val port: Int, @get:Input val dockerComposeServiceName: String) : DBTest(dialect) {
    // Gradle doesn't support injection into constructors with optional parameters (as well as several constructors marked with @Inject)
    // Also IDE's inline hints are abused if parameters are passed as vararg
    // Workaround is to wrap all parameters into a data class and pass it into constructor
    data class Parameters(val dialect: String, val port: Int, val dockerComposeServiceName: String = dialect.toLowerCase())

    @Inject
    constructor(parameters: Parameters) : this(parameters.dialect, parameters.port, parameters.dockerComposeServiceName)

    private val dockerCompose: ComposeSettings = project._dockerCompose.nested(dockerComposeServiceName)

    init {
        dockerCompose.apply {
            isRequiredWithTaskConfigurationAvoidanceBy(this@DBTestWithDockerCompose)
            val env = environment.get().toMutableMap().apply {
                set("COMPOSE_CONVERT_WINDOWS_PATHS", true)
            }
            environment.set(env)
            useComposeFiles.add(
                File(project.rootProject.projectDir, "buildScripts/docker/docker-compose-$dockerComposeServiceName.yml").absolutePath
            )
            captureContainersOutput.set(true)
            removeVolumes.set(true)
            waitForHealthyStateTimeout.set(Duration.ofMinutes(60))
        }
    }

    override fun executeTests() {
        val containerInfo = dockerCompose.servicesInfos[dockerComposeServiceName]!!
        withSystemProperties(
            "exposed.test.$dockerComposeServiceName.host" to containerInfo.host,
            "exposed.test.$dockerComposeServiceName.port" to (containerInfo.ports[port] ?: -1)
        ) {
            super.executeTests()
        }
    }
}

fun ComposeSettings.isRequiredWithTaskConfigurationAvoidanceBy(task: Task) {
    task.dependsOn(upTask)
    task.finalizedBy(downTask)
    upTask.configure {
        shouldRunAfter(project.tasks.named("testClasses"))
    }

    if (task is ProcessForkOptions) task.doFirst { exposeAsEnvironment(task) }
    if (task is JavaForkOptions) task.doFirst { exposeAsSystemProperties(task) }
}

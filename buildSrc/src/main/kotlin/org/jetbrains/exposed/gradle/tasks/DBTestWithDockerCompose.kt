package org.jetbrains.exposed.gradle.tasks

import com.avast.gradle.dockercompose.ComposeSettings
import org.gradle.api.tasks.Input
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

    private val dockerCompose: ComposeSettings = project._dockerCompose.nested(dockerComposeServiceName).apply {
        environment.put("COMPOSE_CONVERT_WINDOWS_PATHS", true)
        useComposeFiles.add(
            File(project.rootProject.projectDir, "buildScripts/docker/docker-compose-$dockerComposeServiceName.yml").absolutePath
        )
        captureContainersOutput.set(true)
        removeVolumes.set(true)
        waitForHealthyStateTimeout.set(Duration.ofMinutes(60))
    }

    override fun executeTests() {
        with(dockerCompose) {
            try {
                upTask.get().up()
                exposeAsEnvironment(this@DBTestWithDockerCompose)
                exposeAsSystemProperties(this@DBTestWithDockerCompose)
                val containerInfo = servicesInfos[dockerComposeServiceName]!!
                withSystemProperties(
                    "exposed.test.$dockerComposeServiceName.host" to containerInfo.host,
                    "exposed.test.$dockerComposeServiceName.port" to (containerInfo.ports[port] ?: -1)
                ) {
                    super.executeTests()
                }
            } finally {
                downTask.get().down()
            }
        }
    }
}

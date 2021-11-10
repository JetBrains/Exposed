package org.jetbrains.exposed.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import java.io.File
import java.time.Duration

fun Project.setupDialectTest(dialect: String) {
    if (dialect != "none") {
        applyPluginSafely("com.avast.gradle.docker-compose")
        val dialectTest = tasks.create("exposedDialectTestWithDocker", Test::class) {
            group = "verification"
            systemProperties["exposed.test.dialects"] = when (dialect) {
                "mysql8" -> "mysql"
                else -> dialect
            }

            doFirst {
                _dockerCompose {
                    val containerInfo = servicesInfos[dialect]!!
                    systemProperty("exposed.test.$dialect.host", containerInfo.host)
                    systemProperty("exposed.test.oracle.port", containerInfo.ports[1521] ?: -1)
                    systemProperty("exposed.test.sqlserver.port", containerInfo.ports[1433] ?: -1)
                    if (dialect in listOf("mysql", "mysql8", "mariadb"))
                        systemProperty("exposed.test.$dialect.port", containerInfo.ports[3306] ?: -1)
                }
            }
        }

        _dockerCompose.isRequiredBy(dialectTest)

        _dockerCompose {
            val env = environment.get().toMutableMap().apply {
                set("COMPOSE_CONVERT_WINDOWS_PATHS", true)
            }
            useComposeFiles.add(File(project.rootProject.projectDir, "buildScripts/docker/docker-compose-$dialect.yml").absolutePath)
            captureContainersOutput.set(true)
            removeVolumes.set(true)
            environment.set(env)
            waitForHealthyStateTimeout.set(Duration.ofMinutes(60))
            captureContainersOutput.set(true)
        }
    }
}

fun setupTestDriverDependencies(dialect: String, testImplementationSetup: (group: String, artifactId: String, version: String) -> Unit) {
    testImplementationSetup("org.xerial", "sqlite-jdbc", Versions.sqlLite3)
    testImplementationSetup("com.h2database", "h2", Versions.h2)
    when (dialect) {
        "mariadb" -> testImplementationSetup("org.mariadb.jdbc", "mariadb-java-client", Versions.mariaDB)
        "mysql" -> testImplementationSetup("mysql", "mysql-connector-java", Versions.mysql51)
        "mysql8" -> testImplementationSetup("mysql", "mysql-connector-java", Versions.mysql80)
        "oracle" -> testImplementationSetup("com.oracle.database.jdbc", "ojdbc8", Versions.oracle12)
        "sqlserver" -> testImplementationSetup("com.microsoft.sqlserver", "mssql-jdbc", Versions.sqlserver)
        else -> {
            testImplementationSetup("com.h2database", "h2", Versions.h2)
            testImplementationSetup("mysql", "mysql-connector-java", Versions.mysql51)
            testImplementationSetup("org.postgresql", "postgresql", Versions.postgre)
            testImplementationSetup("com.impossibl.pgjdbc-ng", "pgjdbc-ng", Versions.postgreNG)
        }
    }
}

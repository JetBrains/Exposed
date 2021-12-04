import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.exposed.gradle.setupTestDriverDependencies
import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

val dialect: String by project

dependencies {
    api(project(":exposed-core"))
    api("joda-time", "joda-time", "2.10.13")
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))

    testRuntimeOnly("org.testcontainers", "testcontainers", Versions.testContainers)
    testImplementation("com.opentable.components", "otj-pg-embedded", Versions.otjPgEmbedded)

    setupTestDriverDependencies(dialect) { group, artifactId, version ->
        testImplementation(group, artifactId, version)
    }
}

setupDialectTest(dialect)

import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.exposed.gradle.setupTestDriverDependencies

plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

val dialect: String by project

dependencies {
    api(project(":exposed-core"))
    api("joda-time", "joda-time", "2.10.5")
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))

    testImplementation("com.opentable.components", "otj-pg-embedded", "0.12.0")
    testRuntimeOnly("org.testcontainers", "testcontainers", "1.15.3")

    setupTestDriverDependencies(dialect) { group, artifactId, version ->
        testImplementation(group, artifactId, version)
    }
}

setupDialectTest(dialect)

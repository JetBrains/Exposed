import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.setupDialectTest

plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
}
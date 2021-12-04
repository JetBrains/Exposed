import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.exposed.gradle.setupTestDriverDependencies
import org.jetbrains.exposed.gradle.Versions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile

plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

val dialect: String by project

dependencies {
    api(project(":exposed-core"))
    api("org.jetbrains.kotlinx", "kotlinx-datetime-jvm", "0.3.1")
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

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        apiVersion = "1.5"
        languageVersion = "1.5"
    }
}

tasks.withType(Test::class.java) {
    jvmArgs = listOf("-XX:MaxPermSize=256m")
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

setupDialectTest(dialect)

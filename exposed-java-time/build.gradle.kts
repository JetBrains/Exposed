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
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation("junit", "junit", "4.12")
    testImplementation(kotlin("test-junit"))

    testRuntimeOnly("org.testcontainers", "testcontainers", Versions.testContainers)

    setupTestDriverDependencies(dialect) { group, artifactId, version ->
        testImplementation(group, artifactId, version)
    }
}

tasks.withType<KotlinJvmCompile> {
    kotlinOptions {
        jvmTarget = "16"
        apiVersion = "1.5"
        languageVersion = "1.5"
    }
}

//tasks.withType<Detekt>().configureEach {
//    // Target version of the generated JVM bytecode. It is used for type resolution.
//    jvmTarget = "1.8"
//}

tasks.withType(Test::class.java) {
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

setupDialectTest(dialect)

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") apply true
    alias(libs.plugins.serialization)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    api(project(":exposed-core"))
    api(libs.kotlinx.jvm.datetime)
    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation(project(":exposed-json"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.VERSION_1_8 > JavaVersion.current()) {
        jvmArgs = listOf("-XX:MaxPermSize=256m")
    }
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

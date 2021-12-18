import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    api("org.springframework", "spring-jdbc", Versions.springFramework)
    api("org.springframework", "spring-context", Versions.springFramework)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotlinCoroutines)

    testImplementation(project(":exposed-dao"))
    testImplementation(project(":exposed-tests"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx","kotlinx-coroutines-debug", Versions.kotlinCoroutines)
    testImplementation("org.springframework", "spring-test", Versions.springFramework)
    testImplementation("org.slf4j", "slf4j-api", Versions.slf4j)
    testImplementation("org.apache.logging.log4j", "log4j-slf4j-impl", Versions.log4j2)
    testImplementation("org.apache.logging.log4j", "log4j-api", Versions.log4j2)
    testImplementation("org.apache.logging.log4j", "log4j-core", Versions.log4j2)
    testImplementation("junit", "junit", "4.12")
    testImplementation("org.hamcrest", "hamcrest-library", "1.3")
    testImplementation("com.h2database", "h2", Versions.h2)
}

tasks.withType<Test>().configureEach {
    jvmArgs = listOf("-XX:MaxPermSize=256m")
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

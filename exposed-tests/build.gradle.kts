import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.Versions

plugins {
    kotlin("jvm") apply true
    id("testWithDBs")
}

repositories {
    mavenCentral()
    maven("https://repository.novatec-gmbh.de/content/repositories/novatec/")
}

dependencies {
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotlinCoroutines)
    implementation(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-dao"))
    implementation(project(":exposed-crypt"))
    implementation(kotlin("test-junit"))
    implementation("org.slf4j", "slf4j-api", Versions.slf4j)
    implementation("org.apache.logging.log4j", "log4j-slf4j-impl", Versions.log4j2)
    implementation("org.apache.logging.log4j", "log4j-api", Versions.log4j2)
    implementation("org.apache.logging.log4j", "log4j-core", Versions.log4j2)
    implementation("junit", "junit", "4.12")
    implementation("org.hamcrest", "hamcrest-library", "1.3")
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-debug", Versions.kotlinCoroutines)

    implementation("org.testcontainers", "mysql", Versions.testContainers)
    implementation("com.opentable.components", "otj-pg-embedded", Versions.otjPgEmbedded)
    testCompileOnly("org.postgresql", "postgresql", Versions.postgre)
    testCompileOnly("com.impossibl.pgjdbc-ng", "pgjdbc-ng", Versions.postgreNG)
    compileOnly("com.h2database", "h2", Versions.h2)
    testCompileOnly("org.xerial", "sqlite-jdbc", Versions.sqlLite3)
}

tasks.withType<Test>().configureEach {
    jvmArgs = listOf("-XX:MaxPermSize=256m")
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}
